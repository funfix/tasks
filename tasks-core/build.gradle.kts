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
            dependencies {
            }
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
                implementation(libs.jspecify)
            }
        }

        val jsMain by getting
        val jsTest by getting
    }
}
