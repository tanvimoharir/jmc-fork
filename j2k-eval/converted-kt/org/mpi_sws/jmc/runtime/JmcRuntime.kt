package org.mpi_sws.jmc.runtime

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.runtime.scheduling.*
import org.mpi_sws.jmc.strategies.JmcReplayUnsupported
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy
import java.util.concurrent.ExecutionException

/**
 * The Runtime environment complete with a scheduler and configuration options used by the model
 * checker.
 *
 *
 * Calls to the runtime are made by the instrumented byte code. These calls are used to record
 * events occurring during the execution of tasks or allow for scheduling changes. For example, the
 * runtime can be used to record Thread creation and deletion.
 *
 *
 * The runtime is a static class that stores minimal states and delegates calls to the [ ] which retains all the state.
 */
object JmcRuntime {
    private val LOGGER: Logger = LogManager.getLogger(JmcRuntime::class.java)

    private val taskManager = TaskManager()

    private var scheduler: Scheduler? = null

    private var config: JmcRuntimeConfiguration? = null

    /**
     * Sets up the runtime with the given configuration.
     *
     * @param config the configuration (instance of [JmcRuntimeConfiguration])
     */
    fun setup(config: JmcRuntimeConfiguration) {
        LOGGER.debug("Setting up!")
        JmcRuntime.config = config
        scheduler =
            Scheduler(
                config.strategy,
                config.schedulerTries,
                config.schedulerTrySleepTimeNanos
            )
        scheduler!!.start()
    }

    @Throws(JmcCheckerException::class)
    fun setupReplay(config: JmcRuntimeConfiguration) {
        LOGGER.debug("Setting up for replay!")
        JmcRuntime.config = config
        val strategy = config.strategy
        if (strategy !is ReplayableSchedulingStrategy) {
            LOGGER.error(
                "The provided strategy is not replayable. Please use a replayable strategy."
            )
            throw JmcReplayUnsupported()
        }
        strategy.replayRecordedTrace()
        scheduler =
            Scheduler(
                strategy,
                config.schedulerTries,
                config.schedulerTrySleepTimeNanos
            )
        scheduler!!.start()
    }

    /**
     * Tears down the runtime by shutting down the scheduler adn clearing the task manager.
     */
    fun tearDown(report: JmcModelCheckerReport) {
        LOGGER.debug("Tearing down!")
        taskManager.reset()
        scheduler!!.shutdown(report)
    }

    private fun updateLoggerFile(iteration: Int) {
        val fileName = config.getReportPath() + "/jmc-runtime-" + iteration + ".log"
        val builder =
            ConfigurationBuilderFactory.newConfigurationBuilder()
        val configuration: Configuration =
            builder.add(
                builder.newAppender("FILE", "File")
                    .addAttribute("fileName", fileName)
                    .addAttribute("append", false)
                    .add(
                        builder.newLayout("PatternLayout")
                            .addAttribute(
                                "pattern",
                                "%d [%t] %5p %c{1.} - %m%n"
                            )
                    )
            )
                .add(builder.newRootLogger(Level.DEBUG).add(builder.newAppenderRef("FILE")))
                .build(false)
        Configurator.reconfigure(configuration)
    }

    /**
     * Initializes the runtime with the main thread for a given iteration.
     *
     *
     * Initializes the scheduler with the main thread and marks it as ready.
     *
     * @param iteration the iteration number
     */
    fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        if (config.getDebug()) {
            updateLoggerFile(iteration)
        }
        LOGGER.debug("Initializing iteration")
        scheduler!!.initIteration(iteration, report)
        val mainThreadId = taskManager.addNextTask()
        taskManager.markStatus(mainThreadId, TaskManager.TaskState.BLOCKED)

        scheduler!!.init(taskManager, mainThreadId)
        try {
            scheduler!!.updateEvent(
                JmcRuntimeEvent.Builder()
                    .type(JmcRuntimeEvent.Type.START_EVENT)
                    .taskId(mainThreadId)
                    .param("startedBy", 1L)
                    .build()
            )
        } catch (ignored: HaltTaskException) {
            LOGGER.error("Failed to start main thread.")
        }
        yield<Any>()
        if (iteration != 0) {
            JmcRuntimeUtils.invokeStaticInitializedClasses(iteration)
        }
    }

    /**
     * Resets the runtime for a new iteration.
     */
    fun resetIteration(iteration: Int) {
        scheduler!!.resetIteration(iteration)
        taskManager.reset()
        JmcRuntimeUtils.clearSyncLocks()
    }

    fun recordTrace() {
        scheduler!!.recordTrace()
    }

    /**
     * Pauses the current task that invokes this method and yields the control to the scheduler. The
     * call returns only when the task that invoked this method is resumed.
     */
    fun <T> yield(): T {
        val currentTask = scheduler!!.currentTask()
        try {
            LOGGER.debug("Yielding task {}", currentTask)
            scheduler!!.yield()
        } catch (e: TaskAlreadyPaused) {
            LOGGER.error("Yielding an already paused task.")
            throw HaltExecutionException.Companion.error("Yielding an already paused task.")
        }
        return wait(currentTask)
    }

    /**
     * Pauses the task with the given ID and yields the control to the scheduler. The call returns
     * only when the task with the given ID is resumed.
     *
     *
     * Use with Caution! It is meant to be called when a new concurrent task starts and yields
     * with the new task id. Should not be used otherwise.
     *
     * @param taskId the ID of the task to be paused
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    fun yield(taskId: Long?) {
        try {
            LOGGER.debug("Yielding task explicitly {}", taskId)
            scheduler!!.yield(taskId)
        } catch (e: TaskAlreadyPaused) {
            LOGGER.error("Yielding an already paused task.")
            throw HaltExecutionException.Companion.error("Yielding an already paused task.")
        }
        wait<Any>(taskId)
    }

    /**
     * Pauses the task.
     *
     * @param taskId the ID of the task to be paused
     */
    fun pause(taskId: Long?) {
        try {
            taskManager.pause<Any>(taskId)
        } catch (e: TaskAlreadyPaused) {
            LOGGER.error("Current thread is already paused.")
            throw HaltExecutionException.Companion.error("Current thread is already paused.")
        }
    }

    fun <T> wait(taskId: Long?): T? {
        try {
            return taskManager.wait(taskId)
        } catch (e: HaltExecutionException) {
            if (e.isReexecutionNeeded) {
                LOGGER.debug("Re-execution needed, throwing HaltExecutionException")
                throw HaltExecutionException.Companion.reexecutionNeeded()
            } else {
                LOGGER.error("Failed to wait for task: {}", taskId)
                val cause = e.cause
                throw HaltExecutionException.Companion.error(cause!!.message)
            }
        } catch (e: ExecutionException) {
            LOGGER.error("Failed to wait for task: {}", taskId)
            val cause = e.cause
            throw HaltExecutionException.Companion.error(cause!!.message)
        } catch (e: InterruptedException) {
            LOGGER.error("Failed to wait for task: {}", taskId)
            val cause = e.cause
            throw HaltExecutionException.Companion.error(cause!!.message)
        }
    }

    /**
     * Updates the TaskManager to terminate the task with the given ID and yields control to the
     * scheduler to schedule other tasks.
     *
     * @param taskId the ID of the task to be terminated
     */
    fun join(taskId: Long?) {
        LOGGER.debug("Joining task {}", taskId)
        //try {
        taskManager.terminate(taskId)
        //scheduler.yield();
        scheduler!!.yieldWithoutPausing()
        /*} catch (TaskAlreadyPaused e) {
            LOGGER.error("Joining an already paused task.");
            throw HaltExecutionException.error("Joining an already paused task.");
        }*/
    }

    /**
     * Returns the current task id.
     *
     * @return the current task id
     */
    @JvmStatic
    fun currentTask(): Long {
        val currentTask = scheduler!!.currentTask()
        if (currentTask == null) {
            LOGGER.error("No current task.")
            throw HaltExecutionException.Companion.error("No current task.")
        }
        return currentTask
    }

    /**
     * Adds a new event to the scheduler which is passed to the strategy.
     *
     * @param event to be added
     */
    @Throws(HaltTaskException::class)
    fun updateEvent(event: JmcRuntimeEvent) {
        LOGGER.debug("Updating event: {}", event)
        try {
            scheduler!!.updateEvent(event)
        } catch (e: HaltTaskException) {
            LOGGER.error("Failed to update event: {}", event)
            taskManager.terminate(event.taskId)
            throw e
        } catch (e: HaltExecutionException) {
            if (e.isReexecutionNeeded) {
                throw HaltExecutionException.Companion.reexecutionNeeded()
            }
            throw e
        } catch (e: Exception) {
            LOGGER.error("Failed to update event: {}", event, e)
            throw HaltExecutionException.Companion.error(e.message)
        }
    }

    /**
     * Terminates the task with the given ID.
     *
     * @param taskId the ID of the task to be terminated
     */
    fun terminate(taskId: Long?) {
        LOGGER.debug("Terminating task {}", taskId)
        taskManager.terminate(taskId)
    }

    /**
     * Adds a new event to the runtime and yields the control to the scheduler.
     *
     * @param event the new event
     */
    @JvmStatic
    @Throws(HaltTaskException::class)
    fun <T> updateEventAndYield(event: JmcRuntimeEvent): T {
        updateEvent(event)
        return yield()
    }

    /**
     * Adds a new task to the runtime and creates a future for that task.
     *
     * @return the ID of the new task
     */
    fun addNewTask(): Long? {
        val newTaskId = taskManager.addNextTask()
        LOGGER.debug("Adding new task {}", newTaskId)
        return newTaskId
    }
}
