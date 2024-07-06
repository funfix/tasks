import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kotlinx.kover.get().pluginId)
    id(libs.plugins.dokka.get().pluginId)
    id("maven-publish")
    id("signing")
}

val dokkaOutputDir = layout.buildDirectory.dir("dokka").get().asFile

tasks.dokkaHtml {
    outputDirectory.set(dokkaOutputDir)
}

val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

val javadocJar =
    tasks.create<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
        from(dokkaOutputDir)
    }

publishing {
    publications {
        register<MavenPublication>("gpr") {
            pom {
                name = "Tasks (core, JVM)"
                description = "Cross-language utilities for working with concurrent tasks"
                url = "https://github.com/funfix/tasks"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "alexandru"
                        name = "Alexandru Nedelcu"
                        email = "noreply@alexn.org"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/funfix/tasks.git"
                    developerConnection = "scm:git:ssh://github.com/funfix/tasks.git"
                    url = "https://github.com/funfix/tasks"
                }

                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/funfix/tasks/issues"
                }
            }
        }
    }
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

//    js(IR) {
//        browser {
//            testTask {
//                useKarma {
//                    useChromeHeadless()
//                }
//            }
//        }
//    }

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
            dependencies {}
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.jspecify)
            }
        }

//        val jsMain by getting
//        val jsTest by getting
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    tasks.register<Test>("testsOn21") {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(21)
            }
    }

    tasks.register<Test>("testsOn17") {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(17)
            }
    }
}
