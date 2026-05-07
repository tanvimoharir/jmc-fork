# J2K Evaluation Findings — JMC Project

## What This Pipeline Does

This pipeline evaluates the quality of IntelliJ IDEA's static Java-to-Kotlin (J2K) converter by running it against the [JMC (Java Model Checker)](https://github.com/mpi-sws-rse/jmc) project. It measures:

1. **Conversion success** — can the converter handle all files?
2. **Compilation validity** — does the converted Kotlin compile?
3. **Structural preservation** — are classes, methods, and fields preserved?
4. **Idiomatic Kotlin usage** — does the output use Kotlin features?
5. **Code quality** — are there code smells like `!!` assertions or unconverted patterns?

The evaluation logic is written entirely in Kotlin and runs as a GitHub Actions pipeline.

---

## Results Overview

| Metric | Core Module | Agent Module | Edge Cases |
|--------|-------------|--------------|------------|
| Java files | 146 | 24 | 6 |
| Conversion rate | 100% | 100% | 100% |
| Compilation errors | 1,101 | 809 | 27 |
| Avg structural match | 71.3% | 67.0% | 70.4% |

---

## Identified Converter Deficiencies

### 1. `package-info.java` conversion produces invalid Kotlin

The converter transforms `package-info.java` (which contains only Javadoc and annotations in Java) into `package-info.kt` files with import statements that reference private inner classes from other files.

**Example errors:**
- `cannot access 'JmcExecutorWorker': it is private in 'JmcExecutorService'`
- `cannot access 'SchedulerThread': it is private in 'Scheduler'`

**Why:** Kotlin has no `package-info` equivalent. The converter should produce only a bare package declaration or skip these files entirely.

**Affected:** Every `package-info.kt` in the output (17 files in core module).

### 2. Nullable type inference for generic collection elements

In `JmcMatcher.kt` (agent module), the converter inferred `String?` for a lambda parameter from a `List<String>.stream()` call where the elements are non-null.

**Example:** `matchingPackages.stream().anyMatch { prefix: String? -> typeName.startsWith(prefix) }` — `startsWith` expects non-null `String` but gets `String?`.

**Why:** The converter is overly conservative about nullability when inferring types from Java generics in stream lambda parameters.

**Affected:** Agent module (`JmcMatcher.kt`), Edge cases (`StreamsAndLambdas.kt`, `ComplexGenerics.kt`).

### 3. Compiler OutOfMemoryError (core module)

When compiling all 146 converted core module files together, `kotlinc` runs out of heap space. This means the compilation error count for the core module may be incomplete.

**Mitigation:** The pipeline uses `-J-Xmx2g` flag. The agent module (24 files) and edge cases (6 files) compile without OOM.

### 4. Smart cast failures on mutable properties

The converter translates Java fields as `var` (mutable) and removes explicit casts, relying on Kotlin's smart casts. However, [smart casts don't work on `var` properties](https://kotlinlang.org/docs/typecasts.html#smart-casts) because another thread could change the value between the check and usage.

**Java (original):**
```java
private SymbolicOperand leftOperand;
if (leftOperand instanceof AbstractBoolean) {
    return getBoolValue((AbstractBoolean) leftOperand);
}
```

**Kotlin (converter output — broken):**
```kotlin
private var leftOperand: SymbolicOperand? = null
if (leftOperand is AbstractBoolean) {
    return getBoolValue(leftOperand)  // ERROR: smart cast impossible
}
```

**Fix:** The converter should introduce a local `val` copy before the type check. See `edge-cases/proposed-fix/SmartCastFix.md`.

**Affected:** `JmcConcreteFormula.kt` in core module (~65 errors from this pattern).

### 5. Nested anonymous classes produce type mismatches

In `NestedAnonymousClasses.kt`, the converter produces anonymous class implementations where the return types don't match the expected generic signatures.

**Example:** `return type mismatch: expected 'Callable<Comparator<String>>', actual '<anonymous>'`

**Why:** The converter doesn't properly handle the generic type parameters when converting nested anonymous class hierarchies.

**Affected:** Edge cases (`NestedAnonymousClasses.kt` — 6 errors).

### 6. Complex generics with streams produce Collector type mismatches

In `ComplexGenerics.kt`, the converter produces code where `Collectors.groupingBy` and `Collectors.mapping` have incompatible type arguments.

**Why:** The converter doesn't correctly translate Java wildcard types (`? extends`, `? super`) and captured types in complex stream collector chains.

**Affected:** Edge cases (`ComplexGenerics.kt` — 6 errors).

---

## Agent Module Findings

The agent module (24 files) contains bytecode instrumentation code using the ASM library — `ClassVisitor`, `MethodVisitor`, and related patterns for intercepting thread, lock, and executor operations at the bytecode level.

**Key observations:**
- All 24 files converted successfully (100%)
- 809 compilation errors, primarily from missing dependencies (`org.objectweb.asm`, `org.apache.logging.log4j`, `org.mpi_sws.jmc.checker`)
- The ASM visitor pattern (extending `ClassVisitor`/`MethodVisitor` and overriding `visit*` methods) converted without structural issues
- The nullable type inference issue (#2 above) was found in `JmcMatcher.kt`
- Structural match (67%) is slightly lower than core (71.3%) because the regex-based Kotlin analyzer has difficulty parsing the dense visitor method override patterns

**Agent-specific errors (excluding missing dependencies):**
- `type mismatch: inferred type is String? but String was expected` in `JmcMatcher.kt` — nullable inference on stream lambda parameter

---

## Patterns That Converted Successfully

| Pattern | File | Notes |
|---------|------|-------|
| Thread subclassing + `start()` override | `JmcThread.kt` | Correctly produces `open class JmcThread : Thread` |
| Builder pattern | `JmcCheckerConfiguration.kt` | Compiles, but not converted to idiomatic Kotlin (no named params/DSL) |
| Synchronized blocks | `SynchronizedPatterns.kt` | Compiles without errors |
| Enum with abstract methods | `EnumWithBehavior.kt` | Compiles without errors |
| ReentrantLock + Condition | `SynchronizedPatterns.kt` | Correctly preserved |
| Volatile fields | `SynchronizedPatterns.kt` | Correctly annotated with `@Volatile` |

---

## Kotlin Idiom Adoption (Core Module)

| Idiom | Adoption | Notes |
|-------|----------|-------|
| `val` over `var` (immutability) | 67% of files | Good — converter prefers immutability |
| `companion object` | 29% of files | Used for static members |
| String templates | 12% of files | Low — most string concatenation left as `+` |
| Null-safety operators | 9% of files | Low — converter rarely uses `?.` or `?:` |
| `when` expressions | 6% of files | Low — if-else chains not converted to `when` |
| `data class` | 1% of files | Converter almost never promotes classes |

---

## Conclusion

The static J2K converter successfully converts all files syntactically (100% conversion rate) but produces code with compilation errors primarily from:
- Missing dependencies (expected, not a converter defect)
- Smart cast failures on mutable properties (converter deficiency)
- Nullable type inference in stream lambdas (converter deficiency)
- Invalid `package-info.kt` generation (converter deficiency)

The converted code is structurally faithful (~70% match) but not idiomatically Kotlin — it's largely "Java written in Kotlin syntax" with low adoption of `when`, string templates, and `data class`.
