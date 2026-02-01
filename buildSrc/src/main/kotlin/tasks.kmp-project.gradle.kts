import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar as VanniktechJavadocJar

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("tasks.base")
}

val dokkaOutputDir = layout.buildDirectory.dir("dokka").get().asFile

dokka {
    dokkaPublications.html {
        outputDirectory.set(dokkaOutputDir)
    }

    dokkaSourceSets.configureEach {
        val tag = "v${project.version}"
        val relativePath = project.projectDir.relativeTo(project.rootDir).invariantSeparatorsPath
        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl.set(URI("https://github.com/funfix/tasks/tree/${tag}/${relativePath}/src"))
            remoteLineSuffix.set("#L")
        }
    }
}

val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(deleteDokkaOutputDir, tasks.named("dokkaGeneratePublicationHtml"))
    from(dokkaOutputDir)
}

// Configure maven publishing to use custom javadocJar instead of auto-generated Dokka V1 task
mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = VanniktechJavadocJar.None(),
            sourcesJar = true
        )
    )
}

// Add the custom javadocJar to all publications
afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                // Only add javadocJar if this is not a sources/javadoc publication
                if (!name.contains("SourcesElements") && !name.contains("JavadocElements")) {
                    artifact(javadocJar)
                }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvm {}

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
        sourceCompatibility = JavaVersion.VERSION_17.majorVersion
        targetCompatibility = JavaVersion.VERSION_17.majorVersion
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
        }
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            // Set on a project-by-project basis
            // explicitApiMode = ExplicitApiMode.Strict
            // allWarningsAsErrors = true
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-jvm-default=enable")
        }
        kotlinJavaToolchain.toolchain.use(
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion)
            }
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion)
        }
}
