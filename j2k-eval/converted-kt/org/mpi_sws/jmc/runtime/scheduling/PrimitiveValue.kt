package org.mpi_sws.jmc.runtime.scheduling

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

/**
 * Represents a primitive value that is returned by a strategy.
 *
 *
 * A concrete implementation of the [SchedulingChoiceValue] class
 *
 *
 * Primitive values are one of int, string or boolean.
 */
class PrimitiveValue(private val value: Any) : SchedulingChoiceValue() {
    fun asInteger(): Int {
        return if (value is Int) {
            value
        } else if (value is Number) {
            value.toInt()
        } else {
            throw ClassCastException("Cannot cast " + value.javaClass + " to Integer")
        }
    }

    fun asString(): String {
        if (value is String) {
            return value
        } else {
            throw ClassCastException("Cannot cast " + value.javaClass + " to String")
        }
    }

    fun asBoolean(): Boolean {
        if (value is Boolean) {
            return value
        } else {
            throw ClassCastException("Cannot cast " + value.javaClass + " to Boolean")
        }
    }

    override fun toJson(): JsonElement {
        return if (value is String) {
            JsonPrimitive(value)
        } else if (value is Number) {
            JsonPrimitive(value)
        } else if (value is Boolean) {
            JsonPrimitive(value)
        } else {
            throw IllegalArgumentException("Unsupported primitive type: " + value.javaClass)
        }
    }

    override fun type(): String {
        return "primitive"
    }
}
