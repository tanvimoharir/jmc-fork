# Proposed Fix: Smart Cast Failures on Mutable Properties

## Problem

The J2K converter translates Java `instanceof` checks on mutable fields directly to Kotlin `is` checks, but doesn't account for Kotlin's smart cast rules. Smart casts don't work on `var` properties because another thread could change the value between the check and the usage.

**Java (original):**
```java
private SymbolicOperand leftOperand;

private boolean evalAtom() {
    if (leftOperand instanceof AbstractBoolean) {
        return getBoolValue((AbstractBoolean) leftOperand);
    } else if (leftOperand instanceof JmcBooleanFormula) {
        return ((JmcBooleanFormula) leftOperand).concreteEvaluation();
    }
    throw new IllegalStateException("Unknown operand type");
}
```

**Kotlin (current converter output — BROKEN):**
```kotlin
private var leftOperand: SymbolicOperand? = null

private fun evalAtom(): Boolean {
    if (leftOperand is AbstractBoolean) {
        return getBoolValue(leftOperand)  // ERROR: smart cast impossible
    } else if (leftOperand is JmcBooleanFormula) {
        return leftOperand.concreteEvaluation()  // ERROR: smart cast impossible
    }
    throw IllegalStateException("Unknown operand type")
}
```

## Proposed Fix

The converter should introduce a local `val` copy before the type check chain:

**Kotlin (proposed fix):**
```kotlin
private var leftOperand: SymbolicOperand? = null

private fun evalAtom(): Boolean {
    val left = leftOperand  // local immutable copy enables smart cast
    if (left is AbstractBoolean) {
        return getBoolValue(left)  // OK: smart cast works on local val
    } else if (left is JmcBooleanFormula) {
        return left.concreteEvaluation()  // OK: smart cast works on local val
    }
    throw IllegalStateException("Unknown operand type")
}
```

Even better, the converter could use a `when` expression:

**Kotlin (idiomatic fix):**
```kotlin
private var leftOperand: SymbolicOperand? = null

private fun evalAtom(): Boolean = when (val left = leftOperand) {
    is AbstractBoolean -> getBoolValue(left)
    is JmcBooleanFormula -> left.concreteEvaluation()
    else -> throw IllegalStateException("Unknown operand type")
}
```

## Implementation

The fix would be applied in the J2K converter's post-processing phase:

1. **Detect the pattern:** Find `if (expr is Type)` where `expr` is a `var` property
2. **Introduce local copy:** Insert `val localCopy = expr` before the if-chain
3. **Replace references:** Replace all uses of `expr` inside the if-branches with `localCopy`
4. **Optionally convert to `when`:** If the pattern is an if-else-if chain of `is` checks on the same variable, convert to a `when` expression

## Impact

In the JMC core module alone, this fix would resolve **65 compilation errors** in `JmcConcreteFormula.kt` — the single largest source of converter-induced compilation failures.

## References

- [Kotlin docs: Smart casts](https://kotlinlang.org/docs/typecasts.html#smart-casts) — "Smart casts do not work when the compiler cannot guarantee that the variable cannot change between the check and the usage"
- [Stack Overflow: Smart cast impossible after J2K conversion](https://stackoverflow.com/questions/64463225/kotlin-errors-after-converting-from-java)
