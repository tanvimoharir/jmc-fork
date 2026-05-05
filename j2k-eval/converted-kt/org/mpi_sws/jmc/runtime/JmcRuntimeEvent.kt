package org.mpi_sws.jmc.runtime

import org.mpi_sws.jmc.api.JmcObject

/**
 * Represents an event that occurs during the execution of a program.
 */
class JmcRuntimeEvent {
    /**
     * Returns the type of the event.
     *
     * @return the type of the event
     */
    /**
     * Sets the type of the event.
     *
     * @param type the type of the event
     */
    // The type of the event
    var type: Type?
    /**
     * Returns the ID of the task that generated the event.
     *
     * @return the ID of the task that generated the event
     */
    /**
     * Sets the ID of the task that generated the event.
     *
     * @param taskId the ID of the task that generated the event
     */
    // The ID of the task that generated the event
    @JvmField
    var taskId: Long?

    // The parameters of the event
    private var params: MutableMap<String, Any?>?

    /**
     * Constructs a new runtime event with the specified type, task ID, and parameters.
     *
     * @param type   the type of the event
     * @param taskId the ID of the task that generated the event
     * @param params the parameters of the event
     */
    constructor(type: Type?, taskId: Long?, params: MutableMap<String, Any?>?) {
        this.type = type
        this.taskId = taskId
        this.params = params
    }

    /**
     * Constructs a new runtime event with the specified type and task ID.
     *
     *
     * The parameters of the event are initialized to an empty map.
     *
     * @param type   the type of the event
     * @param taskId the ID of the task that generated the event
     */
    constructor(type: Type?, taskId: Long?) {
        this.type = type
        this.taskId = taskId
        this.params = HashMap()
    }

    /**
     * Returns the parameters of the event.
     *
     * @return the parameters of the event
     */
    fun getParams(): Map<String, Any?>? {
        return params
    }

    /**
     * Sets the parameters of the event.
     *
     * @param params the parameters of the event
     */
    fun setParams(params: MutableMap<String, Any?>?) {
        this.params = params
    }

    /**
     * Sets the value of the parameter with the specified key.
     *
     * @param key   the key of the parameter
     * @param value the value of the parameter
     */
    fun setParam(key: String, value: Any?) {
        params!![key] = value
    }

    /**
     * Returns the value of the parameter with the specified key as an object of the specified
     * class.
     *
     * @param key the key of the parameter
     * @return the value of the parameter as an object of the specified class. Can throw an
     * exception when casting.
     */
    fun <T> getParam(key: String): T? {
        return params!![key] as T?
    }

    private fun paramToString(params: Map<String, Any?>?): String {
        if (params == null) return ""
        val sb = StringBuilder()
        for ((key, o) in params) {
            if (!sb.isEmpty()) {
                sb.append(", ")
            }
            if (key == "instance") {
                sb.append(JmcObject.handleToString(o))
            } else {
                sb.append(o)
            }
        }
        return sb.toString()
    }

    override fun toString(): String {
        return "RuntimeEvent{" + "type=" + type + ", taskId=" + taskId + ", params=" + paramToString(params) + '}'
    }

    /**
     * A builder for constructing a [JmcRuntimeEvent] object.
     */
    class Builder {
        private var type: Type? = null
        private var taskId: Long? = null
        private var params: MutableMap<String, Any?>? = null

        /**
         * Sets the type of the event.
         */
        fun type(type: Type?): Builder {
            this.type = type
            return this
        }

        /**
         * Sets the ID of the task that generated the event.
         */
        fun taskId(taskId: Long?): Builder {
            this.taskId = taskId
            return this
        }

        /**
         * Sets the parameters of the event.
         */
        fun params(params: MutableMap<String, Any?>?): Builder {
            this.params = params
            return this
        }

        /**
         * Adds a parameter to the event.
         */
        fun param(key: String, value: Any?): Builder {
            if (params == null) {
                params = HashMap()
            }
            params!![key] = value
            return this
        }

        /**
         * Builds the [JmcRuntimeEvent] object.
         */
        fun build(): JmcRuntimeEvent {
            return JmcRuntimeEvent(type, taskId, params)
        }
    }

    /**
     * Enum representing the different types of runtime events that can occur.
     *
     *
     * Each event type corresponds to a specific action or occurrence in the program's execution,
     * such as thread creation, locking, reading, writing, and more.
     */
    enum class Type {
        // Thread creation and termination events
        START_EVENT,
        FINISH_EVENT,
        HALT_EVENT,

        // Thread join events
        JOIN_REQUEST_EVENT,
        JOIN_COMPLETE_EVENT,

        // Thread park and un-park events
        PARK_EVENT,
        UNPARK_EVENT,

        // Monitor events
        ENTER_MONITOR_EVENT,
        EXIT_MONITOR_EVENT,

        // Lock events
        LOCK_ACQUIRE_EVENT,
        LOCK_ACQUIRED_EVENT,
        LOCK_RELEASE_EVENT,

        // Read and write events
        READ_EVENT,
        WRITE_EVENT,
        CAS_EVENT,

        // Message sending and receiving events
        SEND_EVENT,
        RECV_EVENT,
        RECV_BLOCKING_EVENT,

        // Symbolic arithmetic execution
        SYMB_ARTH_EVENT,

        // Related to futures
        FUTURE_START_EVENT,
        GET_FUTURE_EVENT,
        FUTURE_EXCEPTION_EVENT,
        FUTURE_SET_EVENT,

        // TODO: explain
        TAKE_WORK_QUEUE,
        CON_ASSUME_EVENT,
        SYM_ASSUME_EVENT,
        ASSUME_BLOCKED_EVENT,

        WAIT_EVENT,
        WAKEUP_EVENT,
        NOTIFY_EVENT,
        NOTIFY_ALL_EVENT,

        // Task events when using an executor
        TASK_ASSIGNED_EVENT,
        THREAD_POOL_CREATED,
        TASK_CREATED_EVENT,

        // Reactive Event (Events that require information from the strategy, upto the strategy to
        // deal with it)
        REACTIVE_EVENT_RANDOM_VALUE,

        // Related to assertions in the code
        ASSUME_EVENT,
        ASSERT_EVENT,

        SYMB_OP_EVENT,
        SYMB_ASSUME_EVENT,
        SYMB_ASSERT_EVENT,

        // Static Initialization Event
        START_STATIC_INIT_EVENT,
        END_STATIC_INIT_EVENT,

        //Executor tracking event
        EXECUTOR_SHUTDOWN_EVENT,
    }
}
