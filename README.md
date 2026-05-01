# Tasks

[![build](https://github.com/funfix/tasks/actions/workflows/build.yaml/badge.svg)](https://github.com/funfix/tasks/actions/workflows/build.yaml) [![maven](https://img.shields.io/maven-central/v/org.funfix/tasks-jvm.svg)](https://central.sonatype.com/artifact/org.funfix/tasks-jvm) [![javadoc](https://javadoc.io/badge2/org.funfix/tasks-jvm/javadoc.svg)](https://javadoc.io/doc/org.funfix/tasks-jvm)

This is a library meant for library authors that want to build libraries that work across Java, Scala, or Kotlin, without having to worry about interoperability with whatever method of I/O that the library is using under the hood.

## Usage

Read the [Javadoc](https://javadoc.io/doc/org.funfix/tasks-jvm/0.4.1/org/funfix/tasks/jvm/package-summary.html).
Better documentation is coming.

### Migration Note (v0.5.0)

The `AsyncFun` interface has changed to improve cancellation management and simplify the API. This is a source and binary incompatible change.

**Old shape:**
```java
Task.fromAsync((executor, callback) -> {
    // ...
    return () -> { /* cleanup */ };
});
```

**New shape:**
```java
Task.fromAsync(continuation -> {
    var executor = continuation.getExecutor();
    continuation.invokeOnCancellation(() -> { /* cleanup */ });
    // ...
});
```

Key differences:
- The `executor` and `callback` are now encapsulated in the `Continuation`.
- Cancellation cleanup is registered via `continuation.invokeOnCancellation(finalizer)` instead of returning a `Cancellable`.
- `continuation.onCancellation()` signals that the task has completed due to cancellation, whereas `invokeOnCancellation(finalizer)` registers a cleanup action to run when cancellation occurs.

---

Maven:
```xml
<dependency>
  <groupId>org.funfix</groupId>
  <artifactId>tasks-jvm</artifactId>
  <version>0.4.1</version>
</dependency>
```

Gradle:
```kotlin
dependencies {
    implementation("org.funfix:tasks-jvm:0.4.1")
}
```

sbt:
```scala
libraryDependencies += "org.funfix" % "tasks-jvm" % "0.4.1"
```
