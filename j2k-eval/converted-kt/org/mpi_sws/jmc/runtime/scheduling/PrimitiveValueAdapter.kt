package org.mpi_sws.jmc.runtime.scheduling

import com.google.gson.JsonElement

/**
 * Represents a primitive (Number|String|Boolean) value used in scheduling choices.
 *
 *
 * This class extends [SchedulingChoiceValue] to provide a specific implementation
 * for integer, string or boolean values, allowing them to be serialized to JSON and identified by type.
 */
class PrimitiveValueAdapter : SchedulingChoiceValueAdapter<PrimitiveValue>() {
    public override fun fromJson(json: JsonElement): PrimitiveValue {
        if (json.isJsonPrimitive) {
            val primitive = json.asJsonPrimitive
            return if (primitive.isString) {
                PrimitiveValue(primitive.asString)
            } else if (primitive.isNumber) {
                PrimitiveValue(primitive.asNumber)
            } else if (primitive.isBoolean) {
                PrimitiveValue(primitive.asBoolean)
            } else {
                throw IllegalArgumentException("Unsupported JSON primitive type: $primitive")
            }
        } else {
            throw IllegalArgumentException("Expected a JSON primitive, but got: $json")
        }
    }
}
