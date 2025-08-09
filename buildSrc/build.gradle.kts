import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val props = run {
    val projectProperties = Properties()
    val fis = FileInputStream("$rootDir/../gradle.properties")
    projectProperties.load(fis)
    projectProperties
}

fun version(k: String) =
    props.getProperty("versions.$k")

dependencies {
    implementation(libs.gradle.versions.plugin)
    implementation(libs.vanniktech.publish.plugin)
    implementation(libs.errorprone.gradle.plugin)
}
