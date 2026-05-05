package org.mpi_sws.jmc.util

/** Represents a generic partial order relation.  */
interface PartialOrder<T> {
    /**
     * Compares two objects of type T - the current instance and the other object.
     *
     * @param other the other object to compare to.
     * @return The relation between the two objects.
     */
    fun compare(other: T): Relation

    /** Represents the relation between two objects.  */
    enum class Relation {
        GT,
        LT,
        EQ,
        UNRELATED
    }
}
