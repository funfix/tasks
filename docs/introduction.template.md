# Module Tasks

This is a library meant for library authors that want to build libraries that work across Java, Scala, or Kotlin, without having to worry about interoperability with whatever method of I/O that the library is using under the hood.

## Usage

Read the [Javadoc](https://javadoc.io/doc/org.funfix/tasks-jvm/${version}/org/funfix/tasks/jvm/package-summary.html). Better documentation is coming.

---

Maven:
```xml
<dependency>
  <groupId>org.funfix</groupId>
  <artifactId>tasks-jvm</artifactId>
  <version>${version}</version>
</dependency>
```

Gradle:
```kotlin
dependencies {
    implementation("org.funfix:tasks-jvm:${version}")
}
```

sbt:
```scala
libraryDependencies += "org.funfix" % "tasks-jvm" % "${version}"
```
