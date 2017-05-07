/*
 * Copyright 2017 Nicolas Rinaudo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kantan.sbt

import com.github.tkawachi.doctest.DoctestPlugin.autoImport._
import com.github.tkawachi.doctest.DoctestPlugin.DoctestTestFramework
import com.typesafe.sbt.SbtGit.git
import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{createHeaders, headers}
import de.heikoseeberger.sbtheader.license.Apache2_0
import org.scalastyle.sbt.ScalastylePlugin
import sbt._
import sbt.Keys._
import wartremover.{Wart, WartRemover, Warts}

/** Settings common to all projects.
  *
  * Among other things, this plugin will set sane scalac options, import whatever is required for the current version
  * of scala to support macros, ...
  *
  * It's usually necessary to enable one of the following plugins:
  *  - [[PublishedPlugin]]    : configures projects whose artifacts are intended for publication on maven central.
  *  - [[UnpublishedPlugin]]  : configures projects whose artifacts are not meant to be published, such as tests and
  *                             documentation.
  *  - [[DocumentationPlugin]]: configures projects whose output is a documentation website.
  */
object KantanPlugin extends AutoPlugin {
  // - Common dependency versions --------------------------------------------------------------------------------------
  // -------------------------------------------------------------------------------------------------------------------
  val kindProjectorVersion = "0.9.3"
  val macroParadiseVersion = "2.1.0"



  // - Public settings -------------------------------------------------------------------------------------------------
  // -------------------------------------------------------------------------------------------------------------------
  object autoImport {
    val kantanProject: SettingKey[String] = settingKey("Name of the kantan project")

    /** `true` if java 8 is supported, `false` otherwise. */
    lazy val java8Supported: Boolean = BuildProperties.java8Supported

    implicit class KantanOperations(val proj: Project) extends AnyVal {
      def laws(name: String): Project =
        proj.settings(unmanagedClasspath in Test ++= (fullClasspath in (LocalProject(name), Compile)).value)

      def aggregateIf(predicate: Boolean)(refs: ProjectReference*): Project =
        if(predicate) proj.aggregate(refs:_*)
        else          proj

      def dependsOnIf(predicate: Boolean)(refs: ClasspathDep[ProjectReference]*): Project =
        if(predicate) proj.dependsOn(refs:_*)
        else          proj
    }
  }
  import autoImport._



  // - AutoPlugin implementation ---------------------------------------------------------------------------------------
  // -------------------------------------------------------------------------------------------------------------------
  override def trigger = allRequirements

  override def requires = HeaderPlugin && WartRemover

  override lazy val projectSettings =
    generalSettings ++ scalacSettings ++ commonDependencies ++ remoteSettings ++
    ScalastylePlugin.projectSettings ++ wartRemoverSettings

  override def globalSettings =
    addCommandAlias("validate", ";clean;scalastyle;test:scalastyle;coverage;test;coverageReport;coverageAggregate;doc")



  // - Custom settings -------------------------------------------------------------------------------------------------
  // -------------------------------------------------------------------------------------------------------------------
  /** Remote identifiers, computed from [[autoImport.kantanProject]]. */
  lazy val remoteSettings: Seq[Setting[_]] = Seq(
    homepage       := Some(url(s"https://nrinaudo.github.io/kantan.${kantanProject.value}")),
    apiURL         := Some(url(s"https://nrinaudo.github.io/kantan.${kantanProject.value}/api/")),
    git.remoteRepo := s"git@github.com:nrinaudo/kantan.${kantanProject.value}.git",
    scmInfo        := Some(ScmInfo(
      url(s"https://github.com/nrinaudo/kantan.${kantanProject.value}"),
      s"scm:git:git@github.com:nrinaudo/kantan.${kantanProject.value}.git"
    ))
  )

  private def addBoilerplate(confs: Configuration*): List[Setting[_]] =
    confs.foldLeft(List.empty[Setting[_]]) { (acc, conf) ⇒
      acc ++ (unmanagedSources in (conf, createHeaders) ++= (((sourceDirectory in conf).value / "boilerplate") **
                                                             "*.template").get)
    }


  /** General settings. */
  lazy val generalSettings: Seq[Setting[_]] = {
    val license = Apache2_0("2017", "Nicolas Rinaudo")

    Seq(
      organization            := "com.nrinaudo",
      scalaVersion            := "2.12.2",
      crossScalaVersions      := Seq("2.10.6", "2.11.11", "2.12.2"),
      autoAPIMappings         := true,
      incOptions              := incOptions.value.withNameHashing(true),
      doctestWithDependencies := false,
      doctestMarkdownEnabled  := true,
      doctestTestFramework    := DoctestTestFramework.ScalaTest,
      headers                 := Map(
        "scala"    → license,
        "java"     → license,
        "template" → license
      ),
      resolvers              ++= Seq(
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("snapshots")
      )
    ) ++ addBoilerplate(Compile, Test)
  }

  /** Sane, version dependent scalac settings. */
  def scalacSettings: Seq[Setting[_]] = {
    def base(version: String) = Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:experimental.macros",
      "-deprecation",
      "-unchecked",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xfuture"
    ) ++ (CrossVersion.partialVersion(version) match {
      case Some((_, x)) if x >= 12 ⇒ Seq("-Ypartial-unification")
      case Some((_, 10)) ⇒ Seq("-Xdivergence211")
      case _             ⇒ Seq.empty
    })

    // Warnings are only fatal when compiling the "main" code. I'd gladly make them fatal everywhere, but -Xlint
    // can be way too agressive (unused-imports basically make the REPL unusable), and tests sometimes need to do
    // dodgy things (such as declare two implicits and make sure the right one is picked up - making the other one
    // unused).
    Seq(
      scalacOptions                       := base(scalaVersion.value),
      scalacOptions in (Compile, compile) ++= Seq("-Xfatal-warnings", "-Xlint"),
      scalacOptions in Test               ++= Seq("-Xlint")
    )
  }

  /** WartRemover settings, disabled for 2.10 because of weird compatibility issues that I can't bother to get into.
    * 2.10 support is going to be dropped sooner rather than later anyway.
    */
  def wartRemoverSettings: Seq[Setting[_]] = {
    List(Compile, Test).flatMap { c ⇒ inConfig(c)(WartRemover.autoImport.wartremoverErrors in (Compile, compile) ++= {
      if(scalaVersion.value.startsWith("2.10")) Seq.empty
      // Removes Warts that have too many false positives (and are mostly covered by other tools as well anyway).
      else Warts.allBut(Wart.NonUnitStatements,
        Wart.Equals, Wart.Overloading, Wart.ImplicitParameter, Wart.Nothing, Wart.ImplicitConversion, Wart.Any,
        Wart.ToString, Wart.PublicInference)
    })}
  }

  /** Includes common dependencies (macros and kind-projector). */
  lazy val commonDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= Seq(
      compilerPlugin("org.spire-math" % "kind-projector" % kindProjectorVersion cross CrossVersion.binary),
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
    ) ++ {
      if(scalaVersion.value.startsWith("2.10"))
        List(compilerPlugin("org.scalamacros" % "paradise" % macroParadiseVersion cross CrossVersion.full))
      else Nil
    }
  )
}
