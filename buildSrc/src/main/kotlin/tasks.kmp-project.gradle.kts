import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.dokka")
    id("tasks.base")
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
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            // explicitApiMode = ExplicitApiMode.Strict
            // allWarningsAsErrors = true
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
        kotlinJavaToolchain.toolchain.use(
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(11)
            }
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(11)
        }
}
