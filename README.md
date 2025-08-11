# Tasks

[![build](https://github.com/funfix/tasks/actions/workflows/build.yaml/badge.svg)](https://github.com/funfix/tasks/actions/workflows/build.yaml) [![maven](https://img.shields.io/maven-central/v/org.funfix/tasks-jvm.svg)](https://central.sonatype.com/artifact/org.funfix/tasks-jvm) [![javadoc](https://javadoc.io/badge2/org.funfix/tasks-jvm/javadoc.svg)](https://javadoc.io/doc/org.funfix/tasks-jvm)

This is a library meant for library authors that want to build libraries that work across Java, Scala, or Kotlin, without having to worry about interoperability with whatever method of I/O that the library is using under the hood.

## Usage

Read the [Javadoc](https://javadoc.io/doc/org.funfix/tasks-jvm/0.3.0/org/funfix/tasks/jvm/package-summary.html).
Better documentation is coming.

---

Maven:
```xml
<dependency>
  <groupId>org.funfix</groupId>
  <artifactId>tasks-jvm</artifactId>
  <version>0.3.0</version>
</dependency>
```

Gradle:
```kotlin
dependencies {
    implementation("org.funfix:tasks-jvm:0.3.0")
}
```

sbt:
```scala
libraryDependencies += "org.funfix" % "tasks-jvm" % "0.3.0"
```
