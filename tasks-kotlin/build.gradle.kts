plugins {
    id("tasks.kmp-project")
}

mavenPublishing {
    pom {
        name = "Tasks / Kotlin"
        description = "Integration with Kotlin's Coroutines"
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":tasks-core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(project(":tasks-core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jvmTest by getting

        val jsMain by getting {
            dependencies {
                implementation(project(":tasks-core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jsTest by getting
    }
}
