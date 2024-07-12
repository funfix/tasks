rootProject.name = "tasks"

include("tasks-core-jvm")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// https://docs.gradle.org/current/userguide/platforms.html
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // https://github.com/ben-manes/gradle-versions-plugin
            plugin("versions", "com.github.ben-manes.versions")
                .version("0.51.0")

            // https://vanniktech.github.io/gradle-maven-publish-plugin/
            plugin("publish", "com.vanniktech.maven.publish")
                .version("0.29.0")

            // https://jspecify.dev/docs/start-here
            library("jspecify", "org.jspecify", "jspecify")
                .version("0.3.0")

            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api")
                .version("5.10.2")
        }
    }
}
