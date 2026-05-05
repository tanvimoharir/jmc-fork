package org.mpi_sws.jmc.api.util.concurrent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.util.concurrent.JmcFuture
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import java.util.concurrent.*

/**
 * A future that runs a callable function in a new thread.
 *
 * @param <T> The return type of the callable function.
</T> */
class JmcFuture<T> : RunnableFuture<T?> {
    private val future: CompletableFuture<T?>
    val taskId: Long?
    private val thread: JmcThread

    //2 writw events 1. result
    constructor(function: Callable<T>, taskId: Long?) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = taskId
        this.thread =
            JmcThread(
                Runnable {
                    try {
                        set(function.call())
                        return@Runnable
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                },
                taskId
            )
    }

    constructor(runnable: Runnable, taskId: Long?) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = taskId
        this.thread =
            JmcThread(
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

    constructor(runnable: Runnable, result: T, taskId: Long?) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = taskId
        this.thread =
            JmcThread(
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

    constructor(thread: JmcThread, result: T) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = thread.taskId
        this.thread =
            JmcThread(
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

    constructor(thread: JmcThread) {
        this.future = CompletableFuture()
        JmcRuntimeUtils.writeEventWithoutYield(
            this.future,
            false, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        JmcRuntime.yield<Any>()
        this.taskId = thread.taskId
        this.thread = thread
    }

    /**
     * Cancel the future.
     *
     *
     * Currently unsupported by Jmc. Cannot stop tasks yet.
     *
     * @param b Whether to interrupt the future.
     * @return Whether the future was successfully cancelled.
     */
    override fun cancel(b: Boolean): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val cancelled = future.isCancelled
        JmcRuntime.yield<Any>()
        return cancelled
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
        LOGGER.debug("Waiting on future: {}", thread.taskId)
        thread.join1(0L)
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val result = future.get()
        JmcRuntime.yield<Any>()
        return result
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun get(l: Long, timeUnit: TimeUnit): T? {
        val waitTime = timeUnit.toMillis(l)
        thread.join1(waitTime)
        LOGGER.debug("Waiting on future {} with timeout: {}ms", thread.taskId, waitTime)
        // Currently we do not support timeouts, therefore the timeout here is ignored
        JmcRuntimeUtils.readEventWithoutYield(
            this.future, "java/util/concurrent/CompletableFuture", "result", "Z"
        )
        val result = future[l, timeUnit]
        JmcRuntime.yield<Any>()
        return result
    }

    private fun set(value: T?) {
        future.complete(value)
    }

    /** Run the underlying callable function in a new thread.  */
    override fun run() {
        LOGGER.debug("Starting future: {}", thread.taskId)
        thread.runWithoutJoin()
    }

    companion object {
        // TODO: Add support for cancellation and timeouts.
        private val LOGGER: Logger = LogManager.getLogger(JmcFuture::class.java)
    }
}
