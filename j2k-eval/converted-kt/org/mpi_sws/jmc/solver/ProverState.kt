package org.mpi_sws.jmc.solver

import org.mpi_sws.jmc.api.symbolic.array.SymArrayVariable
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable
import org.sosy_lab.java_smt.api.ProverEnvironment

class ProverState(var prover: ProverEnvironment) {
    var symIntVariableMap: MutableMap<String?, SymIntVariable?> = HashMap()
    var symBoolVariableMap: MutableMap<String?, SymBoolVariable?> = HashMap()
    var symArrayVariableHashMap: MutableMap<String?, SymArrayVariable?> = HashMap()

    fun clear() {
        symIntVariableMap.clear()
        symBoolVariableMap.clear()
        symArrayVariableHashMap.clear()
    }
}
