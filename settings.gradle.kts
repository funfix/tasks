rootProject.name = "tasks"

include("tasks-jvm")
include("tasks-kotlin")
include("tasks-kotlin-coroutines")
include("tasks-scala")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
