import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

val projectVersion = property("project.version").toString()

plugins {
    id("org.jetbrains.dokka")
    id("com.github.ben-manes.versions")
}

repositories {
    mavenCentral()
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:2.1.0")
        // classpath("org.jetbrains.dokka:kotlin-as-java-plugin:2.0.0")
    }
}

//dokka {
//    dokkaPublications.html {
//        outputDirectory.set(rootDir.resolve("build/dokka"))
//        outputDirectory.set(file("build/dokka"))
//    }
//}

dokka {
    dokkaPublications.html {
        includes.from("docs/introduction.md")
        outputDirectory.set(file("build/dokka"))
    }

    pluginsConfiguration.html {
        customAssets.from(
            "docs/funfix-512.png",
            "docs/favicon.ico"
        )
        customStyleSheets.from("docs/logo-styles.css")
        templatesDir.set(file("docs/dokka-templates"))
        footerMessage.set("© Alexandru Nedelcu")
    }
}

dependencies {
    // Aggregating project dokka tasks here triggers Dokka V1 tasks in subprojects
    // which fail when Dokka V2 mode is enabled. Instead, generate docs per
    // subproject (they already configure their own dokkaPublication tasks).
    // If an aggregated site is needed, run the v2 `dokkaGenerate` tasks from
    // the root or create a dedicated aggregation task that depends on
    // `dokkaGeneratePublicationHtml` for each subproject.
    // dokka(project(":tasks-jvm"))
    // dokka(project(":tasks-kotlin-coroutines"))
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(version)
        return isStable.not()
    }

    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
    checkForGradleUpdate = true
    outputFormatter = "html"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}
