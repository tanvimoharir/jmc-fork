package org.mpi_sws.jmc.api.util.concurrent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.util.concurrent.JmcScheduledExecutorService
import org.mpi_sws.jmc.api.util.concurrent.JmcScheduledExecutorService.JmcScheduledExecutorWorker
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A scheduled executor service for JMC model checking.
 *
 *
 * This class extends [ScheduledThreadPoolExecutor] to maintain type compatibility
 * with code that casts to ScheduledThreadPoolExecutor. In JMC's controlled execution:
 *
 *  * Scheduling delays are modeled as yield points (no actual time delay)
 *  * Periodic tasks (scheduleAtFixedRate, scheduleWithFixedDelay) execute once
 *  * All tasks are executed by worker threads managed by JMC's runtime
 *
 */
class JmcScheduledExecutorService : ScheduledThreadPoolExecutor {
    //Worker thread management - same pattern as JmcExecutorService
    private val counter: AtomicInteger
    private val capacity: Int
    private val queue: BlockingQueue<Runnable>
    private val workers: MutableList<JmcScheduledExecutorWorker>
    private val isShutdown = AtomicBoolean(false)

    /**
     * Creates a scheduled executor service with the specified core pool size.
     *
     * @param corePoolSize the number of threads to keep in the pool
     */
    constructor(corePoolSize: Int) : super(corePoolSize) {
        counter = AtomicInteger(0)
        capacity = corePoolSize
        queue = LinkedBlockingQueue()
        workers = ArrayList()
        for (i in 0..<capacity) {
            val worker = JmcScheduledExecutorWorker(i, this.queue, this.counter)
            workers.add(worker)
            worker.start()
        }
        isShutdown.set(false)
        JmcRuntimeUtils.registerExecutor(this)
    }

    /**
     * Creates a scheduled executor service with the specified core pool size and thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool
     * @param threadFactory the factory to use when creating new threads
     */
    constructor(corePoolSize: Int, threadFactory: ThreadFactory) : super(corePoolSize, threadFactory) {
        counter = AtomicInteger(0)
        capacity = corePoolSize
        queue = LinkedBlockingQueue()
        workers = ArrayList()
        for (i in 0..<capacity) {
            val worker = JmcScheduledExecutorWorker(i, this.queue, this.counter)
            workers.add(worker)
            worker.start()
        }
        isShutdown.set(false)
        JmcRuntimeUtils.registerExecutor(this)
    }

    /**
     * Add a future to the work queue and yield to allow workers to pick it up.
     * Uses the same pattern as JmcExecutorService.offer().
     */
    private fun offer(runnable: Runnable) {
        if (counter.get() < capacity) {
            //If we know that the task will be immediately picked up,
            // We pause and wait for the matching yield
            val taskId = JmcRuntime.currentTask()
            JmcRuntime.pause(taskId)
            queue.offer(runnable)
            JmcRuntime.wait<Any>(taskId)
        } else {
            //Otherwise all other actual JVM threads are blocked.
            // Hence, we just yield and allow one of them to continue
            queue.offer(runnable)
            JmcRuntime.yield<Any>()
        }
    }

    /**
     * Schedule a Runnable task to execute after a delay.
     * In JMC, the delay is modeled as a yield point - the task executes immediately.
     */
    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): JmcScheduledFuture<*> {
        val future: JmcScheduledFuture<*> = JmcScheduledFuture<Any>(command, JmcRuntime.addNewTask())
        offer(future)
        return future
    }

    /**
     * Schedule a Callable task to execute after a delay.
     * In JMC, the delay is modeled as a yield point - the task executes immediately.
     */
    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): JmcScheduledFuture<V> {
        val future = JmcScheduledFuture(callable, JmcRuntime.addNewTask())
        offer(future)
        return future
    }

    /**
     * Schedule a task to execute periodically at a fixed rate.
     * In JMC, periodic execution is not modeled - the task executes once.
     */
    override fun scheduleAtFixedRate(
        command: Runnable,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit
    ): JmcScheduledFuture<*> {
        //Execute once since we do not have periodic execution in Jmc
        val future: JmcScheduledFuture<*> = JmcScheduledFuture<Any>(command, JmcRuntime.addNewTask())
        offer(future)
        return future
    }

    /**
     * Schedule a task to execute periodically with a fixed delay between executions.
     * In JMC, periodic execution is not modeled - the task executes once.
     */
    override fun scheduleWithFixedDelay(
        command: Runnable,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit
    ): JmcScheduledFuture<*> {
        //Execute once since there is no periodic execution in jmc
        val future: JmcScheduledFuture<*> = JmcScheduledFuture<Any?>(command, JmcRuntime.addNewTask())
        offer(future)
        return future
    }


    /**
     * Submits a Callable task to the executor service.
     */
    override fun <T> submit(callable: Callable<T>): JmcFuture<T> {
        val future: JmcFuture<T> = JmcFuture<Any?>(callable, JmcRuntime.addNewTask())
        offer(future)
        return future
    }


    /**
     * Submits a Runnable task with a result to the executor service.
     */
    override fun <T> submit(runnable: Runnable, result: T): JmcFuture<T> {
        val future = if (runnable is JmcThread) {
            JmcFuture(runnable, result)
        } else {
            // Otherwise create a new JmcThread via JmcFuture's constructor
            JmcFuture(runnable, result, JmcRuntime.addNewTask())
        }
        offer(future)
        return future
    }


    /**
     * Submits a Runnable task to the executor service.
     */
    override fun submit(runnable: Runnable): JmcFuture<*> {
        val future = if (runnable is JmcThread) {
            JmcFuture<Runnable>(runnable, runnable)
        } else {
            JmcFuture<Any>(runnable, JmcRuntime.addNewTask())
        }
        offer(future)
        return future
    }

    /**
     * Executes a Runnable task.
     */
    override fun execute(runnable: Runnable) {
        if (runnable is JmcThread) {
            val jmcFuture: JmcFuture<*> = JmcFuture<Any>(runnable)
            offer(jmcFuture)
        } else {
            offer(JmcFuture<Any>(runnable, JmcRuntime.addNewTask()))
        }
    }


    /**
     * Invokes all callable tasks and returns their futures.
     */
    @Throws(InterruptedException::class)
    override fun <T> invokeAll(collection: Collection<Callable<T>>): List<Future<T>> {
        //Map each callable to a future and run them
        val futures: MutableList<Future<T>> = ArrayList()
        for (callable in collection) {
            val future = JmcFuture(callable, JmcRuntime.addNewTask())
            futures.add(future)
            offer(future)
        }
        return futures
    }


    /**
     * Invokes all callable tasks with a timeout and returns their futures.
     * Timeout is ignored in JMC.
     */
    @Throws(InterruptedException::class)
    override fun <T> invokeAll(
        collection: Collection<Callable<T>?>, l: Long, timeUnit: TimeUnit
    ): List<Future<T>> {
        return invokeAll(collection)
    }

    /**
     * Invokes any callable task and returns the result of the first completed one.
     */
    @Throws(InterruptedException::class, ExecutionException::class)
    override fun <T> invokeAny(collection: Collection<Callable<T>>): T {
        val futures: MutableList<JmcFuture<*>> = ArrayList()
        val allTasks: MutableSet<Long?> = HashSet()
        for (callable in collection) {
            val future = JmcFuture(callable, JmcRuntime.addNewTask())
            futures.add(future)
            allTasks.add(JmcRuntime.addNewTask())
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
            //Check if all tasks are completed
            if (completedTasks.size == allTasks.size) {
                break
            }
        }
        return null
    }


    /**
     * Invokes any callable task with a timeout and returns the result of the first completed one.
     * Timeout is ignored in JMC.
     */
    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun <T> invokeAny(collection: Collection<Callable<T>?>, l: Long, timeUnit: TimeUnit): T {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        return invokeAny(collection)
    }

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
                LOGGER.error("Error while shutting down scheduled worker thread", e)
            }
        }
    }

    override fun shutdownNow(): List<Runnable> {
        for (worker in workers) {
            worker.shutdown()
        }
        isShutdown.set(true)
        return ArrayList()
    }

    override fun isShutdown(): Boolean {
        return isShutdown.get()
    }

    override fun isTerminated(): Boolean {
        return isShutdown.get() && counter.get() == 0
    }

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

    /**
     * Worker thread that executes tasks from the queue.
     * Handles both JmcFuture and JmcScheduledFuture.
     */
    class JmcScheduledExecutorWorker(
        private val id: Int, private val queue: BlockingQueue<Runnable>,
        private val workCounter: AtomicInteger
    ) : Thread() {
        private val isShutdown = AtomicBoolean(false)

        fun shutdown() {
            isShutdown.set(true)
        }

        fun isShutdown(): Boolean {
            return isShutdown.get()
        }

        override fun run() {
            while (!isShutdown.get()) {
                var task: Runnable? = null
                var taskId: Long? = null
                try {
                    task = queue.take()

                    //Extract taskId based on task type
                    if (task is JmcFuture<*>) {
                        taskId = task.taskId
                    } else if (task is JmcScheduledFuture<*>) {
                        taskId = task.taskId
                    }

                    LOGGER.debug("Scheduled worker {}  received task {}", id, taskId)
                    workCounter.incrementAndGet()
                    task.run()
                    workCounter.decrementAndGet()
                } catch (e: InterruptedException) {
                    //Worker interrupted
                } finally {
                    if (task != null && taskId != null) {
                        if (queue.isEmpty()) {
                            JmcRuntime.join(taskId)
                        } else {
                            JmcRuntime.terminate(taskId)
                        }
                    }
                    LOGGER.debug("Scheduled worker {}  completed task", id)
                }
            }
        }

        companion object {
            private val LOGGER: Logger = LogManager.getLogger(
                JmcScheduledExecutorWorker::class.java
            )
        }
    }


    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcScheduledExecutorService::class.java
        )
    }
}
