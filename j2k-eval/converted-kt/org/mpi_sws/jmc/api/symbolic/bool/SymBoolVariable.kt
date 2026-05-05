package org.mpi_sws.jmc.api.symbolic.bool

import org.sosy_lab.java_smt.api.BooleanFormula
import java.util.*

/**
 * Class representing a symbolic boolean variable with an associated formula and value.
 */
class SymBoolVariable(
    /**
     * Sets the BooleanFormula of the symbolic variable.
     *
     * @param var the BooleanFormula to set
     */
    /**
     * The BooleanFormula representing the symbolic variable
     */
    var `var`: BooleanFormula
) {
    /**
     * Returns the BooleanFormula of the symbolic variable.
     *
     * @return the BooleanFormula representing the symbolic variable
     */

    /**
     * Returns the concrete boolean value of the symbolic variable.
     *
     * @return the boolean value associated with the symbolic variable
     */
    /**
     * Sets the concrete boolean value of the symbolic variable.
     *
     * @param value the boolean value to set
     */
    /**
     * The concrete boolean value associated with the symbolic variable
     */
    var value: Boolean

    /**
     * Constructor to initialize the symbolic boolean variable with a formula.
     * The value is randomly assigned as true or false.
     *
     * @param var the BooleanFormula representing the symbolic variable
     */
    init {
        val random = Random()
        this.value = random.nextBoolean()
    }

    /**
     * Creates a deep copy of the SymBoolVariable.
     *
     * @return a new instance of SymBoolVariable with the same formula and value
     */
    override fun clone(): SymBoolVariable {
        val copy = SymBoolVariable(this.`var`)
        copy.value = value
        return copy
    }
}
