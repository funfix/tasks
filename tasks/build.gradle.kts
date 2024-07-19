import org.jetbrains.dokka.gradle.DokkaTask

plugins {
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

tasks.withType<DokkaTask>().configureEach {
    dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:1.9.20")
    }
}
