# Formal Verification Research for JMC

🔬 **Lean Squad** — Automated formal verification survey for the JMC (Java Model Checker) project.

## Executive Summary

JMC is a sophisticated Java model checker for concurrent systems. This research identifies high-value formal verification targets, focusing on the core distributed systems utilities that form the foundation of JMC's checking algorithms.

**Recommended tool**: Lean 4 with Mathlib, selected for its strong support of abstract algebra (partial/total orders), decidable propositions, and the ability to directly model the pure functional core of JMC's APIs.

## Repository Overview

**Project**: JMC (Java Model Checker)  
**Language**: Java  
**Size**: ~150 Java files, ~20K LOC in core  
**Domain**: Concurrent software model checking  
**Key abstractions**: Lamport vector clocks, scheduling strategies, concurrency primitives, state management

### Architecture
- **`core/`**: Runtime instrumentation, model checker, solver interface, scheduling strategies
- **`agent/`**: Bytecode instrumentation for thread/lock/atomic operations
- **`gradle-plugin/`**: Build system integration

### Concurrency Focus
JMC provides abstract models of Java concurrency primitives:
- Thread creation and synchronization (`JmcThread`)
- Atomic variables (`JmcAtomicInteger`, `JmcAtomicReference`, etc.)
- Locks (`JmcReentrantLock`)
- Executors and futures
- Lock-free data structures in test programs

## FV-Amenable Targets

### 1. **Lamport Vector Clocks** (High Priority)

**File**: `core/src/main/java/org/mpi_sws/jmc/util/LamportVectorClock.java`

**Why this matters**:
- Vector clocks are a fundamental distributed systems primitive used internally by JMC for tracking causality
- The happens-before relation is used to detect race conditions and verify partial order properties
- Correctness of vector clock operations is critical to soundness of JMC's analysis

**What to verify**:
1. **Happens-before transitivity**: If A happens-before B and B happens-before C, then A happens-before C
2. **Update monotonicity**: Updating a clock with another clock never decreases its components
3. **Comparison consistency**: The `compare()` method returns results consistent with individual `happensBefore()` checks
4. **Vector growth invariants**: Growing vectors to accommodate larger indices preserves ordering properties
5. **Component comparison correctness**: The `Component` class total order matches the underlying vector clock components

**Specification size**: ~150–200 lines of Lean 4

**Proof tractability**: **Medium** – mostly straightforward induction and structural reasoning. The `Math.max` operation and component-wise comparison allow decidable propositions.

**Approximations**:
- Model only pure functional vector operations
- Assume Java array semantics correctly implement the intended data structure (do not model memory aliasing or mutation races)
- Omit static initialization and class loading
- Treat integer bounds as unbounded (no overflow verification)

**Example theorem**:
```lean
theorem happensBefore_transitive (vc1 vc2 vc3 : LamportVectorClock) :
  vc1.happensBefore vc2 → vc2.happensBefore vc3 → vc1.happensBefore vc3 := by
  sorry
```

**Existing tests**: None currently in the repo; good opportunity to write correspondence tests.

---

### 2. **PartialOrder and TotalOrder Relations** (Medium Priority)

**Files**: 
- `core/src/main/java/org/mpi_sws/jmc/util/PartialOrder.java`
- `core/src/main/java/org/mpi_sws/jmc/util/TotalOrder.java`

**Why this matters**:
- These define the comparison abstraction used throughout JMC (LamportVectorClock, Event comparison, etc.)
- Verifying the relation type itself provides a foundation for all downstream comparison-based proofs

**What to verify**:
1. Partial order properties (reflexivity, antisymmetry, transitivity) where applicable
2. Total order properties on Component and other concrete instances
3. Consistency between PartialOrder and TotalOrder implementations
4. Exhaustiveness of the Relation enum (can't miss an order case)

**Specification size**: ~80–120 lines

**Proof tractability**: **High** – largely definitional; many proofs close by `decide` or simple case analysis.

**Approximations**:
- Model as Lean type classes over abstract types
- Do not model exception handling (InvalidComparisonException)

---

### 3. **Task Manager and Scheduler** (Lower Priority)

**Files**: 
- `core/src/main/java/org/mpi_sws/jmc/runtime/TaskManager.java`
- `core/src/main/java/org/mpi_sws/jmc/runtime/scheduling/Scheduler.java`

**Why this matters**:
- Scheduler correctness is central to JMC's ability to explore all interleavings
- Scheduling state must satisfy invariants (no tasks leaked, no duplicate task IDs, etc.)

**Challenge**: These are more imperative and stateful than vector clocks. Initial FV effort should focus on simpler targets.

---

## Methodology

### Phase 1: Research & Spec Extraction
1. Identify FV-friendly targets (done above)
2. Read source code and infer design intent
3. Write informal specifications capturing correctness properties

### Phase 2: Lean 4 Formalization
1. Set up Lake project with Mathlib
2. Translate informal specs to Lean 4 type definitions and propositions
3. Write function stubs and property statements (using `sorry`)

### Phase 3: Implementation Models
1. Translate core JMC operations into Lean 4 (functional equivalents)
2. Update proposition statements to reference Lean implementations

### Phase 4: Proofs
1. Prove propositions using Lean 4 tactics (omega, simp, induction, decide)
2. Report any counterexamples or unsoundness found

### Phase 5: Validation
1. Write executable correspondence tests comparing Lean model against original
2. Verify model fidelity to source code

## Tool Choice: Lean 4

**Why Lean 4 over alternatives (Coq, Isabelle, F*)**:
- **Strong Mathlib ecosystem**: Extensive pre-proved lemmas on partial orders, sets, arithmetic
- **Decidable propositions**: Many JMC properties are decidable (finite vector sizes); `decide` tactic closes them automatically
- **Tactic power**: `omega` tactic handles arithmetic goals efficiently; `simp` with decision procedures is effective
- **Functional style**: Maps cleanly to Java's API-level operations without complex imperative models

**Key Mathlib modules**:
- `Mathlib.Order.RelClasses`: Partial order, total order, relation abstraction
- `Mathlib.Algebra.Order.Ring.Lemmas`: Arithmetic reasoning
- `Mathlib.Data.Vector`: Vector operations and lemmas
- `Mathlib.Data.Fintype`: Finite type reasoning

## Expected Timeline

| Phase | Target | Est. Effort | Feasibility |
|-------|--------|-------------|-------------|
| 1–2 | LamportVectorClock informal + Lean spec | 1–2 runs | High |
| 3–4 | LamportVectorClock impl + core theorems | 1–2 runs | Medium–High |
| 5 | Correspondence tests & validation | 1 run | Medium |
| — | PartialOrder/TotalOrder formalization | 1 run | High |
| — | Scheduling verification (future) | 2+ runs | Lower |

## Known Gaps & Open Questions

1. **Integer overflow**: Should we verify under bounded integer semantics or abstract integers?
   - **Decision**: Start with abstract integers; add overflow verification if needed.

2. **Concurrency**: JMC instruments multi-threaded code. Can we verify without modeling the full threading model?
   - **Decision**: Focus on utilities that are thread-safe by design (immutable ops, etc.); defer scheduler verification.

3. **Exception paths**: Should `grow()` throwing RuntimeException be modelled?
   - **Decision**: Model as a precondition ("vectors must have compatible sizes after growth"); omit exception reasoning initially.

## Related Work

- **Aesir** (Hawblitzel et al.): Verified distributed systems in Dafny; uses vector clocks
- **IronFleet**: Verified Paxos and consistency protocols; similar partial order reasoning
- **Lamport & Masse**: Original vector clock formalization in temporal logic
- **Tlaplus model checker**: Formal verification baseline for this domain

## Next Steps

1. ✅ Research complete — identified 3 high-value targets
2. ⬜ Task 2: Write informal spec for LamportVectorClock
3. ⬜ Task 3: Create Lean 4 project and formalize spec
4. ⬜ Task 4: Implement Lean model of vector clock operations
5. ⬜ Task 5: Prove core theorems (happens-before transitivity, update monotonicity, etc.)
6. ⬜ Task 6: Write and run correspondence tests

---

**Last Updated**: 2026-04-25 03:43 UTC  
**Survey Scope**: Main JMC codebase, versions 0.1.2+
