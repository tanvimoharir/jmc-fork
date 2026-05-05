package org.mpi_sws.jmc.strategies.tracker

import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

/** Tracks the active tasks based on events.  */
interface Tracker {
    /**
     * Updates the event.
     *
     * @param event the event to update
     * @return the set of active tasks
     */
    @Throws(HaltCheckerException::class)
    fun updateEvent(event: JmcRuntimeEvent): Set<Long?>

    /** Resets the tracker.  */
    fun reset()
}
