package org.mpi_sws.jmc.strategies.estimation

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.strategies.trust.*

interface EstimationStrategy {
    fun compileRuntimeEvent(event: JmcRuntimeEvent): List<Event?> {
        val events = EventFactory.fromRuntimeEvent(event)
        if (event.type == JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT) {
            val e =
                Event(
                    event.taskId - 1,
                    LocationStore.Companion.ThreadLocation,
                    Event.Type.NOOP
                )
            e.setAttribute("join-req", true)
            events.add(e)
        }
        return events
    }
}
