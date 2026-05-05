package org.mpi_sws.jmc.solver

class SolverResult(private val result: Boolean, val isNegatable: Boolean) {
    fun result(): Boolean {
        return result
    }
}
