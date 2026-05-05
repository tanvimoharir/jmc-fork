package org.mpi_sws.jmc.runtime.scheduling

import com.google.gson.JsonElement

/**
 * A value that the strategy uses to communicate with the runtime yields.
 *
 *
 * The abstraction helps record the values when a buggy trace is found.
 *
 *
 * For each [SchedulingChoiceValue], there should be a
 */
abstract class SchedulingChoiceValue {
    /**
     * Converts this value to a JSON object.
     *
     * @return the JSON representation of this value
     */
    abstract fun toJson(): JsonElement

    /**
     * Returns the type of this value.
     *
     *
     * Default types (int|string) have inbuilt adapters.
     *
     *
     * This is used to identify the type of value in the JSON representation.
     *
     * @return the type of this value
     */
    abstract fun type(): String
}
