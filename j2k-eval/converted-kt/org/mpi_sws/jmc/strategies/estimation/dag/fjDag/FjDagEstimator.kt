package org.mpi_sws.jmc.strategies.estimation.dag.fjDag

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator
import org.mpi_sws.jmc.strategies.trust.Event
import org.mpi_sws.jmc.strategies.trust.EventUtils
import org.mpi_sws.jmc.strategies.trust.ExecutionGraphSimulator

class FjDagEstimator : MetaGraphEstimator {
    protected val executionGraph: ExecutionGraphSimulator = ExecutionGraphSimulator()

    /**
     * @return
     */
    override var expectedValue: Float = 1f
        protected set

    var isForkComplete: Boolean = false
        private set

    /**
     * @param events
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(events: List<Event>, activeTasks: MutableSet<Long?>) {
        if (!events.isEmpty() && activeTasks.size != 0) {
            // The lock acquisition and release events, will be compiled into a pair of ReadEx and WriteEx events
            for (e in events) {
                LOGGER.debug("Received event: {}", e)
                executionGraph.updateEvent(e)
            }

            // Update the estimation based on the last event
            val e = events[events.size - 1]

            if (!isForkComplete) {
                if (EventUtils.isJoinRequest(e) && e.taskId == 0L) {
                    // The main task finished forking and is now starting to join
                    isForkComplete = true
                }
                // Since the main task is still forking, we do not update the estimation
                return
            }
            if (e.taskId == 0L) {
                // We do not update the estimation based on the main task events
                return
            }
            updateEstimation(e, activeTasks)
        }
    }

    protected fun updateEstimation(e: Event, activeTasks: MutableSet<Long?>) {
        if (EventUtils.isThreadStart(e)) {
            // If the event is a thread finish event or a thread start event, we do not consider it in the estimation
            return
        }
        // If the main task is still active, we do not consider it in the estimation
        activeTasks.remove(1L)
        if (activeTasks.size > 0) {
            var `in` = 1
            val out = activeTasks.size
            val poMax = executionGraph.allNonNoopPoMaxEvents
            for (poMaxEvent in poMax) {
                if (poMaxEvent.taskId != 0L && poMaxEvent.taskId !== e.taskId &&
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
    }

    protected fun isScMax(e: Event): Boolean {
        return executionGraph.isCoMax(e) &&
                executionGraph.isRfMax(e) &&
                executionGraph.isFrMax(e) &&
                executionGraph.isTcMax(e) &&
                executionGraph.isStMax(e) &&
                executionGraph.isJtMax(e)
    }

    protected fun conflict(e1: Event, e2: Event): Boolean {
        return if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            false
        } else { // One of the two events is a write event
            e1.location == e2.location
        }
    }

    /**
     *
     */
    override fun reset() {
        isForkComplete = false
        expectedValue = 1f
        executionGraph.reset()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            FjDagEstimator::class.java
        )
    }
}
