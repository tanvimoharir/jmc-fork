package org.mpi_sws.jmc.solver

import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver
import org.sosy_lab.java_smt.api.BooleanFormulaManager
import org.sosy_lab.java_smt.api.IntegerFormulaManager

object SolverUtil {
    val solver: SymbolicSolver
        get() = SymbolicSolverSingletonFactory.getSolver(null)

    fun getSolver(solverType: SMTSolverTypes?): SymbolicSolver {
        return SymbolicSolverSingletonFactory.getSolver(solverType)
    }

    val incrementalSolver: IncrementalSolver
        get() = SymbolicSolverSingletonFactory.getIncrementalSolver(null)

    fun getIncrementalSolver(solverType: SMTSolverTypes?): IncrementalSolver {
        return SymbolicSolverSingletonFactory.getIncrementalSolver(solverType)
    }

    val bmgr: BooleanFormulaManager?
        get() {
            val solver = solver
            return solver.getBmgr()
        }

    val imgr: IntegerFormulaManager?
        get() {
            val solver = solver
            return solver.getImgr()
        }

    fun getSymBoolVariable(name: String?): SymBoolVariable? {
        val solver = solver
        return solver.getSymBoolVariable(name!!)
    }

    fun getSymIntVariable(name: String): SymIntVariable? {
        val solver = solver
        return solver.getSymIntVariable(name)
    }

    fun getSymIntVarValue(name: String): Int {
        val solver = solver
        return solver.getSymIntVarValue(name)
    }

    fun getSymBoolVarValue(name: String?): Boolean {
        val solver = solver
        return solver.getSymBoolVarValue(name)
    }
}
