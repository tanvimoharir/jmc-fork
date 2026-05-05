package org.mpi_sws.jmc.strategies.tracker

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.strategies.SchedulingStrategy

/**
 * A strategy that tracks the active tasks.
 */
abstract class TrackActiveTasksStrategy : SchedulingStrategy {
    private val allTasks: MutableSet<Long?>
    private val activeTasks: MutableSet<Long?>
    private val tasksLock = Any()

    private val trackers: List<Tracker>

    /**
     * Constructs a new TrackActiveTasksStrategy object.
     */
    constructor() {
        this.allTasks = HashSet()
        this.activeTasks = HashSet()
        this.trackers = java.util.List.of(
            TrackTasks(),
            TrackWaitNotify(),
            TrackStaticInit(),
            TrackExecutors()
        )
    }

    /**
     * Constructs a new TrackActiveTasksStrategy object with the given trackers.
     */
    constructor(trackers: List<Tracker>) {
        this.allTasks = HashSet()
        this.activeTasks = HashSet()
        this.trackers = trackers
    }

    override fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
    }

    override fun updateEvent(event: JmcRuntimeEvent) {
        val localActiveTasks: MutableSet<Long?>
        synchronized(tasksLock) {
            allTasks.add(event.taskId)
            localActiveTasks = HashSet(this.allTasks)
        }
        for (tracker in trackers) {
            localActiveTasks.retainAll(tracker.updateEvent(event))
        }

        LOGGER.debug("Active tasks: {}", HashSet(localActiveTasks))
        synchronized(tasksLock) {
            activeTasks.clear()
            activeTasks.addAll(localActiveTasks)
        }
    }

    private fun clear() {
        synchronized(tasksLock) {
            activeTasks.clear()
            allTasks.clear()
        }
        for (tracker in trackers) {
            tracker.reset()
        }
    }

    override fun resetIteration(iteration: Int) {
        clear()
    }

    override fun teardown(report: JmcModelCheckerReport) {
        clear()
    }

    /**
     * Returns whether the given thread is active.
     *
     * @param threadId the thread ID
     * @return whether the given thread is active
     */
    protected fun isActive(threadId: Long?): Boolean {
        synchronized(tasksLock) {
            return activeTasks.contains(threadId)
        }
    }

    /**
     * Returns the set of active tasks.
     *
     * @return the set of active tasks
     */
    protected fun getActiveTasks(): Set<Long?> {
        synchronized(tasksLock) {
            return HashSet(activeTasks)
        }
    }

    /**
     * Marks the given thread as active.
     *
     * @param threadId the thread ID
     */
    protected fun markActive(threadId: Long?) {
        synchronized(tasksLock) {
            activeTasks.add(threadId)
        }
    }

    /**
     * Marks the given thread as inactive.
     *
     * @param threadId the thread ID
     */
    protected fun markInactive(threadId: Long?) {
        synchronized(tasksLock) {
            activeTasks.remove(threadId)
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            TrackActiveTasksStrategy::class.java
        )
    }
}
