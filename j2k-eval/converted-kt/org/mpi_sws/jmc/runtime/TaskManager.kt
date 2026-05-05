package org.mpi_sws.jmc.runtime

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Encapsulates all the operations related to Task objects used by the runtime Except the
 * SchedulerTask The encapsulation ensures no memory leak when creating many tasks.
 */
class TaskManager {
    /**
     * The state of each task managed by the @RuntimeEnvironment is represented by one of the
     * following.
     */
    enum class TaskState {
        RUNNING,
        BLOCKED,
        CREATED,
        TERMINATED,
    }

    /**
     * Stores a set of custom IDs used by the Runtime.
     */
    private var idCounter = 1L

    private val idCounterLock = Any()

    /**
     * Stores the state of each task.
     */
    private val taskStates: MutableMap<Long?, TaskState> =
        HashMap()

    /**
     * Stores the future of blocked tasks.
     */
    private val taskFutures: MutableMap<Long?, CompletableFuture<*>> =
        HashMap()

    private val tasksLock = Any()

    /**
     * Returns the next task ID to be assigned.
     *
     * @return the next task ID to be assigned
     */
    private fun nextTaskId(): Long {
        synchronized(idCounterLock) {
            return idCounter++
        }
    }

    /**
     * Resets the TaskManager object.
     */
    fun reset() {
        synchronized(idCounterLock) {
            idCounter = 1L
        }
        synchronized(tasksLock) {
            taskStates.clear()
            for (future in taskFutures.values) {
                future.complete(null)
            }
            taskFutures.clear()
        }
    }

    /**
     * Adds a new task to the TaskManager object. The task is assigned the next available task ID
     * and a default name "Task-ID".
     *
     * @return the ID of the task
     */
    fun addNextTask(): Long {
        val customTaskId = nextTaskId()
        synchronized(tasksLock) {
            taskStates.put(customTaskId, TaskState.CREATED)
        }
        return customTaskId
    }

    /**
     * Pauses the task with the specified custom ID. A new future is created and stored in the
     * [TaskManager.taskFutures] map. If the task is already paused, a [ ] exception is thrown.
     *
     * @param taskId the custom ID of the task
     * @return a future that completes when the task is resumed
     * @throws TaskAlreadyPaused if the task with the specified custom ID is already paused
     */
    @Throws(TaskAlreadyPaused::class)
    fun <T> pause(taskId: Long?): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        synchronized(tasksLock) {
            if (taskFutures.containsKey(taskId)) {
                throw TaskAlreadyPaused()
            }
            taskStates[taskId] = TaskState.BLOCKED
            taskFutures.put(taskId, future)
        }
        return future
    }

    /**
     * Resumes the task with the specified custom ID. The future associated with the task is
     * completed.
     *
     * @param taskId the custom ID of the task
     * @throws TaskNotExists if the task with the specified custom ID does not exist
     */
    @Throws(TaskNotExists::class)
    fun resume(taskId: Long) {
        synchronized(tasksLock) {
            val future = taskFutures[taskId]
                ?: // The task is not paused or has been completed.
                throw TaskNotExists(taskId)
            future.complete(null)
            taskFutures.remove(taskId)
            taskStates.put(taskId, TaskState.RUNNING)
        }
    }

    @Throws(TaskNotExists::class)
    fun <T> resume(taskId: Long, value: T) {
        val future: CompletableFuture<*>
        synchronized(tasksLock) {
            future = taskFutures[taskId]!!
            if (future == null) {
                // The task is not paused or has been completed.
                throw TaskNotExists(taskId)
            }
            taskFutures.remove(taskId)
            taskStates.put(taskId, TaskState.RUNNING)
        }
        try {
            val castedFuture = future as CompletableFuture<T>
            castedFuture.complete(value)
        } catch (e: ClassCastException) {
            LOGGER.error("Failed to cast future for task: {}", taskId)
            throw TaskNotExists(taskId)
        }
    }

    fun error(taskId: Long?, e: Exception?) {
        synchronized(tasksLock) {
            val future = taskFutures[taskId] ?: return
            future.completeExceptionally(e)
            taskFutures.remove(taskId)
        }
    }

    /**
     * Terminates the task with the specified custom ID. The future associated with the task is
     * completed.
     *
     * @param taskId the custom ID of the task
     */
    fun terminate(taskId: Long?) {
        synchronized(tasksLock) {
            taskStates[taskId] = TaskState.TERMINATED
            val future = taskFutures[taskId] ?: return
            future.complete(null)
            taskFutures.remove(taskId)
        }
    }

    /**
     * Return the size of the task pool.
     *
     * @return the size of the task pool
     */
    fun size(): Int {
        synchronized(tasksLock) {
            return taskStates.size
        }
    }

    /**
     * Update the state of the task with the specified custom ID.
     *
     * @param taskId the custom ID of the task
     * @param state  the new state of the task
     */
    fun markStatus(taskId: Long?, state: TaskState) {
        synchronized(tasksLock) {
            taskStates.put(taskId, state)
        }
    }

    /**
     * Return the state of the task with the specified custom ID.
     *
     * @param taskId the custom ID of the task
     * @return the state of the task
     */
    fun getStatus(taskId: Long?): TaskState? {
        synchronized(tasksLock) {
            return taskStates[taskId]
        }
    }

    /**
     * Return all the tasks with the specified state.
     *
     * @param state the state of the tasks to find
     * @return a list of tasks with the specified state
     */
    fun findTasksWithStatus(state: TaskState): List<Long?> {
        val result: MutableList<Long?> = ArrayList()
        synchronized(tasksLock) {
            for ((key, value) in taskStates) {
                if (value == state) {
                    result.add(key)
                }
            }
        }
        return result
    }

    val activeTasks: List<Long?>
        /**
         * Return custom IDs of all the tasks.
         *
         * @return a list of custom IDs of all the tasks
         */
        get() {
            val result = ArrayList<Long?>()
            synchronized(tasksLock) {
                for ((key, value) in taskStates) {
                    if (value != TaskState.TERMINATED) {
                        result.add(key)
                    }
                }
            }
            return result
        }

    /**
     * Return true if the task with the specified system task ID is in the task pool and with status
     * provided.
     *
     * @param taskId the custom ID of the task
     * @param state  the state of the task
     * @return true if the task exists with status
     */
    fun isTaskOfStatus(taskId: Long?, state: TaskState): Boolean {
        synchronized(tasksLock) {
            if (!taskStates.containsKey(taskId)) {
                return false
            }
            return taskStates[taskId] == state
        }
    }

    /**
     * Wait for the task with the specified custom ID to complete.
     *
     * @param taskId the custom ID of the task
     */
    @Throws(InterruptedException::class, ExecutionException::class)
    fun <T> wait(taskId: Long?): T? {
        val future: CompletableFuture<*>?
        synchronized(tasksLock) {
            future = taskFutures[taskId]
        }
        if (future == null) {
            return null
        }
        try {
            val castedFuture = future as CompletableFuture<T>
            return castedFuture.get()
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is HaltTaskException) {
                throw cause
            } else if (cause is HaltExecutionException && cause.isReexecutionNeeded) {
                throw HaltExecutionException.Companion.reexecutionNeeded()
            } else {
                throw e
            }
        }
    }

    /**
     * Stop all the tasks in the task pool.
     */
    fun stopAll() {
        println("******stopAll*****")
        synchronized(tasksLock) {
            for ((_, value) in taskFutures) {
                value
                    .completeExceptionally(HaltExecutionException.Companion.error("Stopping execution"))
                // wait
            }
            taskFutures.clear()
            taskStates.clear()
        }
    }

    fun doNextStop(): Long? {
        synchronized(tasksLock) {
            val taskIds: List<Long?> = ArrayList(taskStates.keys)
            taskIds.sort(java.util.Comparator { obj: Long, anotherLong: Long? ->
                obj.compareTo(
                    anotherLong!!
                )
            })
            for (i in taskIds.indices.reversed()) {
                val taskId = taskIds[i]
                if (taskStates[taskId] != TaskState.TERMINATED &&
                    taskStates[taskId] != TaskState.CREATED
                ) {
                    return taskId
                }
            }
        }
        return -1L
    }

    fun stopTask(taskId: Long?) {
        synchronized(tasksLock) {
            val future = taskFutures[taskId]
            future?.completeExceptionally(HaltExecutionException.Companion.reexecutionNeeded())
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(TaskManager::class.java)
    }
}
