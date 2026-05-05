package org.mpi_sws.jmc.strategies.estimation

import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.trust.Event

interface MetaGraphEstimator {
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    fun updateEvent(events: List<Event>, activeTasks: MutableSet<Long?>)

    val expectedValue: Float

    fun reset()
}
