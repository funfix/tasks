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
    props.getProperty("versions.$k")?.toString()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${version("kotlin")}")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:${version("kotlinx.kover")}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${version("dokka")}")
    implementation("com.github.ben-manes:gradle-versions-plugin:${version("gradle.versions")}")
}
