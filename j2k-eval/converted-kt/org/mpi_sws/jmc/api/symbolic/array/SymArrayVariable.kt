package org.mpi_sws.jmc.api.symbolic.array

import org.sosy_lab.java_smt.api.ArrayFormula
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula

// TODO : Complete the implementation
/**
 * The [SymArrayVariable] class is used to represent a symbolic array variable.
 */
class SymArrayVariable
/**
 * Constructor to initialize the symbolic array variable.
 *
 * @param var the symbolic array variable.
 */(
    /**
     * The symbolic array variable. This is represented using an [ArrayFormula] from the JavaSMT library.
     */
    private val `var`: ArrayFormula<IntegerFormula, IntegerFormula>
) {
    /**
     * Clones the symbolic array variable.
     *
     * @return a clone of the symbolic array variable.
     */
    override fun clone(): SymArrayVariable {
        val copy = SymArrayVariable(this.`var`)
        return copy
    }
}
