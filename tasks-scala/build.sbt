import java.util.Properties

val scala3Version = "3.3.1"

//val props = settingsKey[Properties]("Main project properties")
//props := {
//  val projectProperties = Properties()
//  val rootDir = (baseDirectory in ThisBuild).value
//  val fis = FileInputStream(s"$rootDir/../gradle.properties")
//  projectProperties.load(fis)
//  projectProperties
//}

lazy val root = project
  .in(file("."))
  .settings(
    name := "Tasks Scala",
//    version := props.value.getProperty("project.version"),

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )
