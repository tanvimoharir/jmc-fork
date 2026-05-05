package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

/**
 * The LockSupport class is the replacement for [java.util.concurrent.locks.LockSupport]
 * class.
 */
object JmcLockSupport {
    /**
     * Park the current thread.
     *
     *
     * This method calls the parkOperation method of the RuntimeEnvironment class to park the
     * current thread.
     */
    fun park() {
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.PARK_EVENT)
                .taskId(JmcRuntime.currentTask())
                .build()
        JmcRuntime.updateEventAndYield<Any>(event)
    }

    /**
     * Unpark the given thread.
     *
     *
     * This method calls the unparkOperation method of the RuntimeEnvironment class to unpark the
     * given thread.
     *
     * @param thread
     */
    fun unpark(thread: Thread?) {
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.UNPARK_EVENT)
                .taskId(JmcRuntime.currentTask())
                .build()
        JmcRuntime.updateEventAndYield<Any>(event)
    }
}
