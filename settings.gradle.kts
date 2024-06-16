rootProject.name = "tasks"

include("core")

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
            version("kotlin", "2.0.0")

            // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
            plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").versionRef("kotlin")
            plugin("kotlin-multiplatform", "org.jetbrains.kotlin.multiplatform").versionRef("kotlin")

            // https://github.com/Kotlin/kotlinx-kover
            plugin("kotlinx-kover", "org.jetbrains.kotlinx.kover").version("0.8.1")

            // https://github.com/ben-manes/gradle-versions-plugin
            plugin("versions", "com.github.ben-manes.versions").version("0.51.0")

            // https://kotlinlang.org/api/latest/kotlin.test/
            library("kotlin-test", "org.jetbrains.kotlin", "kotlin-test")
                .versionRef("kotlin")
            library("kotlin-test-common", "org.jetbrains.kotlin", "kotlin-test-common")
                .versionRef("kotlin")
            library("kotlin-test-annotations-common", "org.jetbrains.kotlin", "kotlin-test-annotations-common")
                .versionRef("kotlin")
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api")
                .version("5.10.2")
        }
    }
}
