import java.net.URI

plugins {
    `java-library`
    jacoco
    id("org.jetbrains.dokka")
    id("tasks.base")
}

dokka {
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
