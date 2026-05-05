package org.mpi_sws.jmc.api.util.concurrent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

/**
 * This class is a wrapper around the Java Thread class - [java.lang.Thread]. It is used to
 * intercept the start, finish, and interrupt events of a thread.
 *
 *
 * The goal is to replace all references to Thread with JmcThread in bytecode instrumentation.
 *
 *
 * The method to be overridden is now run1 and similarly the method to join is join1.
 */
open class JmcThread : Thread {
    /**
     * Returns the task ID of this thread.
     *
     * @return The task ID of this thread.
     */
    val taskId: Long?
    private val createdBy: Long?

    // Private constructor to handle wrapping existing threads
    // without initializing a new JMC task in the runtime.
    // Using any JmcThread method will break if initialize is false.
    private constructor(r: Runnable, initialize: Boolean) : super(r) {
        if (initialize) {
            this.taskId = JmcRuntime.addNewTask()
            this.createdBy = JmcRuntime.currentTask()
            super.setUncaughtExceptionHandler { t: Thread, e: Throwable ->
                this.handleInterrupt(
                    t,
                    e
                )
            }
        } else {
            this.taskId = null
            this.createdBy = null
        }
    }

    /**
     * Constructs a new JmcThread object with the given JMC thread ID.
     */
    // TODO: extend to all constructors of Thread and handle ThreadGroups, also all join methods
    //      Should be a drop in replacement for all possible ways to use Threads
    /**
     * Constructs a new JmcThread object.
     */
    @JvmOverloads
    constructor(jmcThreadId: Long? = JmcRuntime.addNewTask()) : super() {
        this.taskId = jmcThreadId
        this.createdBy = JmcRuntime.currentTask()
        super.setUncaughtExceptionHandler { t: Thread, e: Throwable ->
            this.handleInterrupt(
                t,
                e
            )
        }
        LOGGER = LogManager.getLogger(JmcThread::class.java.name + " Task=" + jmcThreadId)
    }

    /**
     * Constructs a new JmcThread object with the given Runnable and JMC thread ID.
     */
    /**
     * Constructs a new JmcThread object with the given Runnable.
     */
    @JvmOverloads
    constructor(r: Runnable?, jmcThreadId: Long? = JmcRuntime.addNewTask()) : super(r) {
        this.taskId = jmcThreadId
        this.createdBy = JmcRuntime.currentTask()
        super.setUncaughtExceptionHandler { t: Thread, e: Throwable ->
            this.handleInterrupt(
                t,
                e
            )
        }
    }

    override fun run() {
        var event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.START_EVENT)
                .taskId(taskId)
                .param("startedBy", createdBy)
                .build()
        try {
            JmcRuntime.updateEvent(event)
        } catch (e: HaltTaskException) {
            LOGGER.error("Failed to start task: {}", e.message)
        }
        try {
            JmcRuntime.yield(taskId)
            run1()
        } catch (e: Exception) {
            if (e is HaltExecutionException && e.isReexecutionNeeded) {
                LOGGER.debug("Re-execution needed, throwing HaltExecutionException")
            } else if (e is HaltTaskException && e.isBlocked) {
                LOGGER.debug("Blocked task execution, throwing HaltTaskException")
            } else {
                LOGGER.error("Exception running the thread: {}", e.message)
            }
        } finally {
            event =
                JmcRuntimeEvent.Builder()
                    .type(JmcRuntimeEvent.Type.FINISH_EVENT)
                    .taskId(taskId)
                    .build()
            try {
                JmcRuntime.updateEvent(event)
            } catch (e: HaltTaskException) {
                LOGGER.error("Failed to finish task : {}", e.message)
            }
            JmcRuntime.join(taskId)
        }
    }

    /**
     * Used to run just the function in a wrapped thread and not as a separate thred.
     *
     *
     * Used internally by the Executor service that will invoke threads in a larger thread
     * context.
     */
    fun runWithoutJoin() {
        var event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.START_EVENT)
                .taskId(taskId)
                .param("startedBy", createdBy)
                .build()
        try {
            JmcRuntime.updateEvent(event)
        } catch (e: HaltTaskException) {
            LOGGER.error("Failed to start task: {}", e.message)
        }
        try {
            JmcRuntime.yield(taskId)
            run1()
        } catch (e: HaltTaskException) {
            event =
                JmcRuntimeEvent.Builder()
                    .type(JmcRuntimeEvent.Type.HALT_EVENT)
                    .taskId(taskId)
                    .build()
            try {
                JmcRuntime.updateEvent(event)
            } catch (ex: HaltTaskException) {
                LOGGER.error("Failed to halt task (runWithoutJoin) : {}", ex.message)
            }
        } finally {
            event =
                JmcRuntimeEvent.Builder()
                    .type(JmcRuntimeEvent.Type.FINISH_EVENT)
                    .taskId(taskId)
                    .build()
            try {
                JmcRuntime.updateEvent(event)
            } catch (e: HaltTaskException) {
                LOGGER.error("Failed to finish task (runWithoutJoin) : {}", e.message)
            }
        }
    }

    override fun start() {
        val taskId = JmcRuntime.currentTask()
        JmcRuntime.pause(taskId)
        super.start()
        JmcRuntime.wait<Any>(taskId)
    }

    /**
     * This method is overridden by the user.
     */
    @Throws(HaltTaskException::class)
    open fun run1() {
        super.run()
    }

    private fun handleInterrupt(t: Thread, e: Throwable) {
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.HALT_EVENT)
                .taskId(taskId)
                .build()
        try {
            JmcRuntime.updateEvent(event)
        } catch (ex: HaltTaskException) {
            LOGGER.error("Failed to halt task on interrupt : {}", ex.message)
        }
        LOGGER.info("thread {} interrupted with exception: {}", t.name, e.message)
    }

    /**
     * Replacing the Thread join to intercept the join Event.
     */
    /**
     * Replacing the thread join to intercept the join Event
     *
     * @throws InterruptedException when the underlying join call fails
     */
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun join1(millis: Long = 0L) {
        val requestingTask = JmcRuntime.currentTask()
        val requestEvent =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT)
                .taskId(requestingTask)
                .param("waitingTask", taskId)
                .build()
        try {
            JmcRuntime.updateEventAndYield<Any>(requestEvent)
        } catch (e: HaltTaskException) {
            LOGGER.error("Failed to join task : {}", e.message)
        }
        super.join(millis)
        val completedEvent =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT)
                .taskId(requestingTask)
                .param("joinedTask", taskId)
                .build()
        try {
            JmcRuntime.updateEventAndYield<Any>(completedEvent)
        } catch (e: HaltTaskException) {
            LOGGER.error("Failed to complete join task : {}", e.message)
        }
    }

    /**
     * Returns a string representation of this thread, including the
     * thread's name, priority, and thread group.
     *
     * @return a string representation of this thread.
     */
    override fun toString(): String {
        return "JmcThread-" + taskId
    }

    companion object {
        private var LOGGER: Logger = LogManager.getLogger(JmcThread::class.java)

        fun currentThread(): JmcThread {
            val t = Thread.currentThread()
            return if (t is JmcThread) {
                t
            } else {
                JmcThread(t, false)
            }
        }
    }
}
