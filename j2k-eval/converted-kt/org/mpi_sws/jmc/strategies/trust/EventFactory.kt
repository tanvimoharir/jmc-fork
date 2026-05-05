package org.mpi_sws.jmc.strategies.trust

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import java.util.List

object EventFactory {
    /**
     * Creates a new event mapping the runtime event to the trust event.
     *
     *
     * Returns empty list if event not supported.
     *
     * @param runtimeEvent The runtime event.
     * @return A list of trust events (empty if not supported).
     */
    fun fromRuntimeEvent(runtimeEvent: JmcRuntimeEvent): MutableList<Event> {
        // Note: Subtract 1 from the task id since the runtime is 1-indexed
        when (runtimeEvent.type) {
            JmcRuntimeEvent.Type.START_EVENT -> {
                // Update EventUtils::isThreadStart if anything changes here
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        LocationStore.Companion.ThreadLocation,
                        Event.Type.NOOP
                    )
                event.setAttribute("thread_start", true)
                val startedBy = runtimeEvent.getParam<Long>("startedBy")
                event.setAttribute("started_by", startedBy!! - 1)
                return List.of(event)
            }

            JmcRuntimeEvent.Type.WRITE_EVENT -> {
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        Location.Companion.fromRuntimeEvent(runtimeEvent).hashCode(),
                        Event.Type.WRITE
                    )
                return List.of(event)
            }

            JmcRuntimeEvent.Type.READ_EVENT -> {
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        Location.Companion.fromRuntimeEvent(runtimeEvent).hashCode(),
                        Event.Type.READ
                    )
                return List.of(event)
            }

            JmcRuntimeEvent.Type.FINISH_EVENT -> {
                // Update EventUtils::isThreadFinish if anything changes here
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        LocationStore.Companion.ThreadLocation,
                        Event.Type.NOOP
                    )
                event.setAttribute("thread_finish", true)
                return List.of(event)
            }

            JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT -> {
                // Update EventUtils::isThreadJoin if anything changes here
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        LocationStore.Companion.ThreadLocation,
                        Event.Type.NOOP
                    )
                val joinedTask = runtimeEvent.getParam<Long>("joinedTask")
                event.setAttribute("thread_join", true)
                event.setAttribute("joined_task", joinedTask!! - 1)
                return List.of(event)
            }

            JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT -> {
                val event1 =
                    Event(
                        runtimeEvent.taskId - 1,
                        Location.Companion.fromRuntimeEvent(runtimeEvent).hashCode(),
                        Event.Type.READ_EX
                    )
                event1.setAttribute("lock_acquire", true)
                val event2 =
                    Event(
                        runtimeEvent.taskId - 1,
                        Location.Companion.fromRuntimeEvent(runtimeEvent).hashCode(),
                        Event.Type.WRITE_EX
                    )
                event2.setAttribute("lock_acquire", true)
                return List.of(event1, event2)
            }

            JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT -> {
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        Location.Companion.fromRuntimeEvent(runtimeEvent).hashCode(),
                        Event.Type.NOOP
                    )
                event.setAttribute("lock_acquired", true)
                return List.of(event)
            }

            JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT -> {
                val event =
                    Event(
                        runtimeEvent.taskId - 1,
                        Location.Companion.fromRuntimeEvent(runtimeEvent).hashCode(),
                        Event.Type.WRITE
                    )
                event.setAttribute("lock_release", true)
                return List.of(event)
            }

            JmcRuntimeEvent.Type.ASSUME_EVENT -> {
                val event = Event(runtimeEvent.taskId - 1, null, Event.Type.ASSUME)
                val result = runtimeEvent.getParam<Boolean>("result")!!
                event.setAttribute("result", result)
                return List.of(event)
            }
        }

        return ArrayList()
    }
}
