plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.0")
}

tasks.test {
    useJUnitPlatform()
}