package org.mpi_sws.jmc.strategies

import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.runtime.*
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice

/**
 * The scheduling strategy is responsible for deciding which thread to schedule next.
 *
 *
 * It is used by the [Scheduler] to decide which thread to schedule next. The [ ] is in turn used by the [JmcRuntime] to manage the execution of threads.
 *
 *
 * Implementations of this interface should be thread-safe. Multiple threads can make concurrent
 * calls to the [SchedulingStrategy.updateEvent] function.
 */
interface SchedulingStrategy {
    /**
     * Initializes the strategy for a new iteration.
     *
     * @param iteration the number of the iteration.
     */
    @Throws(HaltCheckerException::class)
    fun initIteration(iteration: Int, report: JmcModelCheckerReport)

    /**
     * Updates the strategy with the event that has occurred.
     *
     *
     * May be left empty if unused
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    fun updateEvent(event: JmcRuntimeEvent)

    /**
     * Returns the ID of the next thread to be scheduled.
     *
     * @return the ID of the next thread to be scheduled.
     */
    fun nextTask(): SchedulingChoice<*>?

    /**
     * Resets the strategy for the current Iteration.
     */
    fun resetIteration(iteration: Int)

    /**
     * Teardown the strategy. Allows for releasing resources.
     */
    fun teardown(report: JmcModelCheckerReport)
}
