package org.mpi_sws.jmc.solver

import org.mpi_sws.jmc.solver.incremental.IncrementalSolver

object SymbolicSolverSingletonFactory {
    private var solver: SymbolicSolver? = null

    fun getSolver(solverType: SMTSolverTypes?): SymbolicSolver {
        if (solver != null) {
            return solver!!
        }

        if (solverType == null) {
            solver = IncrementalSolver()
        } else {
            solver = IncrementalSolver(solverType)
        }
        return solver
    }

    fun getIncrementalSolver(solverType: SMTSolverTypes?): IncrementalSolver {
        if (solver != null) {
            if (solver is IncrementalSolver) {
                return solver
            }
            throw IllegalStateException("Solver singleton is not an IncrementalSolver")
        }

        if (solverType == null) {
            solver = IncrementalSolver()
        } else {
            solver = IncrementalSolver(solverType)
        }
        return solver as IncrementalSolver
    }
}
