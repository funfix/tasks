# Tasks

## Project Layout

- **`tasks-jvm/`** — Core Java library (`org.funfix.tasks.jvm`). Java 17+, Error Prone + NullAway enabled.
- **`tasks-kotlin-coroutines/`** — Kotlin coroutines integration, depends on `tasks-jvm`. Strict explicit API mode.
- **`buildSrc/`** — Gradle convention plugins (shared build config).

## Java & Null Safety

All Java code uses **JSpecify** annotations for null-safety. NullAway is configured in error mode.

Use the **jspecify-nullness** skill when working with JSpecify annotations, null-safe generics, or NullAway conformance.

## Acceptance Criteria

For all work to be considered complete:
```sh
./gradlew check
```

## HOW-TOs

### Update project's dependencies

```bash
make dependency-updates-ci
```

This will generate these reports:
- For the Gradle project(s):
  - `./build/dependencyUpdates/report.html`
  - `./tasks-jvm/build/dependencyUpdates/report.html`
  - `./tasks-kotlin-coroutines/build/dependencyUpdates/report.html`

RULES: 
- Never upgrade major versions (semver), instead ask the user or warn them!!!
- Never upgrade to SNAPSHOT, RC, or milestone versions.
- Fix breakage, but apply good judgement.
