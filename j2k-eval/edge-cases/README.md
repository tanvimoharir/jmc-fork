# Edge Case Dataset

Custom Java files designed to stress-test the static J2K converter with tricky patterns.

## Files

| File | Pattern Tested | Expected Difficulty |
|------|---------------|-------------------|
| `NestedAnonymousClasses.java` | Deeply nested anonymous classes, variable capture | Should become lambdas |
| `ComplexGenerics.java` | Wildcards, bounded types, self-referential generics | Verbose or incorrect signatures |
| `MutableFieldInstanceof.java` | `instanceof` on mutable fields | **CONFIRMED FAILURE** — produces smart cast errors |
| `SynchronizedPatterns.java` | synchronized blocks, volatile, wait/notify, double-checked locking | May not convert idiomatically |
| `StreamsAndLambdas.java` | Java streams, method references, SAM conversion | Should become Kotlin collection ops |
| `EnumWithBehavior.java` | Enums with abstract methods and per-constant overrides | Complex enum translation |

## How to Run

Convert these files using IntelliJ's "Convert Java File to Kotlin File" action, then compare the output against idiomatic Kotlin.

## Proposed Fix

See `proposed-fix/SmartCastFix.md` for a detailed fix proposal for the smart cast issue identified in the JMC evaluation.
