import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    id("tasks.java-project")
    id("tasks.versions")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

mavenPublishing {
    pom {
        name.set("Funfix Tasks (JVM)")
        description.set("Task datatype, meant for cross-language interoperability.")
    }
}

dependencies {
    api(libs.jspecify)

    compileOnly(libs.jetbrains.annotations)
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform("org.junit:junit-bom:6.0.2"))
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
    
}

tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        check("RequireExplicitNullMarking", CheckSeverity.ERROR)
        nullaway {
            error()
            onlyNullMarked = true
        }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.nullaway {
        disable()
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
