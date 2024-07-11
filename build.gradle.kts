import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

val projectVersion = property("project.version").toString()

plugins {
    alias(libs.plugins.versions)
}

repositories {
    mavenCentral()
}

subprojects {
    group = "org.funfix"
    version = projectVersion.let { version ->
        if (!project.hasProperty("buildRelease"))
            "$version-SNAPSHOT"
        else
            version
    }

    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
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
    }

    tasks.register("printVersion") {
        doLast {
            println("Project version: $version")
        }
    }
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
