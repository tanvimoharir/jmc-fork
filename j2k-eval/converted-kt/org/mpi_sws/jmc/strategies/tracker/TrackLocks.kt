package org.mpi_sws.jmc.strategies.tracker

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** Tracks the locks acquired and released events of tasks.  */
open class TrackLocks : Tracker {
    /**
     * For each lock, Contains a list of tasks that want the lock. Once the task acquires the lock,
     * it is removed from the set.
     */
    private val waitingTasks: MutableMap<Any, MutableSet<Long>> =
        ConcurrentHashMap()

    private val wantingTasks: MutableMap<Any, MutableSet<Long>> =
        ConcurrentHashMap()

    private val activeTasks: MutableMap<Long?, Optional<Any>> = ConcurrentHashMap()

    /**
     * Updates based on lock acquire and release events.
     *
     *
     * For every acquire event, if the lock is already acquired, the task is made to wait.
     * Tracked in [TrackLocks.waitingTasks].
     *
     *
     * If it is not yet acquired, it is put in [TrackLocks.wantingTasks] and retained in
     * active tasks.
     *
     *
     * For every release event, the corresponding waiting tasks are marked as active.
     *
     * @param event the event to update
     * @return the set of active tasks
     */
    override fun updateEvent(event: JmcRuntimeEvent): Set<Long?> {
        val taskId = event.taskId
            ?: // Ignore events without a task ID
            return getActiveTasks()
        activeTasks.putIfAbsent(taskId, Optional.empty())

        val type = event.type

        if (type == JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT) {
            val lock = event.getParam<Any>("instance")
            if (tryLock(taskId, lock!!)) {
                return getActiveTasks()
            }
        } else if (type == JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT) {
            val lock = event.getParam<Any>("instance")
            lockAcquired(taskId, lock!!)
        } else if (type == JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT) {
            val lock = event.getParam<Any>("instance")
            unlock(taskId, lock!!)
        }
        return getActiveTasks()
    }

    protected fun tryLock(taskId: Long, lock: Any): Boolean {
        // Want the lock. Three cases.
        // 1. Current task already has the lock. Ignore.
        val owner = activeTasks[taskId]
        if (owner != null && owner.isPresent) {
            if (owner.get() === lock) {
                LOGGER.debug("Reentrant lock already included by task {}", taskId)
                return true
            }
        }
        // 2. The lock is already acquired by another task. The current task is added to the
        // waiting list.
        if (waitingTasks.containsKey(lock)) {
            LOGGER.debug("Task {} waits for lock {}", taskId, lock.hashCode())
            val tasks = waitingTasks[lock]!!
            tasks.add(taskId)
            activeTasks.remove(taskId)
        } else {
            // 3. The lock is not acquired by any task. The current task is added to the
            // wanting
            // list.
            LOGGER.debug("Task {} wants lock {}", taskId, lock.hashCode())
            wantingTasks.putIfAbsent(lock, HashSet())
            wantingTasks[lock]!!.add(taskId)
        }
        return false
    }

    protected fun lockAcquired(taskId: Long, lock: Any) {
        // The lock is acquired by the current task. Remove it from the wanting list and add
        // the rest to waiting
        // list.
        activeTasks[taskId] = Optional.of(lock)
        waitingTasks.putIfAbsent(lock, HashSet())
        val wantingList: Set<Long>? = wantingTasks[lock]
        if (wantingList != null) {
            for (wantingTask in wantingList) {
                // If the task is not already in the waiting list, add it to the waiting
                // list
                if (wantingTask == taskId) {
                    // Ignore the current task
                    continue
                }
                waitingTasks[lock]!!.add(wantingTask)
                activeTasks.remove(wantingTask)
            }
            wantingTasks.remove(lock)
        }
    }

    protected fun unlock(taskId: Long?, lock: Any) {
        // The lock is released. The waiting tasks are marked as active.
        LOGGER.debug("Task {} released lock {}", taskId, lock.hashCode())
        activeTasks[taskId] = Optional.empty()
        val blockedTasks: Set<Long>? = waitingTasks[lock]
        wantingTasks[lock] = HashSet()
        if (blockedTasks != null) {
            for (blockedTask in blockedTasks) {
                activeTasks[blockedTask] = Optional.empty()
                wantingTasks[lock]!!.add(blockedTask)
            }
            waitingTasks.remove(lock)
        }
    }

    protected fun getActiveTasks(): Set<Long?> {
        return activeTasks.keys
    }

    override fun reset() {
        activeTasks.clear()
        waitingTasks.clear()
        wantingTasks.clear()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(TrackLocks::class.java)
    }
}
