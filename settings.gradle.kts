rootProject.name = "tasks"

include(
    "tasks-core-jvm",
    "tasks",
    "tasks-kotlin",
)

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
