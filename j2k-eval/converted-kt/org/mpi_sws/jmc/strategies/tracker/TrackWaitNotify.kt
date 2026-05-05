package org.mpi_sws.jmc.strategies.tracker

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.JmcObject
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

class TrackWaitNotify : TrackLocks() {
    private val activeTasks: MutableSet<Long?> = HashSet()
    private val trackedTasks: MutableSet<Long?> = HashSet()
    private val waitingTasks =
        HashMap<Int, MutableSet<Long?>>()
    private val availableTasks =
        HashMap<Int, MutableSet<Long?>>()

    override fun updateEvent(event: JmcRuntimeEvent): Set<Long?> {
        super.updateEvent(event)
        if (!trackedTasks.contains(event.taskId)) {
            activeTasks.add(event.taskId)
        }
        trackedTasks.add(event.taskId)
        when (event.type) {
            JmcRuntimeEvent.Type.WAIT_EVENT -> {
                // TODO: need validation to ensure that wait is called on an object that is locked
                // by the current thread. If not, throw an exception saying error in wait/notify
                // usage.
                val `object` = event.getParam<Any>("object")
                val objectId = JmcObject.handleHashCode(`object`)
                val waitingList =
                    waitingTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                waitingList.add(event.taskId)
                waitingTasks[objectId] = waitingList
                activeTasks.remove(event.taskId)
                this.unlock(event.taskId, `object`!!)
            }

            JmcRuntimeEvent.Type.WAKEUP_EVENT -> {
                val objectId = event.getParam<Any>("object").hashCode()
                val waitingList =
                    waitingTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                val availableList =
                    availableTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                if (availableList.isEmpty()) {
                    throw HaltCheckerException.Companion.error("No available tasks to wake up")
                }
                val taskId = event.taskId
                availableList.remove(taskId)
                waitingList.addAll(availableList)
                waitingTasks[objectId] = waitingList
                availableTasks[objectId] = HashSet()
                activeTasks.removeAll(availableList)
            }

            JmcRuntimeEvent.Type.NOTIFY_EVENT -> {
                val objectId = event.getParam<Any>("object").hashCode()
                val waitingList =
                    waitingTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                val availableList =
                    availableTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                availableList.addAll(waitingList)
                availableTasks[objectId] = availableList
                activeTasks.addAll(waitingList)
                waitingList.clear()
                waitingTasks[objectId] = waitingList
            }

            JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT -> {
                val objectId = event.getParam<Any>("object").hashCode()
                val waitingList =
                    waitingTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                activeTasks.addAll(waitingList)
                waitingList.clear()
                val availableList: Set<Long?> =
                    availableTasks.computeIfAbsent(objectId) { k: Int? -> HashSet() }
                activeTasks.addAll(availableList)
                availableTasks[objectId] = HashSet()
                waitingTasks[objectId] = waitingList
            }
        }
        val result: MutableSet<Long?> = HashSet(this.activeTasks)
        result.retainAll(super.getActiveTasks())
        LOGGER.debug("Active tasks after wait/notify: {}", result)
        return result
    }

    override fun reset() {
        super.reset()
        activeTasks.clear()
        trackedTasks.clear()
        waitingTasks.clear()
        availableTasks.clear()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            TrackWaitNotify::class.java
        )
    }
}
