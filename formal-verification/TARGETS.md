# FV Targets Progress Tracker

🔬 **Lean Squad** — Formal verification target tracking for JMC.

| # | Target | File | Phase | Status | Notes |
|---|--------|------|-------|--------|-------|
| 1 | LamportVectorClock | `core/src/main/java/org/mpi_sws/jmc/util/LamportVectorClock.java` | Research (1) | ⬜ Not started | Core distributed systems primitive; high priority |
| 2 | PartialOrder/TotalOrder | `core/src/main/java/org/mpi_sws/jmc/util/PartialOrder.java`, `TotalOrder.java` | Research (1) | ⬜ Not started | Relation abstraction used throughout JMC |
| 3 | TaskManager & Scheduler | `core/src/main/java/org/mpi_sws/jmc/runtime/TaskManager.java`, `Scheduler.java` | Research (1) | ⬜ Not started | Lower priority; requires stateful reasoning |

## Phase Definitions

- **Phase 1 (Research)**: Target identified, initial specification drafted
- **Phase 2 (Informal Spec)**: `specs/<name>_informal.md` written with pre/post/invariants
- **Phase 3 (Lean Spec)**: `lean/FVSquad/<Name>.lean` created with type definitions and proposition statements (`sorry` proofs)
- **Phase 4 (Lean Impl)**: Lean implementations written for target functions
- **Phase 5 (Proofs)**: Core theorems proved (may contain remaining `sorry`s)
- **Phase 6 (Validation)**: Correspondence tests written and passing

## Key Metrics

- **Total Targets**: 3
- **Active Targets**: 1 (LamportVectorClock)
- **Targets in Lean**: 0
- **Theorems Proved**: 0
- **`sorry` Count**: N/A

## How to Update This File

When a target advances to the next phase:
1. Update the **Phase** column to the next phase number
2. Update the **Status** column (⬜ not started, 🔄 in progress, ✅ done)
3. Add notes about any blockers or key insights
4. Create or update the corresponding artifact (spec file, Lean file, test harness)

---

**Last Updated**: 2026-04-25 03:43 UTC
