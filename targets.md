# Lean Squad FV Targets for JMC

## Priority Targets

### 1. LamportVectorClock (PHASE 1)
- **File**: `core/src/main/java/org/mpi_sws/jmc/util/LamportVectorClock.java`
- **Phase**: Research (1) - In progress
- **Benefit**: Verify core properties of Lamport vector clocks, a fundamental distributed systems data structure
- **Key properties to verify**:
  - Vector clock happens-before relation is transitive and consistent
  - Vector update preserves monotonicity
  - Comparison operation is consistent (compare results match individual comparisons)
  - Vector grow operation preserves ordering invariants
- **Spec size**: ~200 lines of Lean 4
- **Proof tractability**: Medium - mostly structural reasoning, some arithmetic
- **Approximations**: Model only the vector operations, not Java reflection/memory model
- **Notes**: 
  - Has well-defined public API: happensBefore, update, compare
  - Immutable operations make formalization straightforward
  - Component class provides total order on individual slots

### 2. PartialOrder/TotalOrder relations (PHASE 1)
- **File**: `core/src/main/java/org/mpi_sws/jmc/util/PartialOrder.java`, `TotalOrder.java`
- **Phase**: Research (1)
- **Benefit**: Formalize the relation type system; prove properties of ordering relations
- **Key properties**:
  - Transitivity of relationships
  - Reflexivity/irreflexivity properties
  - Consistency between partial and total order implementations
- **Spec size**: ~100 lines
- **Proof tractability**: High - definitional
- **Notes**: Good foundation for LamportVectorClock verification

### 3. Scheduling/Comparison Operations (PHASE 1)
- **File**: Various strategy files under `core/src/main/java/org/mpi_sws/jmc/strategies/`
- **Phase**: Research (1)
- **Benefit**: Verify scheduling strategy correctness
- **Status**: Further investigation needed

## Key Observations
- JMC is a mature Java model checker with careful API design
- Strong emphasis on concurrency primitives (locks, atomics, schedulers)
- Vector clocks are a fundamental building block - good place to start
- Codebase follows clear separation of concerns

## Next Steps
1. Write informal specs for LamportVectorClock
2. Set up Lean 4 project
3. Formalize vector clock properties
4. Write proofs for core invariants
