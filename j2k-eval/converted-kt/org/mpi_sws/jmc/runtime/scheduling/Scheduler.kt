package org.mpi_sws.jmc.runtime.scheduling

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.runtime.*
import org.mpi_sws.jmc.runtime.scheduling.Scheduler.SchedulerThread
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy
import org.mpi_sws.jmc.strategies.SchedulingStrategy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

/**
 * The scheduler is responsible for managing the execution of threads.
 *
 *
 * The scheduler uses the strategy to decide which thread to Schedule. To do so, it instantiates
 * a separate SchedulerThread that is blocked whenever other threads are running and unblocked only
 * when it needs to pick the next thread to schedule.
 *
 *
 * To interact with the scheduler, invoke the [Scheduler.yield] which will defer control
 * the scheduler thread.
 */
class Scheduler(
    /**
     * The scheduling strategy used to decide which thread to schedule.
     */
    private val strategy: SchedulingStrategy?, schedulerTries: Int, schedulerTrySleepTimeNanos: Long
) {
    /**
     * The thread manager used to manage the thread states.
     */
    private var taskManager: TaskManager? = null

    /**
     * The ID of the current thread. Protected by the lock for accesses to read and write
     */
    private var currentTask: Long?

    private val currentTaskLock = Any()

    /**
     * The scheduler thread instance.
     */
    private val schedulerThread =
        SchedulerThread(this, strategy, schedulerTries, schedulerTrySleepTimeNanos)

    var isInStopAllMode: Boolean = false
        private set

    /**
     * Constructs a new Scheduler object.
     *
     * @param strategy the scheduling strategy
     */
    init {
        this.currentTask = 0L
    }

    /**
     * Starts the scheduler thread.
     */
    fun start() {
        schedulerThread.start()
    }

    /**
     * Initializes the scheduler with the task manager and the main thread.
     *
     * @param taskManager the task manager
     * @param mainTaskId  the ID of the main thread
     */
    fun init(taskManager: TaskManager?, mainTaskId: Long?) {
        this.taskManager = taskManager
        this.setCurrentTask(mainTaskId)
    }

    /**
     * Initializes the strategy for a new iteration.
     *
     * @param iteration the number of the iteration
     */
    @Throws(HaltCheckerException::class)
    fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        // Ask the tastManager if the all thread are finished. If not block it
        // clear the threads state
        strategy!!.initIteration(iteration, report)
    }

    /**
     * Returns the ID of the current task.
     *
     * @return the ID of the current task
     */
    fun currentTask(): Long? {
        synchronized(currentTaskLock) {
            return currentTask
        }
    }

    /**
     * Sets the ID of the current task.
     *
     * @param taskId the ID of the current task
     */
    protected fun setCurrentTask(taskId: Long?) {
        synchronized(currentTaskLock) {
            currentTask = taskId
        }
    }

    /**
     * Performs the scheduling choice (instance of [SchedulingChoice]) indicated. Either
     * resuming the task, stopping the task or stopping all tasks.
     *
     * @param choice The scheduling choice to make.
     */
    protected fun <T : SchedulingChoiceValue?> scheduleTask(choice: SchedulingChoice<T?>) {
        if (choice.isBlockExecution) {
            LOGGER.debug("Stopping all tasks.")
            startStopAllMode()
        } else if (choice.isBlockTask) {
            val taskId = choice.taskId
            setCurrentTask(taskId)
            taskManager!!.error(taskId, HaltTaskException.Companion.blocked(taskId))
        } else {
            val taskId = choice.taskId
            if (taskId == null) {
                LOGGER.error("Resuming a task with null ID.")
                throw HaltExecutionException.Companion.error("Resuming a task with null ID.")
            }
            setCurrentTask(taskId)
            try {
                LOGGER.debug("Resuming task: {}", taskId)
                if (taskId == null) {
                    LOGGER.error("Task ID is null, cannot resume task.")
                    throw HaltExecutionException.Companion.error("Task ID is null, cannot resume task.")
                }
                if (choice.value != null) {
                    taskManager!!.resume(taskId, choice.value)
                } else {
                    taskManager!!.resume(taskId)
                }
            } catch (e: TaskNotExists) {
                LOGGER.error("Resuming a non existent task: {}", e.message)
                throw HaltExecutionException.Companion.error(e.message)
            }
        }
    }

    private fun startStopAllMode() {
        isInStopAllMode = true
        doNextStop()
    }

    private fun doNextStop() {
        val taskId = taskManager!!.doNextStop()
        if (taskId == -1L) {
            LOGGER.error("Task ID is null, cannot stop the task.")
            throw HaltExecutionException.Companion.error("Task ID is null, cannot stop the task.")
        }
        setCurrentTask(taskId)
        taskManager!!.stopTask(taskId)
        if (taskId == 1L) {
            // Main task stopped, exit stop all mode
            isInStopAllMode = false
            LOGGER.debug("Exiting stop all mode.")
        }
    }

    /**
     * Updates the event in the scheduling strategy.
     *
     * @param event the event to be updated
     */
    @Throws(HaltTaskException::class)
    fun updateEvent(event: JmcRuntimeEvent) {
        if (isInStopAllMode) {
            return
        }
        strategy!!.updateEvent(event)
    }

    /**
     * Pauses the current task and yields the control to the scheduler.
     *
     *
     * The call is non-blocking and returns immediately.
     *
     * @return a future that completes when the task is resumed
     * @throws TaskAlreadyPaused if the current task is already paused
     */
    @Throws(TaskAlreadyPaused::class)
    fun yield(): CompletableFuture<*>? {
        if (!isInStopAllMode) {
            val future: CompletableFuture<*>
            synchronized(currentTaskLock) {
                future = taskManager!!.pause<Any>(currentTask)
                currentTask = null
            }
            // Release the scheduler thread
            LOGGER.debug("Enabling scheduler thread.")
            schedulerThread.enable()
            return future
        }
        return null
    }

    fun yieldWithoutPausing() {
        synchronized(currentTaskLock) {
            currentTask = null
        }
        // Release the scheduler thread
        LOGGER.debug("Enabling scheduler thread.")
        schedulerThread.enable()
    }

    /**
     * Pauses the task with the given ID and yields the control to the scheduler.
     *
     *
     * The call is non-blocking and returns immediately.
     *
     * @param taskId the ID of the task to be paused
     * @return a future that completes when the task is resumed
     * @throws TaskAlreadyPaused if the task is already paused
     */
    @Throws(TaskAlreadyPaused::class)
    fun yield(taskId: Long?): CompletableFuture<*>? {
        if (!isInStopAllMode) {
            val future: CompletableFuture<*> = taskManager!!.pause<Any>(taskId)
            synchronized(currentTaskLock) {
                currentTask = null
            }
            // Release the scheduler thread
            LOGGER.debug("Enabling scheduler thread.")
            schedulerThread.enable()
            return future
        }
        return null
    }

    /**
     * Resets the TaskManager and the scheduling strategy for a new iteration.
     */
    fun resetIteration(iteration: Int) {
        strategy!!.resetIteration(iteration)
    }

    fun recordTrace() {
        if (strategy is ReplayableSchedulingStrategy) {
            try {
                strategy.recordTrace()
            } catch (e: JmcCheckerException) {
                LOGGER.error("Failed to record trace: {}", e.message)
            }
        } else {
            LOGGER.warn("Recording trace is not supported by the current scheduling strategy")
        }
    }

    /**
     * Shuts down the scheduler.
     */
    fun shutdown(report: JmcModelCheckerReport) {
        schedulerThread.shutdown()
        strategy!!.teardown(report)
    }

    /**
     * The SchedulerThread class is responsible for scheduling the tasks.
     */
    private class SchedulerThread(
        /**
         * The scheduler instance.
         */
        private val scheduler: Scheduler,
        /**
         * The scheduling strategy used by the scheduler.
         */
        private val strategy: SchedulingStrategy?,
        private val schedulerTries: Int,
        private val schedulerTrySleepTimeNanos: Long
    ) : Thread() {
        /**
         * A queue used to enable the scheduler thread. Adding a boolean value to the queue enables
         * the scheduler to run one iteration, while adding a true value shuts down the scheduler.
         */
        private val enablingQueue = LinkedBlockingQueue<Boolean>()

        /**
         * Enables the scheduler.
         */
        fun enable() {
            try {
                enablingQueue.put(false)
            } catch (e: InterruptedException) {
                LOGGER.error("Enabling the scheduler thread was interrupted: {}", e.message)
            }
        }

        /**
         * Shuts down the scheduler.
         */
        fun shutdown() {
            try {
                enablingQueue.put(true)
            } catch (e: InterruptedException) {
                LOGGER.error(
                    "Shutting down the scheduler thread was interrupted: {}", e.message
                )
            }
        }

        /**
         * The main loop of the scheduler thread.
         */
        override fun run() {
            LOGGER.info("Scheduler thread started.")
            while (true) {
                // Wait for the scheduler to be enabled
                try {
                    val shutdown = enablingQueue.take()
                    if (shutdown) {
                        LOGGER.info("Shutting down scheduler thread.")
                        break
                    }
                    LOGGER.debug("Scheduler thread enabled.")

                    // Repeat until the task is not null. Error out after trying x times.
                    // It is possible that the scheduler is enabled but no task is available.
                    // The solution is to just wait for something to become available. and throw an
                    // error otherwise.
                    if (scheduler.isInStopAllMode) {
                        scheduler.doNextStop()
                        continue
                    }

                    var nextTask: SchedulingChoice<*>? = null
                    for (i in 0..<schedulerTries) {
                        nextTask = strategy!!.nextTask()
                        if (nextTask != null) {
                            break
                        }
                        if (schedulerTrySleepTimeNanos > 0) {
                            sleep(schedulerTrySleepTimeNanos)
                        }
                    }
                    if (nextTask != null) {
                        scheduler.scheduleTask(nextTask)
                    } else {
                        LOGGER.error("No task to schedule.")
                    }
                } catch (e: Exception) {
                    LOGGER.error("Scheduler thread threw an exception: {}", e.message)
                    break
                }
            }
            LOGGER.info("Scheduler thread finished.")
        }

        companion object {
            private val LOGGER: Logger = LogManager.getLogger(
                SchedulerThread::class.java.name
            )
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(Scheduler::class.java.name)
    }
}
