# J2K Evaluation Report

**Generated:** 2026-05-07T09:29:13.499259
**Target project:** JMC (Java Model Checker) — core module
**Converter:** Kotlin static J2K (kotlin-compiler 1.9.22)

## Summary

| Metric | Value |
|--------|-------|
| Total Java files | 6 |
| Successfully converted | 6 |
| Failed to convert | 0 |
| Conversion rate | 100.0% |
| Compilation success | NO |
| Compilation errors | 20 |
| Avg structural match | 70.4% |

## Compilation Errors

Total compilation errors: **20**

First 20 errors:

```
converted/ComplexGenerics.kt:15:45: error: argument type mismatch: actual type is '(Number) -> Double', but '((Number?) -> Double)!' was expected.
converted/ComplexGenerics.kt:34:17: error: argument type mismatch: actual type is 'Collector<Map.Entry<K (of fun <K, V> invertMap), V (of fun <K, V> invertMap)>!, CapturedType(*), (Mutable)Map<V! (of fun <K, V> invertMap), List<K (of fun <K, V> invertMap)>!>!>!', but 'Collector<in Map.Entry<K (of fun <K, V> invertMap), V (of fun <K, V> invertMap)>!, Any!, Map<V (of fun <K, V> invertMap), List<K (of fun <K, V> invertMap)>>!>!' was expected.
converted/ComplexGenerics.kt:35:91: error: unresolved reference 'value'.
converted/ComplexGenerics.kt:36:21: error: argument type mismatch: actual type is 'Collector<Map.Entry<K (of fun <K, V> invertMap), V (of fun <K, V> invertMap)>!, CapturedType(*), List<K (of fun <K, V> invertMap)>!>!', but 'Collector<in Map.Entry<K (of fun <K, V> invertMap), V (of fun <K, V> invertMap)>!, Any!, List<K (of fun <K, V> invertMap)>!>!' was expected.
converted/ComplexGenerics.kt:37:95: error: unresolved reference 'key'.
converted/ComplexGenerics.kt:38:25: error: argument type mismatch: actual type is 'Collector<K! (of fun <K, V> invertMap), CapturedType(*), (Mutable)List<K! (of fun <K, V> invertMap)>!>!', but 'Collector<in K! (of fun <K, V> invertMap), Any!, List<K (of fun <K, V> invertMap)>!>!' was expected.
converted/NestedAnonymousClasses.kt:9:16: error: return type mismatch: expected 'Callable<Comparator<String>>', actual '<anonymous>'.
converted/NestedAnonymousClasses.kt:10:34: error: return type of 'fun call(): Comparator<String>' is not a subtype of the return type of the overridden member 'fun call(): Comparator<String?>?' defined in 'edgecases.<anonymous>'.
converted/NestedAnonymousClasses.kt:11:24: error: class '<anonymous>' is not abstract and does not implement abstract member:
converted/NestedAnonymousClasses.kt:11:24: error: return type mismatch: expected 'Comparator<String>', actual '<anonymous>'.
converted/NestedAnonymousClasses.kt:12:21: error: 'compare' overrides nothing. Potential signatures for overriding:
converted/NestedAnonymousClasses.kt:24:25: error: variable expected.
converted/StreamsAndLambdas.kt:12:21: error: argument type mismatch: actual type is '(String) -> Boolean', but '((String?) -> Boolean)!' was expected.
converted/StreamsAndLambdas.kt:13:26: error: argument type mismatch: actual type is '(String) -> String', but '((String?) -> String!)!' was expected.
converted/StreamsAndLambdas.kt:14:41: error: argument type mismatch: actual type is 'Collector<String!, CapturedType(*), (Mutable)List<String!>!>!', but 'Collector<in String!, Any!, List<String>!>!' was expected.
converted/StreamsAndLambdas.kt:21:17: error: argument type mismatch: actual type is 'Collector<String!, CapturedType(*), (Mutable)Map<Int!, (Mutable)List<String!>!>!>!', but 'Collector<in String?, Any!, Map<Int, List<String>>!>!' was expected.
converted/StreamsAndLambdas.kt:29:23: error: argument type mismatch: actual type is '(String) -> Int', but '((String?) -> Int)!' was expected.
converted/StreamsAndLambdas.kt:36:26: error: argument type mismatch: actual type is '(String) -> String', but '((String?) -> String!)!' was expected.
converted/StreamsAndLambdas.kt:46:41: error: argument type mismatch: actual type is 'Collector<String!, CapturedType(*), (Mutable)List<String!>!>!', but 'Collector<in String!, Any!, List<String>!>!' was expected.
converted/StreamsAndLambdas.kt:69:36: error: argument type mismatch: actual type is 'Collector<R! (of fun <T, R> applyTransformer), CapturedType(*), (Mutable)List<R! (of fun <T, R> applyTransformer)>!>!', but 'Collector<in R! (of fun <T, R> applyTransformer), Any!, List<R (of fun <T, R> applyTransformer)>!>!' was expected.
```

## Structural Analysis

Compares the number of classes, methods, and fields between the original Java and converted Kotlin. A preservation rate below 90% indicates the converter may have lost or merged structural elements.

| Element | Java (original) | Kotlin (converted) | Preservation | Status |
|---------|----------------|-------------------|-------------|--------|
| Classes | 8 | 6 | 75% | ⚠️ POTENTIAL ISSUE |
| Methods | 38 | 36 | 95% | OK |
| Fields | 12 | 16 | 100% | OK |

**Lowest structural match files:**

| File | Match | Java (classes/methods/fields) | Kotlin (classes/methods/fields) | Status |
|------|-------|------------------------------|-------------------------------|--------|
| StreamsAndLambdas.java | 50% | 1/5/0 | 1/8/5 | Low match |
| EnumWithBehavior.java | 57% | 0/7/1 | 0/5/0 | Low match |
| MutableFieldInstanceof.java | 65% | 3/8/4 | 1/8/1 | Low match |
| NestedAnonymousClasses.java | 67% | 1/5/0 | 1/5/1 | Low match |
| SynchronizedPatterns.java | 89% | 1/6/6 | 1/4/8 | Low match |

## Kotlin Idiom Usage

Measures how many files use idiomatic Kotlin features. Higher percentages indicate the converter produced Kotlin-native code rather than Java-with-Kotlin-syntax.

| Idiom | Files Using It | Percentage | Status |
|-------|---------------|-----------|--------|
| More `val` than `var` declarations (immutability) | 4 / 6 | 67% | OK |
| `when` expressions (replaces switch/if-else) | 0 / 6 | 0% | ⚠️ POTENTIAL ISSUE — low adoption |
| Null-safety operators (`?.`, `?:`) | 0 / 6 | 0% | OK |
| String templates (`${}`) | 1 / 6 | 17% | OK |
| `companion object` (for static members) | 2 / 6 | 33% | OK |
| `data class` (for value types) | 0 / 6 | 0% | ⚠️ POTENTIAL ISSUE — converter never promotes to data class |

## Conversion Quality

Detects code patterns that indicate poor conversion quality. Each metric has an acceptable range — values outside that range are flagged as potential issues.

| Metric | Type | Value | Acceptable Range | Comments |
|--------|------|-------|-----------------|----------|
| `!!` non-null assertions | count | 0 | 0 | No issues |
| Line ratio (Kotlin / Java) | ratio | 2.68 | 0.7 – 1.0 | ⚠️ POTENTIAL ISSUE — converted code is more verbose than original |
| `if-else is` chains | count | 2 | 0 | ⚠️ POTENTIAL ISSUE — should be `when` expressions |
| String concatenation (`+`) | count | 5 | 0 | ⚠️ POTENTIAL ISSUE — should use string templates |
| Explicit casts (`as`) | count | 7 | 0 | ⚠️ POTENTIAL ISSUE — could use smart casts instead |
| JVM interop annotations | count | 0 | >0 for libraries | ⚠️ POTENTIAL ISSUE — may break Java callers |
| `open` classes | count | 0 | matches inheritable classes | Needed for classes with subclasses |

## Observations

- **Conversion rate: 6/6 (100%)** — the static J2K converter handles all files syntactically.
- ⚠️ **POTENTIAL ISSUE — Type mismatches: 14 errors** — a J2K converter deficiency. The converter inferred nullable types (`String?`) for generic collection elements where the original Java code used non-null values. For example, `List<String>` stream lambdas get parameter type `String?` instead of `String`, causing `startsWith(prefix)` to fail.
- **Structural fidelity: 70.4% average** — 1 files have ≥90% match, 0 files have <50% match.
- **Kotlin idioms:** 4/6 files prefer `val` over `var`, 0 use `when` expressions, 0 use null-safety operators. No files use `data class` (converter never promotes classes).

## Hypotheses Tested

Each hypothesis was formulated before running the evaluation and tested against the actual results.

| Hypothesis | Result | Evidence |
|-----------|--------|----------|

## How to Interpret These Results

- **⚠️ POTENTIAL ISSUE** flags indicate areas where the converter produced suboptimal or incorrect output.
- **Compilation errors** from unresolved references are expected (missing dependencies) and do not indicate converter defects.
- **Compilation errors** from smart casts, type mismatches, or private access are real converter deficiencies.
- **Structural match** measures whether the converter preserved all classes, methods, and fields. Values below 90% warrant investigation.
- **Idiom usage** below 10% for `when` expressions or string templates suggests the converter produced Java-style code in Kotlin syntax.
- **`!!` assertions** are the strongest signal of poor conversion — each one is a potential NullPointerException at runtime.
