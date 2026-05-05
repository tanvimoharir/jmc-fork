// Create new file: jmc/core/src/main/java/org/mpi_sws/jmc/strategies/tracker/TrackStaticInit.java
package org.mpi_sws.jmc.strategies.tracker

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks static initialization events to ensure proper synchronization
 * of static initializers across iterations.
 */
class TrackStaticInit : Tracker {
    /**
     * Tracks which task is currently executing static initialization.
     * Only one task can execute static init at a time (mimics JVM's synchronized <clinit>).
    </clinit> */
    private val currentStaticInitTask: MutableMap<Long, Int> = ConcurrentHashMap()

    /**
     * Tasks waiting to execute static initialization.
     */
    /**
     * All active tasks.
     */
    private val activeTasks: MutableSet<Long?> =
        ConcurrentHashMap.newKeySet()

    override fun updateEvent(event: JmcRuntimeEvent): Set<Long?> {
        val taskId = event.taskId ?: return getActiveTasks()

        activeTasks.add(taskId)

        val type = event.type

        if (type == JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT) {
            if (currentStaticInitTask.containsKey(taskId)) {
                var count = currentStaticInitTask[taskId]!!
                count++
                currentStaticInitTask[taskId] = count
            } else {
                currentStaticInitTask[taskId] = 1
            }
        } else if (type == JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT) {
            if (!currentStaticInitTask.containsKey(taskId)) {
                // TODO throw an error
            }

            val count = currentStaticInitTask[taskId]!!
            if (count == 1) {
                currentStaticInitTask.remove(taskId)
            } else {
                currentStaticInitTask[taskId] = count - 1
            }
        }
        if (currentStaticInitTask.isEmpty()) {
            return getActiveTasks()
        } else {
            val keySet: Set<*> = currentStaticInitTask.keys
            if (keySet.size != 1) {
                // TODO : Throw and error
            }
            return HashSet(keySet)
        }
    }


    private fun getActiveTasks(): Set<Long?> {
        return HashSet(activeTasks)
    }

    override fun reset() {
        activeTasks.clear()
        //waitingForStaticInit.clear();
        currentStaticInitTask.clear()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            TrackStaticInit::class.java
        )
    }
}
