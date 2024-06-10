import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    // https://kotlinlang.org/docs/multiplatform-full-stack-app.html
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        withJava()

    }

    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

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
            }
        }

        val jvmTest by getting

        val jsMain by getting
        val jsTest by getting
    }

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "8"
        targetCompatibility = "8"
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}
