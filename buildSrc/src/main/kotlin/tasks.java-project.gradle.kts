import java.net.URI
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar as VanniktechJavadocJar

plugins {
    `java-library`
    jacoco
    id("org.jetbrains.dokka")
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
        JavaLibrary(
            javadocJar = VanniktechJavadocJar.None(),
            sourcesJar = true
        )
    )
}

// Add the custom javadocJar to the publication
afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("maven") {
                artifact(javadocJar)
            }
        }
    }
}
