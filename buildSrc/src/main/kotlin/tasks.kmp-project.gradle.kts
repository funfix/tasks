import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
}

repositories {
    mavenCentral()
}

group = "org.funfix"

val projectVersion = property("project.version").toString()
version = projectVersion.let { version ->
    if (!project.hasProperty("buildRelease"))
        "$version-SNAPSHOT"
    else
        version
}

publishing {
    repositories {
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/funfix/tasks")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        named<MavenPublication>("kotlinMultiplatform") {
            pom {
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

val dokkaOutputDir = layout.buildDirectory.dir("dokka").get().asFile

tasks.dokkaHtml {
    outputDirectory.set(dokkaOutputDir)
}

val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

val javadocJar = tasks.create<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
    from(dokkaOutputDir)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
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

    tasks.withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    tasks.register<Test>("testsOn21") {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(21)
            }
    }

    tasks.register<Test>("testsOn11") {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(11)
            }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("printInfo") {
    doLast {
        println("Group: $group")
        println("Project version: $version")
    }
}
