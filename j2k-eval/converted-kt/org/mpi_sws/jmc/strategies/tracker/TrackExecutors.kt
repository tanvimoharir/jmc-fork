// Create new file: jmc/core/src/main/java/org/mpi_sws/jmc/strategies/tracker/TrackExecutors.java
package org.mpi_sws.jmc.strategies.tracker

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.util.concurrent.JmcExecutorService
import org.mpi_sws.jmc.api.util.concurrent.JmcScheduledExecutorService
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/**
 * Tracks ExecutorService instances to ensure proper shutdown and prevent memory leaks.
 * Prioritizes tasks related to executor shutdown when executors are not properly closed.
 */
class TrackExecutors : Tracker {
    /**
     * Set of registered executors that need tracking.
     */
    private val registeredExecutors: MutableSet<ExecutorService?> =
        ConcurrentHashMap.newKeySet()


    /**
     * All active tasks.
     */
    private val activeTasks: MutableSet<Long?> =
        ConcurrentHashMap.newKeySet()

    override fun updateEvent(event: JmcRuntimeEvent): Set<Long?> {
        val taskId = event.taskId ?: return getActiveTasks()

        activeTasks.add(taskId)

        val type = event.type

        if (type == JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT) {
            handleExecutorEvent(event)
        }


        return getActiveTasks()
    }

    private fun handleExecutorEvent(event: JmcRuntimeEvent) {
        val action = event.getParam<String>("action")
        val executor = event.getParam<ExecutorService>("executor")

        if ("register" == action) {
            registeredExecutors.add(executor)
            LOGGER.debug("Registered executor: {}", executor)
        }
    }

    /**
     * Checks all registered executors and shuts down any that are not already shutdown.
     * This should be called at the end of each iteration.
     */
    fun shutdownExecutors() {
        for (executor in registeredExecutors) {
            if (executor is JmcExecutorService) {
                if (!executor.isShutdown()) {
                    LOGGER.debug("Executor not shutdown, forcing shutdown: {}", executor)
                    executor.shutdown()
                }
            }
            if (executor is JmcScheduledExecutorService) {
                if (!executor.isShutdown()) {
                    LOGGER.debug("Scheduled Executor not shutdown, forcing shutdown: {}", executor)
                    executor.shutdown()
                }
            }
        }
    }

    private fun getActiveTasks(): Set<Long?> {
        return HashSet(activeTasks)
    }

    override fun reset() {
        // Shutdown any executors that weren't properly shutdown
        shutdownExecutors()

        activeTasks.clear()
        registeredExecutors.clear()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            TrackExecutors::class.java
        )
    }
}
