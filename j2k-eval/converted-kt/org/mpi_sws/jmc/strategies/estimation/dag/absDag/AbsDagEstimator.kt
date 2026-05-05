package org.mpi_sws.jmc.strategies.estimation.dag.absDag

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.estimation.dag.DagEstimator
import org.mpi_sws.jmc.strategies.trust.Event
import org.mpi_sws.jmc.strategies.trust.EventUtils

class AbsDagEstimator : DagEstimator() {
    /**
     * @param events
     * @param activeTasks
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(events: List<Event>, activeTasks: MutableSet<Long?>) {
        if (!events.isEmpty() && activeTasks.size != 0) {
            for (e in events) {
                LOGGER.debug("Received event: {}", e)
                if (EventUtils.isThreadFinish(e) || EventUtils.isThreadJoin(e) || EventUtils.isJoinRequest(e)) {
                    return
                }
                executionGraph.updateEvent(e)
            }
            val e = events[events.size - 1]
            if (EventUtils.isNoop(e)) {
                return
            }
            if (activeTasks.size - 1 > 0) {
                updateEstimation(e, activeTasks)
            }
        }
    }

    /**
     * @param e
     * @param activeTasks
     */
    override fun updateEstimation(e: Event, activeTasks: Set<Long?>) {
        var `in` = 1
        val out = activeTasks.size - 1
        val poMax = executionGraph.allPoMaxEvents
        for (poMaxEvent in poMax) {
            if (!EventUtils.isNoop(poMaxEvent) && poMaxEvent.taskId !== e.taskId &&
                isScMax(poMaxEvent)
            ) {
                if (!conflict(poMaxEvent, e)) {
                    `in`++
                }
            }
        }
        expectedValue = expectedValue * out / `in`
        LOGGER.debug("Expected value: {}", expectedValue)
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            AbsDagEstimator::class.java
        )
    }
}
