import de.heikoseeberger.sbtheader.AutomateHeaderPlugin

lazy val baseSettings: Seq[sbt.Def.Setting[_]] = {
  Seq(
    organization         := "com.nrinaudo",
    organizationHomepage := Some(url("https://nrinaudo.github.io")),
    organizationName     := "Nicolas Rinaudo",
    startYear            := Some(2016),
    licenses             := Seq("Apache-2.0" → url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage             := Some(url(s"https://nrinaudo.github.io/kantan.sbt")),
    developers           := List(Developer("nrinaudo", "Nicolas Rinaudo", "nicolas@nrinaudo.com",
      url("https://twitter.com/nicolasrinaudo"))),
    scmInfo              := Some(ScmInfo(
      url(s"https://github.com/nrinaudo/kantan.sbt"),
      s"scm:git:git@github.com:nrinaudo/kantan.sbt.git"
    )),
    scalafmtVersion := Versions.scalafmt
  )
}

lazy val pluginSettings = scriptedSettings ++ Seq(
  scriptedLaunchOpts ++= Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value),
  sbtPlugin           := true,
  scalacOptions       ++= Seq("-feature", "-language:existentials")
)

lazy val root = Project(id = "kantan-sbt", base = file("."))
  .settings(moduleName := "root")
  .settings(baseSettings)
  .settings(
    publish         := (),
    publishLocal    := (),
    publishArtifact := false
  )
  .aggregate(core, strict, kantan, boilerplate, scalastyle, scalafmt)

lazy val core = project
  .settings(
    moduleName := "kantan.sbt",
    name       := "core"
  )
  .settings(baseSettings)
  .settings(pluginSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    addSbtPlugin("de.heikoseeberger"   %  "sbt-header"            % Versions.sbtHeader),
    addSbtPlugin("org.tpolecat"        %  "tut-plugin"            % Versions.tut),
    addSbtPlugin("com.typesafe.sbt"    %  "sbt-site"              % Versions.sbtSite),
    addSbtPlugin("com.eed3si9n"        %  "sbt-unidoc"            % Versions.sbtUnidoc),
    addSbtPlugin("com.typesafe.sbt"    %  "sbt-ghpages"           % Versions.sbtGhPages),
    addSbtPlugin("org.scoverage"       %% "sbt-scoverage"         % Versions.scoverage),
    addSbtPlugin("com.github.tkawachi" %  "sbt-doctest"           % Versions.sbtDoctest)
  )

lazy val strict = project
  .settings(
    moduleName := "kantan.sbt-strict",
    name       := "strict"
  )
  .settings(baseSettings)
  .settings(pluginSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(addSbtPlugin("org.wartremover" % "sbt-wartremover" % Versions.wartRemover))
  .dependsOn(core)

lazy val scalastyle = project
  .settings(
    moduleName := "kantan.sbt-scalastyle",
    name       := "scalastyle"
  )
  .settings(baseSettings)
  .settings(pluginSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % Versions.scalastyle))
  .dependsOn(core)

lazy val scalafmt = project
  .settings(
    moduleName := "kantan.sbt-scalafmt",
    name       := "scalafmt"
  )
  .settings(baseSettings)
  .settings(pluginSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(addSbtPlugin("com.lucidchart" %  "sbt-scalafmt" % Versions.sbtScalafmt))
  .dependsOn(core)

lazy val boilerplate = project
  .settings(
    moduleName := "kantan.sbt-boilerplate",
    name       := "boilerplate"
  )
  .settings(baseSettings)
  .settings(pluginSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(addSbtPlugin("io.spray" % "sbt-boilerplate" % Versions.boilerplate))
  .dependsOn(core)

lazy val kantan = project
  .settings(
    moduleName := "kantan.sbt-kantan",
    name       := "kantan"
  )
  .settings(baseSettings)
  .settings(pluginSettings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype"  % Versions.sbtSonatype),
    addSbtPlugin("com.jsuereth"   % "sbt-pgp"       % Versions.sbtPgp)
  )
  .dependsOn(strict, scalastyle, scalafmt)


addCommandAlias("validate", ";clean;scalastyle;test:scalastyle;compile;scripted")
