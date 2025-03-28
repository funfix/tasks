@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("tasks.kmp-project")
}

mavenPublishing {
    pom {
        name = "Tasks / Kotlin"
        description = "Integration with Kotlin Multiplatform"
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            compilerOptions {
                explicitApi = ExplicitApiMode.Strict
                allWarningsAsErrors = true
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            compilerOptions {
                explicitApi = ExplicitApiMode.Strict
                allWarningsAsErrors = true
            }

            dependencies {
                implementation(project(":tasks-jvm"))
                compileOnly(libs.jetbrains.annotations)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jsMain by getting {
            compilerOptions {
                explicitApi = ExplicitApiMode.Strict
                allWarningsAsErrors = true
            }
        }
    }
}
