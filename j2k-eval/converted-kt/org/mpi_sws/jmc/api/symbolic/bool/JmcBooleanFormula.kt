package org.mpi_sws.jmc.api.symbolic.bool

import org.mpi_sws.jmc.api.symbolic.InstructionType
import org.mpi_sws.jmc.api.symbolic.JmcConcreteFormula
import org.mpi_sws.jmc.api.symbolic.SymbolicOperand
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula

/**
 * JmcBooleanFormula represents a symbolic boolean formula used in symbolic execution.
 * It encapsulates a BooleanFormula along with mappings for integer and boolean variables.
 */
class JmcBooleanFormula : SymbolicOperand {
    /**
     * Gets the underlying BooleanFormula.
     *
     * @return the underlying BooleanFormula
     */
    /**
     * Sets the underlying BooleanFormula.
     *
     * @param formula the BooleanFormula to set
     */
    /**
     * The underlying BooleanFormula representing the symbolic expression.
     */
    var formula: BooleanFormula? = null

    /**
     * The JmcConcreteFormula used for concrete evaluation of the symbolic formula.
     */
    private val jmcConcreteFormula = JmcConcreteFormula()

    /**
     * Gets the mapping of integer variables.
     *
     * @return the mapping of integer variables
     */
    /**
     * Sets the mapping of integer variables.
     *
     * @param integerVariableMap the mapping to set
     */
    /**
     * A mapping of integer variable names to their corresponding IntegerFormula representations.
     */
    var integerVariableMap: MutableMap<String?, IntegerFormula?>? = HashMap()

    /**
     * Gets the mapping of boolean variables.
     *
     * @return the mapping of boolean variables
     */
    /**
     * Sets the mapping of boolean variables.
     *
     * @param booleanVariableMap the mapping to set
     */
    /**
     * A mapping of boolean variable names to their corresponding BooleanFormula representations.
     */
    var booleanVariableMap: MutableMap<String?, BooleanFormula?>? = HashMap()

    /**
     * Adds an integer variable to the mapping.
     *
     * @param name    the name of the variable
     * @param formula the IntegerFormula representing the variable
     */
    fun addIntegerVariable(name: String?, formula: IntegerFormula?) {
        integerVariableMap!![name] = formula
    }

    /**
     * Gets the integer variable corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the integer variable
     */
    fun getIntegerVariable(name: String?): IntegerFormula? {
        return integerVariableMap!![name]
    }

    /**
     * Adds a boolean variable to the mapping.
     *
     * @param name    the name of the variable
     * @param formula the BooleanFormula representing the variable
     */
    fun addBooleanVariable(name: String?, formula: BooleanFormula?) {
        booleanVariableMap!![name] = formula
    }

    /**
     * Gets the boolean variable corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the boolean variable
     */
    fun getBooleanVariable(name: String?): BooleanFormula? {
        return booleanVariableMap!![name]
    }

    /**
     * Checks if this formula is dependent on another formula by checking for shared variables.
     *
     * @param operation the other JmcBooleanFormula to compare with
     * @return true if there is a dependency, false otherwise
     */
    fun isFormulaDependent(operation: JmcBooleanFormula): Boolean {
        for ((key, value) in integerVariableMap!!) {
            val valueInFormula2 = operation.integerVariableMap!![key]
            if (valueInFormula2 != null && valueInFormula2 == value) {
                return true
            }
        }
        for ((key, value) in booleanVariableMap!!) {
            val valueInFormula2 = operation.booleanVariableMap!![key]
            if (valueInFormula2 != null && valueInFormula2 == value) {
                return true
            }
        }
        return false
    }

    /**
     * Sets the JmcConcreteFormula using left and right operands and an operator.
     *
     * @param left     the left operand
     * @param right    the right operand
     * @param operator the operator
     */
    fun setJmcFormula(left: SymbolicOperand?, right: SymbolicOperand?, operator: InstructionType?) {
        jmcConcreteFormula.setLeftOperand(left)
        jmcConcreteFormula.setRightOperand(right)
        jmcConcreteFormula.setOperator(operator)
    }

    /**
     * Sets the JmcConcreteFormula using a list of operands and an operator.
     *
     * @param operands the list of operands
     * @param operator the operator
     */
    fun setJmcFormula(operands: List<SymbolicOperand?>?, operator: InstructionType?) {
        jmcConcreteFormula.setOperands(operands)
        jmcConcreteFormula.setOperator(operator)
    }

    /**
     * Evaluates the concrete value of the symbolic boolean formula.
     *
     * @return the concrete boolean value
     */
    fun concreteEvaluation(): Boolean {
        val result = jmcConcreteFormula.evaluate()
        return result
    }
}
