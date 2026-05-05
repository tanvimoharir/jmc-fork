package org.mpi_sws.jmc.util

/** Represents a generic total order relation.  */
interface TotalOrder<T> {
    /**
     * Compares two objects of type T - the current and the other passed as argument.
     *
     * @param other the other object to compare to
     * @return The relation between the two objects.
     */
    @Throws(InvalidComparisonException::class)
    fun compare(other: T): Relation

    /** Represents the relation between two objects.  */
    enum class Relation {
        GT,
        LT,
        EQ,
    }

    /** Represents an exception thrown when an invalid comparison is attempted.  */
    class InvalidComparisonException(message: String?) : Exception(message)
}
