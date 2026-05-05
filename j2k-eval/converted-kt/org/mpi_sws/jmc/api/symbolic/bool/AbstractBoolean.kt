package org.mpi_sws.jmc.api.symbolic.bool

import org.mpi_sws.jmc.api.symbolic.SymbolicOperand

/**
 * The [AbstractBoolean] class is used to represent a boolean value that can be either symbolic or concrete.
 */
abstract class AbstractBoolean : SymbolicOperand {
    /**
     * Gets the value of the boolean.
     *
     * @return the value of the boolean.
     */
    /**
     * Sets the value of the boolean.
     *
     * @param value the value to be set.
     */
    /**
     * [.value] is used to store the value of the boolean.
     */
    var value: Boolean = false

    /**
     * Makes a deep copy of the boolean.
     *
     * @return a deep copy of the boolean.
     */
    abstract override fun clone(): AbstractBoolean

    /**
     * Reads the value of the abstract boolean variable.
     *
     * @return the value of the abstract boolean variable.
     */
    abstract fun read(): AbstractBoolean

    /**
     * Writes the value of the abstract boolean variable with an abstract boolean value.
     *
     * @param value the value to be written.
     */
    abstract fun write(value: SymbolicBoolean)

    /**
     * Writes the value of the abstract boolean variable with a boolean formula value.
     *
     * @param value the value to be written.
     */
    abstract fun write(value: JmcBooleanFormula)
}
