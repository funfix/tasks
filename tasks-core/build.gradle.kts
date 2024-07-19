import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("tasks.kmp-project")
}

publishing {
    publications {
        named<MavenPublication>("kotlinMultiplatform") {
            pom {
                name = "Tasks Core"
                description = "Cross-language utilities for working with concurrent tasks"
            }
        }
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    sourceSets {
        val commonMain by getting {
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                compileOnly(libs.jetbrains.annotations)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.jetbrains.annotations)
                implementation(libs.junit.jupiter.api)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "org.jetbrains.kotlinx.multiplatform.library.template"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<DokkaTask>().configureEach {
    dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:1.9.20")
    }
}
