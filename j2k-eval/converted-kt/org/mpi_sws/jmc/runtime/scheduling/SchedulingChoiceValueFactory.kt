package org.mpi_sws.jmc.runtime.scheduling

import com.google.gson.JsonElement

/**
 * A factory for creating instances of [SchedulingChoiceValue].
 *
 *
 * Accepts adapters for types and invokes the adapters to create values
 */
object SchedulingChoiceValueFactory {
    /**
     * A set of registered adapters for different types of [SchedulingChoiceValue].
     */
    var ADAPTERS: HashMap<String, SchedulingChoiceValueAdapter<out SchedulingChoiceValue>> = HashMap()

    init {
        // Register default adapters for primitive types
        registerAdapter("primitive", PrimitiveValueAdapter())
        registerAdapter("int", PrimitiveValueAdapter())
        registerAdapter("string", PrimitiveValueAdapter())
        registerAdapter("boolean", PrimitiveValueAdapter())
    }

    /**
     * Registers an adapter for a specific type of [SchedulingChoiceValue].
     *
     * @param type the type of the scheduling choice value, used to identify the adapter, should be the same as that returned
     * by [SchedulingChoiceValue.type]
     * @param adapter the adapter instance that converts a JSON object to a [SchedulingChoiceValue]
     */
    fun registerAdapter(
        type: String, adapter: SchedulingChoiceValueAdapter<out SchedulingChoiceValue>
    ) {
        ADAPTERS[type] = adapter
    }

    @Throws(IllegalArgumentException::class)
    fun create(type: String, valueObject: JsonElement?): SchedulingChoiceValue? {
        require(ADAPTERS.containsKey(type)) { "No adapter registered for type: $type" }

        val adapter = ADAPTERS[type]!!
        return adapter.fromJson(valueObject!!)
    }

    fun containsType(type: String): Boolean {
        return ADAPTERS.containsKey(type)
    }
}
