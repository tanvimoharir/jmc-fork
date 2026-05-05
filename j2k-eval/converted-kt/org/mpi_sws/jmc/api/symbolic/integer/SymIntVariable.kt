package org.mpi_sws.jmc.api.symbolic.integer

import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import java.util.*

class SymIntVariable(
    /**
     * Sets the symbolic integer formula.
     *
     * @param var the symbolic integer formula to set
     */
    /**
     * The symbolic variable representing an integer
     */
    var `var`: IntegerFormula
) {
    /**
     * Gets the symbolic integer formula.
     *
     * @return the symbolic integer formula
     */

    /**
     * Gets the concrete value assigned to the symbolic integer variable.
     *
     * @return the concrete value
     */
    /**
     * Sets the concrete value assigned to the symbolic integer variable.
     *
     * @param value the concrete value to set
     */
    /**
     * The concrete value assigned to the symbolic integer variable
     */
    var value: Int

    /**
     * Constructor to create a symbolic integer variable with a given symbolic formula.
     *
     * @param var the symbolic integer formula
     */
    init {
        val random = Random()
        this.value = random.nextInt()
    }

    /**
     * Creates a deep copy of the SymIntVariable.
     *
     * @return a deep copy of the SymIntVariable
     */
    override fun clone(): SymIntVariable {
        val copy = SymIntVariable(this.`var`)
        copy.value = value
        return copy
    }
}
