package org.mpi_sws.jmc.api.symbolic.integer

import org.mpi_sws.jmc.api.symbolic.SymbolicOperand

/**
 * The [AbstractInteger] class is used to represent an integer value that can be either symbolic or concrete.
 */
abstract class AbstractInteger : SymbolicOperand {
    /**
     * [.concreteValue] is used to store the value of the integer.
     */
    /**
     * Sets the concrete value.
     *
     * @param concreteValue the value to be set.
     */
    /**
     * [.concreteValue] is used to store the concrete value of the integer.
     */
    var value: Int = 0

    /**
     * Reads the value of the abstract integer variable.
     *
     * @return the value of the abstract integer variable.
     */
    abstract fun read(): AbstractInteger

    /**
     * Writes the value to the abstract integer variable with an abstract integer value.
     *
     * @param value the value to be written.
     */
    abstract fun write(value: AbstractInteger)

    /**
     * Writes the value of the abstract integer variable with an arithmetic statement value.
     *
     * @param value the value to be written.
     */
    abstract fun write(value: ArithmeticStatement)

    /**
     * Makes a deep copy of the abstract integer.
     *
     * @return a deep copy of the abstract integer.
     */
    abstract override fun clone(): AbstractInteger
}
