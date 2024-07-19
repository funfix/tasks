plugins {
    java // Apply the Java plugin
}

java {
    // Set the Java version
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral() // Use Maven Central for dependencies
}

dependencies {
    testImplementation(project(":tasks-core"))
    testImplementation(project(":tasks-kotlin"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
}

tasks.withType<Test> {
    // Use JUnit Platform for running tests
    useJUnitPlatform()
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }
}
