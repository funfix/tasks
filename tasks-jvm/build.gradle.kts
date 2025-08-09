import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("tasks.java-project")
}

mavenPublishing {
    pom {
        name.set("Funfix Tasks (JVM)")
        description.set("Task datatype, meant for cross-language interoperability.")
    }
}

dependencies {
    api(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.errorprone.nullaway)

    compileOnly(libs.jetbrains.annotations)

    testImplementation(platform("org.junit:junit-bom:5.12.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.majorVersion
    targetCompatibility = JavaVersion.VERSION_17.majorVersion
    options.compilerArgs.addAll(listOf(
        "-Xlint:deprecation",
//        "-Werror"
    ))

    options.errorprone {
        disableAllChecks.set(true)
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "org.funfix")
    }
}

tasks.register<Test>("testsOn21") {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_21.majorVersion)
    }
}

tasks.register<Test>("testsOn17") {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion)
    }
}
