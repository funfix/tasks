# JSpecify Nullness - Reference

Concise guidance for applying JSpecify annotations in Java, including scope defaults, generic type parameters, and tooling constraints.

Sources:
- https://jspecify.dev/docs/start-here/
- https://jspecify.dev/docs/user-guide/
- https://jspecify.dev/docs/whether/
- https://jspecify.dev/docs/using/
- https://jspecify.dev/docs/applying/
- https://jspecify.dev/docs/spec/
- https://jspecify.dev/docs/tool-conformance/


## Table of Contents
- [Goals and model](#goals-and-model)
- [Core annotations](#core-annotations)
- [Null-marked scopes](#null-marked-scopes)
- [Type-use placement rules](#type-use-placement-rules)
- [Generics and type parameters](#generics-and-type-parameters)
- [Adoption and migration](#adoption-and-migration)
- [Tooling and conformance](#tooling-and-conformance)
- [Kotlin and annotation processors](#kotlin-and-annotation-processors)
- [Checklist](#checklist)
- [Test prompts](#test-prompts)

## Goals and model
- JSpecify defines standard nullness annotations and precise semantics for Java type usages.
- The model has four nullness states: nullable, non-null, parametric (type variable), and unspecified.
- `@NullMarked` makes unannotated types non-null by default within the scope; outside it, unannotated types are unspecified.

## Core annotations
- `@Nullable` means the annotated type usage can include `null`.
- `@NonNull` means the annotated type usage excludes `null`.
- `@NullMarked` applies to modules, packages, classes, or methods and makes unannotated types non-null by default.
- `@NullUnmarked` cancels a surrounding `@NullMarked` for incremental adoption.

Example:

```java
@NullMarked
final class Strings {
  static @Nullable String emptyToNull(String x) {
    return x.isEmpty() ? null : x;
  }

  static String nullToEmpty(@Nullable String x) {
    return x == null ? "" : x;
  }
}
```

## Null-marked scopes
- Packages are not hierarchical. `@NullMarked` on `com.example` does not affect `com.example.sub`.
- In a null-marked scope, unannotated types are treated as non-null, but local-variable root types are not annotated.
- `@NullUnmarked` is useful when only part of a package or class is ready for annotations.

## Type-use placement rules
JSpecify annotations are type-use annotations, so placement matters:

- Nested types: mark the nested type, not the outer type.

```java
Map.@Nullable Entry<String, String> entry;
```

- Arrays: the token after the annotation is what can be null.

```java
@Nullable String[] nullableElements;
String @Nullable [] nullableArray;
@Nullable String @Nullable [] bothNullable;
```

- Local variables: do not annotate the root type, but do annotate type arguments or array components.

```java
List<@Nullable String> names = new ArrayList<>();
```

If a tool reports an unrecognized annotation location, move the annotation to the nearest simple type or component.

## Generics and type parameters
- A type parameter without an explicit bound behaves like `<T extends Object>`.
- Inside `@NullMarked`, that means the type argument is non-null unless you make the bound nullable.

Allow nullable type arguments:

```java
@NullMarked
interface Box<T extends @Nullable Object> {
  T get();
  void set(T value);
}
```

Disallow nullable type arguments:

```java
@NullMarked
interface ImmutableBox<T> {
  T get();
}
```

Use `@Nullable` on a type-variable usage only when the result can be null even when the type argument is non-null:

```java
@NullMarked
interface ListLike<E extends @Nullable Object> {
  @Nullable E firstOrNull();
  E get(int index);
}
```

Use `@NonNull` on a type-variable usage to force non-null even when the type argument is nullable:

```java
@NullMarked
interface OptionalLike<T extends @Nullable Object> {
  Optional<@NonNull T> toOptional();
}
```

## Adoption and migration
Recommended steps for a new codebase or incremental migration:

1. Add the dependency `org.jspecify:jspecify:1.0.0` and do not hide it from consumers.
2. Start with a small, low-dependency package or class.
3. Mark obvious nullable usages (`return null`, `if (x == null)`, nullable fields).
4. Add `@NullMarked` to the scope and use `@NullUnmarked` for unannotated areas.
5. Fix type parameters and usages in generics.
6. Run nullness analysis and address findings, then expand to calling code.

Migration from JSR-305 or other annotations:
- Update imports to JSpecify.
- Fix array and nested-type placements to comply with type-use rules.
- Expect some build errors due to type-use placement restrictions.

## Tooling and conformance
- JSpecify defines semantics, not required diagnostics. Tools may choose their own error or warning behavior.
- Conformance is described in terms of questions about whether a usage is recognized, what the augmented type is, and whether an expression is a nullness subtype of its context.
- Some tools are partially conformant, especially around generics or unspecified nullness.

## Kotlin and annotation processors
- Kotlin compilers recognize JSpecify annotations; support level varies by version for `@Nullable`, `@NonNull`, and `@NullUnmarked`.
- If you rely on annotation processors like Dagger, you may need JDK 22+ due to older `javac` type-use annotation bugs.

## Checklist
- Use `@NullMarked` at package or class scope.
- Mark nullable return types and parameters with `@Nullable`.
- Decide per type parameter whether nullable type arguments are allowed.
- Verify type-use placement for arrays and nested types.
- Run a JSpecify-aware nullness checker and resolve findings.

## Test prompts
- "Annotate this API with JSpecify, including generics and `@NullMarked`, and explain call-site impacts."
- "Migrate these JSR-305 annotations to JSpecify and fix any type-use placement issues."
- "Given this signature, decide if the type parameter bound should be nullable and justify it."
- "Explain why `Map.@Nullable Entry` is required here and how arrays should be annotated."
