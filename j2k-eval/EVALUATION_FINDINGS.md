# J2K Evaluation Findings — JMC Core Module

## Overview

| Metric | Value |
|--------|-------|
| Target project | JMC (Java Model Checker) — core module |
| Converter | IntelliJ IDEA static J2K (Kotlin 1.9.22) |
| Total Java files | 146 |
| Successfully converted | 146 (100%) |
| Compilation errors (with stdlib) | 1,101 |
| Avg structural match | 71.3% |

## Error Classification

The 1,101 compilation errors break down into three categories:

### 1. Missing third-party dependencies (expected, not a converter bug)

- `unresolved reference: sosy_lab` — Java SMT solver library not on classpath
- `unresolved reference: junit` — JUnit test framework not on classpath
- `unresolved reference: Testable` — JUnit annotation

These are expected when compiling converted files in isolation without the project's full dependency tree. Not a converter defect.

### 2. `package-info.java` conversion bug (real converter bug)

The converter incorrectly transforms `package-info.java` files. In Java, these files contain package-level Javadoc and annotations. The J2K converter produces `package-info.kt` files that:
- Reference private inner classes from other files (e.g., `cannot access 'JmcExecutorWorker': it is private in 'JmcExecutorService'`)
- Reference private nested types (e.g., `cannot access 'SchedulerThread': it is private in 'Scheduler'`)
- Include `import` statements for third-party libraries that were only mentioned in Javadoc

This is a clear converter bug. Kotlin doesn't have `package-info` files — the converter should either:
- Produce only a bare package declaration
- Skip these files entirely
- Convert only the annotations (not the Javadoc content)

Affected files: every `package-info.kt` in the output (17 files, ~hundreds of errors).

### 3. Smart cast failures on mutable properties (real converter limitation)

The converter translates Java fields as `var` (mutable). When the original Java code does:
```java
if (leftOperand instanceof AbstractBoolean) {
    ((AbstractBoolean) leftOperand).someMethod();
}
```

The converter produces:
```kotlin
if (leftOperand is AbstractBoolean) {
    leftOperand.someMethod() // ERROR: smart cast impossible
}
```

Kotlin's type system rejects this because `leftOperand` is a `var` — it could be reassigned between the `is` check and the usage. The fix is to use a local `val`:
```kotlin
val left = leftOperand
if (left is AbstractBoolean) {
    left.someMethod() // OK: local val can be smart-cast
}
```

This is a known J2K limitation. The converter doesn't introduce local copies for mutable fields that are type-checked.

Primary affected file: `JmcConcreteFormula.kt` (~40 errors from this pattern alone).

## Structural Analysis Summary

The 71.3% average structural match means the converter preserves most classes, methods, and fields, but:
- Some Java interfaces become Kotlin classes (or vice versa) due to how the converter handles abstract types
- Field counts often increase in Kotlin because constructor parameters are counted as fields
- Method counts sometimes decrease when Java getters/setters are converted to Kotlin properties

## Kotlin Idiom Usage

The converter produces moderately idiomatic Kotlin:
- Uses `val` over `var` where possible
- Converts some `switch` statements to `when` expressions
- Uses null-safety operators (`?.`, `?:`) in some cases
- Does NOT produce `data class` (never promotes classes automatically)
- Does NOT use extension functions
- Does NOT use `companion object` for static members (uses top-level functions or `@JvmStatic`)
