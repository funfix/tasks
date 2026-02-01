plugins {
    id("com.vanniktech.maven.publish")
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        inceptionYear.set("2024")
        url = "https://github.com/funfix/tasks"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
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

tasks.register("printInfo") {
    doLast {
        println("Group: $group")
        println("Project version: $version")
    }
}
