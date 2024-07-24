import Boilerplate.crossVersionSharedSources

import java.io.FileInputStream
import java.util.Properties

ThisBuild / scalaVersion := "3.3.1"
ThisBuild / crossScalaVersions := Seq("2.13.14", scalaVersion.value)

ThisBuild / resolvers ++= Seq(Resolver.mavenLocal)

val props = settingKey[Properties]("Main project properties")
ThisBuild / props := {
  val projectProperties = new Properties()
  val rootDir = (ThisBuild / baseDirectory).value
  val fis = new FileInputStream(s"$rootDir/../gradle.properties")
  projectProperties.load(fis)
  projectProperties
}

ThisBuild / version := {
  val base = props.value.getProperty("project.version")
  val isRelease =
    sys.env.get("BUILD_RELEASE").filter(_.nonEmpty)
      .orElse(Option(System.getProperty("buildRelease")))
      .exists(it => it == "true" || it == "1" || it == "yes" || it == "on")
  if (isRelease) base else s"$base-SNAPSHOT"
}

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = project
  .in(file("."))
  .settings(
    publish := {},
    publishLocal := {},
  )
  .aggregate(coreJVM, coreJS)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(crossVersionSharedSources)
  .settings(
    name := "tasks-scala",
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.funfix" % "tasks-jvm" % version.value
    )
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

