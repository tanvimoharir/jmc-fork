package org.mpi_sws.jmc.runtime.scheduling

import com.google.gson.JsonElement

/**
 * An adapter to convert a JSON object to a [SchedulingChoiceValue].
 *
 *
 * Each [SchedulingChoiceValue] should have a corresponding adapter.
 *
 * @param <T> the type of [SchedulingChoiceValue] this adapter converts to
</T> */
abstract class SchedulingChoiceValueAdapter<T : SchedulingChoiceValue?> {
    /**
     * Converts a JSON object to a [SchedulingChoiceValue].
     *
     * @param json the JSON object to convert
     * @return the converted [SchedulingChoiceValue]
     */
    abstract fun fromJson(json: JsonElement): T
}
