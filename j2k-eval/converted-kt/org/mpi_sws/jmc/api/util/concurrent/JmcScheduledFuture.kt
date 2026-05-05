package org.mpi_sws.jmc.api.util.concurrent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.util.concurrent.JmcScheduledFuture
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import java.util.concurrent.*
import kotlin.concurrent.Volatile

/**
 * A scheduled future that runs a callable or runnable function in a new thread.
 * Implements [RunnableScheduledFuture] for JMC model checking.
 *
 *
 * In JMC's controlled execution, delays are not modeled - all scheduled tasks
 * execute immediately. The delay information is stored but not used for actual timing.
 *
 * @param <T> The return type of the callable function.
</T> */
class JmcScheduledFuture<T> : RunnableScheduledFuture<T?> {
    private val future: CompletableFuture<T?>
    val taskId: Long?
    private val thread: JmcThread
    private val delay: Long // Stored but not used in JMC
    private val unit: TimeUnit // Stored but not used in JMC

    @Volatile
    private var cancelled = false

    @Volatile
    private var periodic = false

    /**
     * Creates a scheduled future from a Callable with delay information.
     */
    /**
     * Creates a scheduled future from a Callable with a new task ID.
     */
    @JvmOverloads
    constructor(
        function: Callable<T>,
        taskId: Long?,
        delay: Long = 0,
        unit: TimeUnit = TimeUnit.NANOSECONDS,
        periodic: Boolean = false
    ) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = taskId
        this.delay = delay
        this.unit = unit
        this.periodic = periodic
        this.thread = JmcThread(
            {
                try {
                    set(function.call())
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            },
            taskId
        )
    }

    /**
     * Creates a scheduled future from a Runnable with delay information.
     */
    /**
     * Creates a scheduled future from a Runnable with a new task ID.
     */
    @JvmOverloads
    constructor(
        runnable: Runnable,
        taskId: Long?,
        delay: Long = 0,
        unit: TimeUnit = TimeUnit.NANOSECONDS,
        periodic: Boolean = false
    ) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = taskId
        this.delay = delay
        this.unit = unit
        this.periodic = periodic
        this.thread = JmcThread(
            {
                try {
                    runnable.run()
                    set(null)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            },
            taskId
        )
    }

    /**
     * Creates a scheduled future from a Runnable with a result value and delay information.
     */
    /**
     * Creates a scheduled future from a Runnable with a result value.
     */
    @JvmOverloads
    constructor(
        runnable: Runnable,
        result: T,
        taskId: Long?,
        delay: Long = 0,
        unit: TimeUnit = TimeUnit.NANOSECONDS,
        periodic: Boolean = false
    ) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = taskId
        this.delay = delay
        this.unit = unit
        this.periodic = periodic
        this.thread = JmcThread(
            {
                try {
                    runnable.run()
                    set(result)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            },
            taskId
        )
    }

    /**
     * Creates a scheduled future from an existing JmcThread with a result value and delay information.
     */
    /**
     * Creates a scheduled future from an existing JmcThread with a result value.
     */
    @JvmOverloads
    constructor(
        thread: JmcThread,
        result: T,
        delay: Long = 0,
        unit: TimeUnit = TimeUnit.NANOSECONDS,
        periodic: Boolean = false
    ) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = thread.taskId
        this.delay = delay
        this.unit = unit
        this.periodic = periodic
        this.thread = JmcThread(
            {
                try {
                    thread.run1()
                    set(result)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            },
            taskId
        )
    }

    /**
     * Creates a scheduled future from an existing JmcThread with delay information.
     */
    /**
     * Creates a scheduled future from an existing JmcThread.
     */
    @JvmOverloads
    constructor(thread: JmcThread, delay: Long = 0, unit: TimeUnit = TimeUnit.NANOSECONDS, periodic: Boolean = false) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = thread.taskId
        this.delay = delay
        this.unit = unit
        this.periodic = periodic
        this.thread = thread
    }

    /**
     * Returns the remaining delay. In JMC, always returns 0 since delays are not modeled.
     */
    override fun getDelay(unit: TimeUnit): Long {
        // In JMC, delays are not modeled, so always return 0
        return delay
    }

    /**
     * Compares this scheduled future with another delayed object.
     * In JMC, all delays are 0, so comparison is based on task ID.
     */
    override fun compareTo(other: Delayed): Int {
        if (other === this) {
            return 0
        }
        // In JMC, all delays are 0, so we compare by taskId for determinism
        if (other is JmcScheduledFuture<*>) {
            return java.lang.Long.compare(taskId!!, other.taskId!!)
        }
        // Fall back to delay comparison
        val diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS)
        return if (diff < 0) -1 else if (diff > 0) 1 else 0
    }

    /**
     * Returns whether this is a periodic task.
     * In JMC, periodic tasks are executed once, so this is informational only.
     */
    override fun isPeriodic(): Boolean {
        return periodic
    }

    /**
     * Cancel the future.
     * Currently, cancellation is limited in JMC - cannot stop running tasks.
     */
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (isDone) {
            return false
        }
        cancelled = true
        future.cancel(mayInterruptIfRunning)
        return true
    }

    override fun isCancelled(): Boolean {
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val isCancelled = future.isCancelled || cancelled
        JmcRuntime.yield<Any>()
        return isCancelled
    }

    override fun isDone(): Boolean {
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val done = future.isDone
        JmcRuntime.yield<Any>()
        return done
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): T? {
        LOGGER.debug("Waiting on scheduled future: {}", thread.taskId)
        thread.join1(0L)
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val result = future.get()
        JmcRuntime.yield<Any>()
        return result
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun get(timeout: Long, unit: TimeUnit): T? {
        val waitTime = unit.toMillis(timeout)
        thread.join1(waitTime)
        LOGGER.debug("Waiting on scheduled future {} with timeout: {}ms", thread.taskId, waitTime)
        // Currently we do not support timeouts, therefore the timeout here is ignored
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val result = future[timeout, unit]
        JmcRuntime.yield<Any>()
        return result
    }

    private fun set(value: T?) {
        future.complete(value)
    }

    /**
     * Run the underlying callable/runnable function in a new thread.
     * This is called by the worker thread.
     */
    override fun run() {
        if (cancelled) {
            return
        }
        LOGGER.debug("Starting scheduled future: {}", thread.taskId)
        thread.runWithoutJoin()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcScheduledFuture::class.java
        )
    }
}
