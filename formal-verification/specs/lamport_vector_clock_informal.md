# LamportVectorClock — Informal Specification

🔬 **Lean Squad** — Specification for `org.mpi_sws.jmc.util.LamportVectorClock`

## Purpose

Lamport vector clocks are a fundamental distributed systems data structure that track causal ordering of events across multiple processes or threads. Each process maintains a clock (vector of integers), and the happens-before partial order over clocks captures causality: if clock A happens-before clock B, then we can infer a causal dependency from A's events to B's events.

In JMC, vector clocks are used internally to analyze concurrency: the scheduler instruments thread events and uses happens-before relations to reason about which interleavings are sound and which may exhibit races.

## Core Abstraction

A `LamportVectorClock` is an immutable record containing:
- **`vector: int[]`** – An array of non-negative integers, one per process/thread. Index `i` represents the logical clock of process `i`.

Public operations:
- **Constructor** `LamportVectorClock(int size)` – Create a new zero-initialized vector of given size
- **Constructor** `LamportVectorClock(int[] vector)` – Create a clock from an existing vector (copies the array)
- **Constructor** `LamportVectorClock(LamportVectorClock other, int index)` – Create a new clock by incrementing the component at `index` in `other`; grows vectors if needed
- **`happensBefore(LamportVectorClock other): boolean`** – Determine if this clock is strictly less than `other` in the happens-before order
- **`update(LamportVectorClock other): void`** – Mutate this clock by taking the component-wise maximum with `other` (representing a synchronization point)
- **`compare(LamportVectorClock other): Relation`** – Return the partial order relation: LT, GT, EQ, or UNRELATED
- **`Component.compare(Component other): Relation`** – Total order comparison of a single clock component

## Specification

### Invariants (Global)

1. **Non-negative components**: All vector components are non-negative integers (∀i: vector[i] ≥ 0)
2. **Vector immutability via constructors**: Constructors that accept external arrays must copy; modifications to the caller's array must not affect the clock
3. **Index validity**: All indices passed to public methods must be valid (0 ≤ index < size)

### Data Structure Invariants

For any clock `vc`:
- `vc.vector.length == vc.getSize()` (array length matches reported size)
- `vc.vector[i] >= 0` for all i (non-negativity)

### Preconditions

| Operation | Precondition |
|-----------|--------------|
| `happensBefore(other)` | None; handles mismatched sizes by growing both vectors |
| `update(other)` | None; grows vectors to match if sizes differ |
| `compare(other)` | None; handles mismatched sizes |
| `LamportVectorClock(other, index)` | `index >= 0` (negative indices rejected) |

### Postconditions

#### Constructor: `LamportVectorClock(size)`
- **Effect**: Creates a new vector clock with `size` components, all initialized to 0
- **Result**: 
  - `result.getSize() == size`
  - `result.vector[i] == 0` for all `0 <= i < size`

#### Constructor: `LamportVectorClock(vector)`
- **Effect**: Creates a deep copy of the provided array
- **Result**:
  - `result.getSize() == vector.length`
  - `result.vector[i] == vector[i]` for all i
  - Modifications to the caller's `vector` do not affect `result.vector` (deep copy confirmed)

#### Constructor: `LamportVectorClock(other, index)`
- **Effect**: Creates a new clock by incrementing component `index` of `other`; grows vectors if `index >= other.size()`
- **Precondition**: `index >= 0`
- **Result**:
  - `result.getSize() >= max(other.getSize(), index + 1)`
  - `result.vector[index] == other.vector[index] + 1` (after growth if needed)
  - `result.vector[i] == other.vector[i]` for all `i != index` and `i < other.getSize()` (unchanged components)
  - If growth occurred, new components are initialized to 0
  - **Side effect**: If growth occurred, `other.vector` is also grown to `result.vector.length` to maintain consistency

#### `happensBefore(other): boolean`
- **Definition**: `this < other` in the happens-before order
- **Semantics**: This clock happens-before other if:
  1. `this.vector[i] <= other.vector[i]` for all i (component-wise ≤)
  2. There exists at least one `j` where `this.vector[j] < other.vector[j]` (strict inequality in at least one position)
- **Special case**: Handles vectors of different sizes by growing both to the same length before comparison
- **Result**: Returns true iff both conditions above hold; false otherwise

#### `update(other): void`
- **Effect**: Mutates this clock (side effect!)
- **Semantics**: Set each component to the maximum of the two clocks: `this.vector[i] := max(this.vector[i], other.vector[i])`
- **Precondition**: None; grows vectors if sizes mismatch
- **Postcondition**:
  - `this.vector[i] == max(old_this.vector[i], other.vector[i])` for all i
  - `this.getSize() == max(old_this.getSize(), other.getSize())`

#### `compare(other): Relation`
- **Definition**: Returns the partial order relation between this and other
- **Semantics**:
  - If `this.happensBefore(other)`: return `Relation.LT`
  - Else if `other.happensBefore(this)`: return `Relation.GT`
  - Else if `this.equals(other)`: return `Relation.EQ` (all components equal)
  - Else: return `Relation.UNRELATED` (incomparable; neither is ≤ the other)
- **Result**: One of {LT, GT, EQ, UNRELATED}

#### `equals(other): boolean`
- **Definition**: Component-wise equality
- **Semantics**: Return true iff `this.vector[i] == other.vector[i]` for all i, and sizes match
- **Precondition**: `other != null`
- **Result**: True if all components match; false otherwise

#### `Component.compare(other): Relation` (Total Order)
- **Precondition**: `this.index == other.index` (comparing components at the same position; throws InvalidComparisonException if not)
- **Semantics**: Compare the values at the shared index:
  - If `this.clock.vector[index] < other.clock.vector[index]`: return `Relation.LT`
  - Else if `this.clock.vector[index] > other.clock.vector[index]`: return `Relation.GT`
  - Else: return `Relation.EQ`
- **Result**: One of {LT, GT, EQ} (no UNRELATED; total order)

### Key Properties (Theorems to Prove)

1. **Happens-Before Transitivity**
   ```
   ∀ vc1, vc2, vc3:
     vc1.happensBefore(vc2) ∧ vc2.happensBefore(vc3) → vc1.happensBefore(vc3)
   ```

2. **Happens-Before Asymmetry**
   ```
   ∀ vc1, vc2:
     vc1.happensBefore(vc2) → ¬vc2.happensBefore(vc1)
   ```

3. **Update Monotonicity**
   ```
   ∀ vc1, vc2, vc2_old:
     vc2_old.clone() = vc2
     vc2.update(vc1)
     → ∀i: vc2.vector[i] >= vc2_old.vector[i]
   ```

4. **Update Idempotence**
   ```
   ∀ vc1, vc2:
     vc1.update(vc2)
     vc1_after_first = vc1.clone()
     vc1.update(vc2)
     → vc1_after_first.equals(vc1)
   ```

5. **Compare Consistency**
   ```
   ∀ vc1, vc2:
     vc1.compare(vc2) = LT ↔ vc1.happensBefore(vc2)
     vc1.compare(vc2) = GT ↔ vc2.happensBefore(vc1)
     vc1.compare(vc2) = EQ ↔ vc1.equals(vc2)
   ```

6. **Component Order Consistency**
   ```
   ∀ vc1, vc2, i:
     Component(i, vc1).compare(Component(i, vc2)) = LT
     ↔ vc1.vector[i] < vc2.vector[i]
   ```

## Edge Cases & Boundary Conditions

1. **Empty vector (size 0)**: A clock with no components
   - `happensBefore` on two empty clocks should return false (neither is strictly less than the other)
   - `update` on empty clocks should be a no-op

2. **Single-component vector (size 1)**: Degenerate case
   - Should behave like a scalar counter
   - Happens-before should match scalar `<`

3. **Mismatched vector sizes**: Operations automatically grow to accommodate
   - Implicitly initializes new components to 0
   - Both vectors are grown to the same size (synchronized)

4. **Maximum integer values**: Components could reach Int.MAX_VALUE
   - No explicit overflow protection; behavior is undefined
   - **Model assumption**: Integers are unbounded (or sufficiently large)

## Design Rationale (Inferred)

- **Immutability via constructors**: The three constructors enforce that external arrays are copied, preserving encapsulation
- **Mutable `update()` operation**: Allows efficient in-place synchronization; represents a synchronization barrier
- **Lazy vector growth**: Vectors grow on demand when indices exceed current size, supporting sparse use cases
- **Happens-before as ≤ order**: The definition (component-wise ≤ with strict inequality somewhere) is the standard distributed systems definition
- **Partial order relation type**: Explicitly models that not all pairs of clocks are comparable (UNRELATED case for concurrent events)

## Open Questions for Maintainers

1. **Vector growth side effect on `other`**: The constructor `LamportVectorClock(other, index)` grows `other.vector` if needed. Is this intentional (synchronization of sizes), or a bug?
   - If intentional: document clearly as a side effect
   - If unintended: consider passing vector length instead of the clock itself

2. **Null handling**: Methods like `happensBefore(null)` are not explicitly handled. Should they throw or return false?

3. **Overflow semantics**: At Int.MAX_VALUE, incrementing wraps to Int.MIN_VALUE (Java semantics). Should vector clocks guard against this?

---

**Created**: 2026-04-25 03:43 UTC  
**Target**: `org.mpi_sws.jmc.util.LamportVectorClock`  
**Source file**: `core/src/main/java/org/mpi_sws/jmc/util/LamportVectorClock.java` (lines 1–186)
