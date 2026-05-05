package org.mpi_sws.jmc.api.symbolic

import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import org.mpi_sws.jmc.solver.SolverUtil

class SymbolicFormula {
    fun evaluate(operation: JmcBooleanFormula?): Boolean {
        return JmcRuntimeUtils.SymEvent(operation)
    }

    fun evaluate(symBool: SymbolicBoolean): Boolean {
        if (symBool.eval != null) {
            return evaluate(symBool.eval)
        } else {
            val symVar = SolverUtil.getSymBoolVariable(symBool.name)
            val formula = symVar.getVar()
            val operation = JmcBooleanFormula()
            operation.formula = formula
            operation.addBooleanVariable(symBool.name, formula)
            return evaluate(operation)
        }
    }
}
