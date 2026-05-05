package org.mpi_sws.jmc.strategies.trust

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Represents an event object used by the algorithm.
 */
class Event(
    taskId: Long?,
    /**
     * Returns the location of the event.
     *
     * @return The location of the event.
     */
    var location: Int?,
    /**
     * Returns the type of the event.
     *
     * @return The type of the event.
     */
    val type: Type
) {
    val key: Key = Key(taskId)
    private val attributes: MutableMap<String, Any> = HashMap()

    /**
     * Creates a clone of the event.
     *
     * @return A clone of the event.
     */
    override fun clone(): Event {
        val e = Event(key.taskId, location, type)
        e.key.timestamp = key.timestamp
        e.key.toStamp = key().toStamp
        e.attributes.putAll(attributes)
        return e
    }

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("key", key.toJson())
        if (location != null) {
            json.addProperty("location", location)
        }
        json.addProperty("type", type.toString())
        val attributesJson = JsonObject()
        for ((key1, value) in attributes) {
            attributesJson.addProperty(key1, value.toString())
        }
        json.add("attributes", attributesJson)
        return json
    }

    fun toJsonIgnoreLocation(): JsonElement {
        val json = JsonObject()
        json.add("key", key.toJson())
        json.addProperty("type", type.toString())
        // Sort the attributes by key
        /*JsonObject attributesJson = new JsonObject();
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> attributesJson.addProperty(entry.getKey(), entry.getValue().toString()));
        json.add("attributes", attributesJson);*/
        return json
    }

    /**
     * Returns the attribute of the event with the given key in the type T.
     *
     * @param key The key of the attribute.
     * @param <T> The type of the attribute.
     * @return The attribute with the given key.
    </T> */
    fun <T> getAttribute(key: String): T? {
        if (!attributes.containsKey(key)) {
            return null
        }
        return attributes[key] as T?
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj !is Event) {
            return false
        }
        return this.key == obj.key && this.type == obj.type
    }

    /**
     * Sets the attribute of the event with the given key and value.
     *
     * @param key   The key of the attribute.
     * @param value The value of the attribute.
     */
    fun setAttribute(key: String, value: Any) {
        attributes[key] = value
    }

    /**
     * The key of the event.
     *
     * @return The key of the event.
     */
    fun key(): Key {
        return key
    }

    val taskId: Long?
        /**
         * Returns the task ID of the event.
         *
         * @return The task ID.
         */
        get() = key.taskId

    var timestamp: Int?
        /**
         * Returns the timestamp of the event.
         *
         * @return The timestamp of the event.
         */
        get() = key.timestamp
        /**
         * Sets the timestamp of the event.
         *
         * @param timestamp The timestamp of the event.
         */
        set(timestamp) {
            key.timestamp = timestamp
        }

    var toStamp: Int?
        /**
         * Returns the total order timestamp of the event.
         *
         * @return The total order timestamp of the event.
         */
        get() = key.toStamp
        /**
         * Sets the total order timestamp of the event.
         *
         * @param toStamp The total order timestamp of the event.
         */
        set(toStamp) {
            key.toStamp = toStamp
        }

    fun hasAttribute(key: String): Boolean {
        return attributes.containsKey(key)
    }

    /**
     * Represents the type of the event according to the algorithm.
     */
    enum class Type {
        ASSUME,
        READ,
        READ_EX,
        BLOCK,
        INIT,
        WRITE,
        WRITE_EX,
        END,
        ERROR,
        LOCK_ACQUIRE,
        LOCK_RELEASE,
        NOOP,
    }

    /**
     * Unique key for the event.
     */
    class Key {
        // The task to which the event belongs to
        val taskId: Long?

        // The index of the event in that task. Assuming deterministic executions here.
        var timestamp: Int?

        // The index of the event in the total order
        var toStamp: Int?

        /**
         * Creates a new key with the given task ID and timestamp.
         *
         * @param taskId The task ID.
         */
        constructor(taskId: Long?) {
            this.taskId = taskId
            this.timestamp = null
            this.toStamp = null
        }

        constructor(other: Key) {
            this.taskId = other.taskId
            this.timestamp = other.timestamp
            this.toStamp = other.toStamp
        }

        override fun clone(): Key {
            return Key(this)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val key = o as Key
            if (taskId == null && timestamp == null) {
                return key.taskId == null && key.timestamp == null
            }

            if (taskId != key.taskId) {
                return false
            }
            return timestamp == key.timestamp
        }

        override fun hashCode(): Int {
            if (taskId == null && timestamp == null) {
                return 0
            }
            var result = taskId.hashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }

        override fun toString(): String {
            return "{$taskId, $timestamp}"
        }

        fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("taskId", taskId)
            json.addProperty("timestamp", timestamp)
            return json
        }

        fun compareTo(key: Key): Int {
            if (taskId == null && key.taskId == null) {
                return 0
            }
            if (taskId == null) {
                return -1
            }
            if (key.taskId == null) {
                return 1
            }
            val cmp = taskId.compareTo(key.taskId)
            if (cmp != 0) {
                return cmp
            }
            if (timestamp == null && key.timestamp == null) {
                return 0
            }
            if (timestamp == null) {
                return -1
            }
            if (key.timestamp == null) {
                return 1
            }
            return timestamp!!.compareTo(key.timestamp!!)
        }
    }

    val isInit: Boolean
        /**
         * Returns true if the event is an init event.
         *
         * @return True if the event is an init event.
         */
        get() = type == Type.INIT

    val isRead: Boolean
        get() = type == Type.READ

    val isWrite: Boolean
        get() = type == Type.WRITE

    val isReadEx: Boolean
        get() = type == Type.READ_EX

    val isWriteEx: Boolean
        get() = type == Type.WRITE_EX

    override fun toString(): String {
        return "Event($type)$key"
    }

    fun toVerboseString(): String {
        val sb = StringBuilder()
        sb.append("Event(").append(type.toString()).append(") key: ").append(key)
        if (location != null) {
            sb.append(", location: ").append(location)
        }
        if (!attributes.isEmpty()) {
            sb.append(", attributes: ").append(attributes)
        }
        return sb.toString()
    }

    /**
     * A generic event predicate.
     */
    fun interface EventPredicate {
        /**
         * Tests the event.
         *
         * @param event The event to test.
         * @return True if the event passes the test, false otherwise.
         */
        fun test(event: Event?): Boolean
    }

    companion object {
        /**
         * Creates an init event.
         *
         * @return An init event [Event].
         */
        fun init(): Event {
            return Event(null, null, Type.INIT)
        }

        /**
         * Creates the bottom event to indicate end of the execution.
         *
         * @return An end event [Event].
         */
        fun end(): Event {
            return Event(null, null, Type.END)
        }

        /**
         * Creates a new error event with the given message.
         *
         * @param message The message of the error.
         * @return An error event [Event].
         */
        fun error(message: String): Event {
            val e = Event(null, null, Type.ERROR)
            e.setAttribute("message", message)
            return e
        }
    }
}
