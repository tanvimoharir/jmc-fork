package org.mpi_sws.jmc.api.symbolic.array

/**
 * The [AbstractArray] class is used to represent an array value that can be either symbolic or concrete.
 */
abstract class AbstractArray {
    /**
     * [.type] is used to store the type of the array.
     */
    var type: Type? = null

    /**
     * Clones the abstract array.
     */
    abstract override fun clone(): AbstractArray?

    /**
     * Reads the value of the abstract array variable.
     *
     * @return the value of the abstract array variable.
     */
    abstract fun read(): AbstractArray?

    /**
     * Writes the value to the abstract array variable with an abstract array value.
     *
     * @param value the value to be written.
     */
    abstract fun write(value: AbstractArray?)

    /**
     * Type of the array.
     */
    enum class Type {
        INT,
        BOOL,
        STRING,
        RATIONAL,
        REGEX
    }
}
