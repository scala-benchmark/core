import Dependencies._
import sbt.ModuleID

name := "Smart Backpacker Backend"

lazy val testSettings: Seq[SettingsDefinition] = Seq(
  Test / testOptions += Tests.Setup( () => println(">>> Setup") ),
  Test / testOptions += Tests.Cleanup( () => println(">>> Cleanup") )
)

lazy val commonSettings: Seq[SettingsDefinition] = Seq(
  inThisBuild(List(
    organization := "com.github.gvolpe",
    scalaVersion := "2.12.18",
    version      := "1.2.6",
    scalacOptions := Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-Ypartial-unification"
    ),
    libraryDependencySchemes ++= Seq(
      "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
      "org.typelevel" %% "cats-core" % VersionScheme.Always,
      "org.typelevel" %% "cats-effect" % VersionScheme.Always,
      "org.typelevel" %% "cats-kernel" % VersionScheme.Always,
      "co.fs2" %% "fs2-core" % VersionScheme.Always,
      "co.fs2" %% "fs2-io" % VersionScheme.Always
    )
  )),
  Test / logBuffered := false,
  Test / parallelExecution := true,
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  coverageExcludedPackages := "com\\.github\\.gvolpe\\.smartbackpacker\\\\.common.*;.*Server*;.*Bindings*;.*AirlinesJob*;.*AirlinesApp*;.*ScraperJob*;.*ApiTokenGenerator*;.*TokenGeneration*;.*VisaRequirementsInsertData*;.*JwtTokenAuthMiddleware*;.*Module*;.*ScraperModule*;.*AirlinesModule*;.*IOApp*;",
  libraryDependencies ++= Seq(
    Libraries.catsEffect,
    Libraries.monix,
    Libraries.fs2Core,
    Libraries.http4sServer,
    Libraries.http4sClient,
    Libraries.http4sDsl,
    Libraries.http4sCirce,
    Libraries.tsecJwtMac,
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.h2,
    Libraries.flyway,
    Libraries.doobieCore,
    Libraries.doobieH2,
    Libraries.doobiePostgres,
    Libraries.doobieTest,
    Libraries.scalaScraper,
    Libraries.typesafeConfig,
    Libraries.logback,
    Libraries.scalaTest,
    Libraries.scalaCheck,
    Libraries.metricsCore,
    Libraries.metricsGraphite,
    Libraries.liftWebkit,
    Libraries.scalaXml,
    Libraries.playCrypto
  ),
  organizationName := "Smart Backpacker App",
  startYear := Some(2017),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  pomExtra :=
    <scm>
      <url>git@github.com:gvolpe/smart-backpacker-api.git</url>
      <connection>scm:git:git@github.com:gvolpe/smart-backpacker-api.git</connection>
    </scm>
      <developers>
        <developer>
          <id>gvolpe</id>
          <name>Gabriel Volpe</name>
          <url>http://github.com/gvolpe</url>
        </developer>
      </developers>
)

val AirlineDependencies: Seq[ModuleID] = Seq(Libraries.fs2IO)

lazy val root = project.in(file("."))
  .aggregate(api, airlines, common, scraper)

lazy val common = project.in(file("common"))
  .settings(commonSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)

lazy val api = project.in(file("api"))
  .settings(commonSettings: _*)
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)

lazy val airlines = project.in(file("airlines"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= AirlineDependencies)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(api)

lazy val scraper = project.in(file("scraper"))
  .settings(commonSettings: _*)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(api)
