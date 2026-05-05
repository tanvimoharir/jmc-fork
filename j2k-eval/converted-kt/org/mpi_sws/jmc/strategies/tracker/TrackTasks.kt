package org.mpi_sws.jmc.strategies.tracker

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the tasks start finish and join request events.
 */
class TrackTasks : Tracker {
    private val activeTasks: MutableSet<Long?> = HashSet()
    private val waitingTasks: MutableMap<Long?, MutableSet<Long?>> =
        ConcurrentHashMap()

    private val completedTasks: MutableSet<Long?> = HashSet()
    private val tasksLock = Any()

    override fun updateEvent(event: JmcRuntimeEvent): Set<Long?> {
        if (event.type == JmcRuntimeEvent.Type.START_EVENT) {
            synchronized(tasksLock) {
                activeTasks.add(event.taskId)
            }
        } else if (event.type == JmcRuntimeEvent.Type.FINISH_EVENT) {
            val eventTask = event.taskId
            synchronized(tasksLock) {
                activeTasks.remove(eventTask)
                completedTasks.add(eventTask)
                val waitingList: Set<Long?>? = waitingTasks[eventTask]
                if (waitingList != null) {
                    activeTasks.addAll(waitingList)
                    waitingTasks.remove(eventTask)
                }
            }
        } else if (event.type == JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT) {
            val requestingTask = event.taskId
            val requestedTask = event.getParam<Long>("waitingTask")

            synchronized(tasksLock) {
                // If the requested task is active or not completed, mark the requesting task as
                // waiting
                if (activeTasks.contains(requestedTask)
                    || !completedTasks.contains(requestedTask)
                ) {
                    val waitingList =
                        waitingTasks.computeIfAbsent(requestedTask) { k: Long? -> HashSet() }
                    waitingList.add(requestingTask)
                    activeTasks.remove(requestingTask)
                }
            }
        }
        return getActiveTasks()
    }

    private fun getActiveTasks(): Set<Long?> {
        synchronized(tasksLock) {
            return HashSet(activeTasks)
        }
    }

    override fun reset() {
        synchronized(tasksLock) {
            activeTasks.clear()
            waitingTasks.clear()
            completedTasks.clear()
        }
    }
}
