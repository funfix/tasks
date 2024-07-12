repositories {
    mavenCentral()
}

apply(plugin = "maven-publish")

val projectVersion = property("project.version").toString()

group = "org.funfix"
version = projectVersion.let { version ->
    if (!project.hasProperty("buildRelease"))
        "$version-SNAPSHOT"
    else
        version
}

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

        maven {
            name = "OSSRH"
            url = uri(
                if (project.hasProperty("buildRelease"))
                    "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                else
                    "https://oss.sonatype.org/content/repositories/snapshots/"
            )
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

tasks.register("printVersion") {
    doLast {
        println("Project version: $version")
    }
}
