package org.mpi_sws.jmc.strategies.trust

import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice

/**
 * Represents a scheduling choice with an optional location.
 */
@JvmRecord
data class SchedulingChoiceWrapper(choice: SchedulingChoice<*>, location: Int?) {
    /**
     * Creates a new scheduling choice with the given choice and empty location.
     *
     * @param choice The choice.
     */
    constructor(choice: SchedulingChoice<*>) : this(choice, null)

    /**
     * Returns whether the scheduling choice has a location.
     *
     * @return Whether the scheduling choice has a location.
     */
    fun hasLocation(): Boolean {
        return location != null
    }

    val choice: SchedulingChoice<*> = choice
    val location: Int? = location
}
