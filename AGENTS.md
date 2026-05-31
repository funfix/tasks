# Tasks

## Project Layout

- **`tasks-jvm/`** — Core Java library (`org.funfix.tasks.jvm`). Java 17+, Error Prone + NullAway enabled.
- **`tasks-kotlin-coroutines/`** — Kotlin coroutines integration, depends on `tasks-jvm`. Strict explicit API mode.
- **`buildSrc/`** — Gradle convention plugins (shared build config).

## Java & Null Safety

All Java code uses **JSpecify** annotations for null-safety. NullAway is configured in error mode.

Use the **jspecify-nullness** skill when working with JSpecify annotations, null-safe generics, or NullAway conformance.

## Acceptance Criteria

Work is not done until `make check-all` passes.

```sh
make check-all
```


