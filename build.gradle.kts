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
        classpath("org.jetbrains.dokka:dokka-base:2.0.0")
        // classpath("org.jetbrains.dokka:kotlin-as-java-plugin:2.0.0")
    }
}

//dokka {
//    dokkaPublications.html {
//        outputDirectory.set(rootDir.resolve("build/dokka"))
//        outputDirectory.set(file("build/dokka"))
//    }
//}

tasks.dokkaHtmlMultiModule {
    outputDirectory.set(file("build/dokka"))
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
