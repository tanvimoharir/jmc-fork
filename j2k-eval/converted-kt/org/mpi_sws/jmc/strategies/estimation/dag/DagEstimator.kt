package org.mpi_sws.jmc.strategies.estimation.dag

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator
import org.mpi_sws.jmc.strategies.trust.Event
import org.mpi_sws.jmc.strategies.trust.EventUtils
import org.mpi_sws.jmc.strategies.trust.ExecutionGraphSimulator

open class DagEstimator : MetaGraphEstimator {
    protected val executionGraph: ExecutionGraphSimulator = ExecutionGraphSimulator()

    override var expectedValue: Float = 1f
        protected set

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
            updateEstimation(e, activeTasks)
        }
    }

    protected open fun updateEstimation(e: Event, activeTasks: Set<Long?>) {
        var `in` = 1
        val out = activeTasks.size
        val poMax = executionGraph.allPoMaxEvents
        for (poMaxEvent in poMax) {
            if (poMaxEvent.taskId !== e.taskId && isScMax(poMaxEvent)) {
                if (!conflict(poMaxEvent, e)) {
                    `in`++
                }
            }
        }

        expectedValue = expectedValue * out / `in`
        LOGGER.debug("Expected value: {}", expectedValue)
    }

    // The given event to this method is already a PoMax event. This method will check if the event is a SCMax event.
    // A SCMax event is a PO + RF + FR + CO + ST + TC + JT max event.
    protected fun isScMax(e: Event): Boolean {
        return executionGraph.isCoMax(e) &&
                executionGraph.isRfMax(e) &&
                executionGraph.isFrMax(e) &&
                executionGraph.isTcMax(e) &&
                executionGraph.isStMax(e) &&
                executionGraph.isJtMax(e)
    }

    protected fun conflict(e1: Event, e2: Event): Boolean {
        if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            if (EventUtils.isThreadStart(e1)) {
                val startedBy = EventUtils.getStartedBy(e1)!!
                // We need to check if the START event is PO-MAX regarding the PO-MAX of the starter thread
                return startedBy == e2.taskId || !executionGraph.isStartMaxWithStarter(e1)
            }
            /*if (EventUtils.isThreadFinish(e2)) {
                long tid = e2.getTaskId();
                // get the tid of the thread which started the e2's thread
                long startedBy = executionGraph.getStarterTid(tid);
                LOGGER.debug("Started by: {}", startedBy);
                Event lastEventOfStartedBy = executionGraph.getLastEventOfTask(startedBy);
                return EventUtils.isJoinRequest(lastEventOfStartedBy);
            }*/
        } else { // One of the two events is a write event
            return e1.location == e2.location
        }

        // No conflict found
        return false
    }

    override fun reset() {
        expectedValue = 1f
        executionGraph.reset()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(DagEstimator::class.java)
    }
}
