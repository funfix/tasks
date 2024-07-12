// Use the build script defined in buildSrc
apply(from = rootProject.file("buildSrc/shared.gradle.kts"))

plugins {
    java
    `maven-publish`
    signing
    jacoco
}

dependencies {
    implementation(libs.jspecify)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.majorVersion
    targetCompatibility = JavaVersion.VERSION_17.majorVersion
}

tasks.register<Test>("testsOn21") {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.register<Test>("testsOn17") {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            pom {
                name = "Tasks (core, JVM)"
                description = "Cross-language utilities for working with concurrent tasks"
                url = "https://github.com/funfix/tasks"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
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
    }
}
