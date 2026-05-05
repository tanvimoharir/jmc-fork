package org.mpi_sws.jmc.strategies.estimation

import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.trust.Algo

interface MetaTreeEstimator {
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    fun updateTree(alg: Algo)

    fun resetReExecutionFlag()

    val isReExecutionNeeded: Boolean

    val expectedValue: Int

    fun reset()
}
