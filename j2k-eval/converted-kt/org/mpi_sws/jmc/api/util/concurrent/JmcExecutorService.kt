package org.mpi_sws.jmc.api.util.concurrent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.util.concurrent.JmcExecutorService
import org.mpi_sws.jmc.api.util.concurrent.JmcExecutorService.JmcExecutorWorker
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * An executor service that runs tasks in new threads. It is a redefinition of [ ] for JMC model checking.
 *
 *
 * Currently, the executor service does not support stopping tasks.
 */
class JmcExecutorService : ThreadPoolExecutor {
    // Keeps track of how many current tasks are running.
    // Updated by the worker threads.
    private val counter: AtomicInteger
    private val capacity: Int
    private val queue: BlockingQueue<JmcFuture<*>>
    private val workers: MutableList<JmcExecutorWorker>
    private val isShutdown = AtomicBoolean(false)

    constructor(capacity: Int) : super(
        capacity,
        capacity,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(),
        JmcThreadFactory()
    ) {
        this.capacity = capacity
        this.counter = AtomicInteger(0)
        this.queue = LinkedBlockingQueue()
        this.workers = ArrayList()
        for (i in 0..<capacity) {
            val worker = JmcExecutorWorker(i, this.queue, this.counter)
            workers.add(worker)
            worker.start()
        }
        isShutdown.set(false)
        JmcRuntimeUtils.registerExecutor(this)
    }

    constructor(capacity: Int, threadFactory: ThreadFactory) : super(
        capacity,
        capacity,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(),
        threadFactory
    ) {
        this.capacity = capacity
        this.counter = AtomicInteger(0)
        this.queue = LinkedBlockingQueue()
        this.workers = ArrayList()
        for (i in 0..<capacity) {
            val worker = JmcExecutorWorker(i, this.queue, this.counter)
            workers.add(worker)
            worker.start()
        }
        isShutdown.set(false)
        JmcRuntimeUtils.registerExecutor(this)
    }

    /**
     * Added this constructor for when a class extends ThreadPoolExecutor
     */
    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        timeUnit: TimeUnit,
        receivedQueue: BlockingQueue<Runnable?>
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        timeUnit,
        receivedQueue
    ) {
        this.capacity = maximumPoolSize
        this.counter = AtomicInteger(0)
        this.queue = LinkedBlockingQueue()
        this.workers = ArrayList()
        for (i in 0..<capacity) {
            val worker = JmcExecutorWorker(i, this.queue, this.counter)
            workers.add(worker)
            worker.start()
        }
        isShutdown.set(false)
        JmcRuntimeUtils.registerExecutor(this)
    }

    /** Stops the executor service.  */
    /**
     * Stops the executor service.
     */
    override fun shutdown() {
        super.shutdown()
        for (worker in workers) {
            worker.shutdown()
            worker.interrupt()
        }
        isShutdown.set(true)

        for (worker in workers) {
            try {
                worker.join()
            } catch (e: InterruptedException) {
                LOGGER.error("Error while shutting down worker thread", e)
            }
        }
    }

    private fun addWork() {
        counter.incrementAndGet()
    }

    private fun removeWork() {
        counter.decrementAndGet()
    }

    /**
     * Stops the executor service. Currently not supported.
     */
    override fun shutdownNow(): List<Runnable> {
        // Currently not supported
        for (worker in workers) {
            worker.shutdown()
        }
        isShutdown.set(true)
        return ArrayList()
    }

    /**
     * Returns whether the executor service is shutdown.
     */
    override fun isShutdown(): Boolean {
        return isShutdown.get()
    }

    /**
     * Returns whether the executor service is terminated.
     */
    override fun isTerminated(): Boolean {
        return false
    }

    /**
     * Waits for the executor service to terminate.
     */
    @Throws(InterruptedException::class)
    override fun awaitTermination(l: Long, timeUnit: TimeUnit): Boolean {
        var allShutdown = true
        for (worker in workers) {
            try {
                worker.join()
            } catch (e: InterruptedException) {
                allShutdown = false
            }
        }
        return allShutdown
    }

    private fun offer(future: JmcFuture<*>) {
        if (counter.get() < capacity) {
            // If we know that the task will be immediately picked up,
            // We pause and wait for the matching yield
            val taskId = JmcRuntime.currentTask()
            JmcRuntime.pause(taskId)
            queue.offer(future)
            JmcRuntime.wait<Any>(taskId)
        } else {
            // Otherwise, all other actual JVM threads are blocked.
            // Hence, we just yield and allow one of them to continue
            queue.offer(future)
            JmcRuntime.yield<Any>()
        }
    }

    /**
     * Submits a callable task to the executor service.
     */
    override fun <T> submit(callable: Callable<T>): JmcFuture<T> {
        val future = JmcFuture(callable, JmcRuntime.addNewTask())
        offer(future)
        return future
    }

    override fun <T> submit(runnable: Runnable, t: T): JmcFuture<T> {
        var future: JmcFuture<T>? = null
        future = if (runnable is JmcThread) {
            JmcFuture(runnable, t)
        } else {
            // Otherwise, create a new JmcThread
            JmcFuture(runnable, t, JmcRuntime.addNewTask())
        }
        offer(future)
        return future
    }

    override fun submit(runnable: Runnable): JmcFuture<*> {
        var future: JmcFuture<*>? = null
        future = if (runnable is JmcThread) {
            // If the runnable is already a JmcThread, reuse the taskId
            JmcFuture<Any>(runnable)
        } else {
            // Otherwise, create a new JmcThread
            JmcFuture<Any>(runnable, JmcRuntime.addNewTask())
        }
        offer(future)
        return future
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(collection: Collection<Callable<T>>): List<Future<T>> {
        // Map each callable to a future and run them
        val futures: MutableList<Future<T>> = ArrayList()
        for (callable in collection) {
            val future = JmcFuture(callable, JmcRuntime.addNewTask())
            futures.add(future)
            offer(future)
        }
        return futures
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(
        collection: Collection<Callable<T>?>, l: Long, timeUnit: TimeUnit
    ): List<Future<T>> {
        return invokeAll(collection)
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun <T> invokeAny(collection: Collection<Callable<T>>): T {
        val futures: MutableList<JmcFuture<*>> = ArrayList()
        val allTasks: MutableSet<Long?> = HashSet()
        for (callable in collection) {
            val future = JmcFuture(callable, JmcRuntime.addNewTask())
            futures.add(future)
            allTasks.add(future.taskId)
            offer(future)
        }
        while (true) {
            val completedTasks: MutableSet<Long?> = HashSet()
            for (future in futures) {
                if (future.isDone) {
                    try {
                        return future.get()
                    } catch (e: InterruptedException) {
                        completedTasks.add(future.taskId)
                    }
                }
            }
            // Check if all tasks are completed
            if (completedTasks.size == allTasks.size) {
                break
            }
        }
        return null
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun <T> invokeAny(collection: Collection<Callable<T>?>, l: Long, timeUnit: TimeUnit): T {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        return invokeAny(collection)
    }

    override fun execute(runnable: Runnable) {
        if (runnable is JmcThread) {
            // If the runnable is already a JmcThread, reuse the taskId
            val jmcFuture: JmcFuture<*> = JmcFuture<Any>(runnable)
            offer(jmcFuture)
        } else {
            // Otherwise, create a new JmcThread
            offer(JmcFuture<Any>(runnable, JmcRuntime.addNewTask()))
        }
    }

    private class JmcExecutorWorker(
        private val id: Int,
        private val queue: BlockingQueue<JmcFuture<*>>,
        private val workCounter: AtomicInteger
    ) :
        Thread() {
        private val isShutdown = AtomicBoolean(false)

        fun shutdown() {
            isShutdown.set(true)
        }

        fun isShutdown(): Boolean {
            return isShutdown.get()
        }

        override fun run() {
            while (!isShutdown.get()) {
                var task: JmcFuture<*>? = null
                try {
                    task = queue.take()
                    LOGGER.debug("Received task {} in worker {}", task.taskId, id)
                    workCounter.incrementAndGet()
                    task.run()
                    workCounter.decrementAndGet()
                } catch (e: InterruptedException) {
                    LOGGER.debug("Interrupted", e)
                } finally {
                    if (task != null) {
                        if (queue.isEmpty()) {
                            JmcRuntime.join(task.taskId)
                        } else {
                            JmcRuntime.terminate(task.taskId)
                        }
                    }
                    LOGGER.debug("Completed task in worker {}", id)
                }
            }
        }

        companion object {
            private val LOGGER: Logger = LogManager.getLogger(
                JmcExecutorWorker::class.java
            )
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcExecutorService::class.java
        )
    }
}
