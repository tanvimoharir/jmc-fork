package org.mpi_sws.jmc.api.symbolic.bool

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils

/**
 * The [SymbolicBoolean] class represents a symbolic boolean variable.
 * It extends the [AbstractBoolean] class and provides methods to read and write
 * the symbolic boolean value, as well as to assign expressions or other symbolic booleans.
 */
class SymbolicBoolean : AbstractBoolean {
    /**
     * Gets the name of the symbolic boolean variable.
     *
     * @return the name of the symbolic boolean variable
     */
    /**
     * Sets the name of the symbolic boolean variable.
     *
     * @param name the name to set
     */
    /**
     * The name of the symbolic boolean variable.
     */
    var name: String

    /**
     * Gets the symbolic boolean expression associated with this variable.
     *
     * @return the symbolic boolean expression
     */
    /**
     * Sets the symbolic boolean expression associated with this variable.
     *
     * @param eval the symbolic boolean expression to set
     */
    /**
     * The symbolic boolean expression associated with this variable.
     */
    var eval: JmcBooleanFormula? = null

    /**
     * The concrete boolean value of the symbolic boolean variable.
     */
    override val value: Boolean = false

    /**
     * Default constructor that initializes the symbolic boolean variable with a unique name.
     */
    private constructor() {
        val parts = toString().split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        this.name = "SymbolicBoolean@" + parts[parts.size - 1]
        write()
    }

    /**
     * Constructor that initializes the symbolic boolean variable with a given name and value.
     *
     * @param name  the name of the symbolic boolean variable
     * @param value the concrete boolean value
     */
    private constructor(name: String, value: Boolean) {
        this.name = name
        this.setValue(value)
    }

    /**
     * Public constructor that initializes the symbolic boolean variable with a given name.
     *
     * @param name the name of the symbolic boolean variable
     */
    constructor(name: String) {
        // TODO: The following line must be refactored
        val id = JmcRuntime.currentTask()
        this.name = "SymbolicBoolean@" + name + "_" + id
        this.write()
    }

    /**
     * Assigns a symbolic boolean expression to this variable.
     *
     * @param expression the symbolic boolean expression to assign
     */
    fun assign(expression: JmcBooleanFormula) {
        write(expression)
    }

    /**
     * Assigns another symbolic boolean variable to this variable.
     *
     * @param symbolicBoolean the symbolic boolean variable to assign
     */
    fun assign(symbolicBoolean: SymbolicBoolean) {
        write(symbolicBoolean)
    }

    /**
     * Makes a deep copy of this SymbolicBoolean.
     *
     * @return a deep copy of this SymbolicBoolean
     */
    override fun clone(): SymbolicBoolean {
        val copy = SymbolicBoolean(name, getValue())
        if (eval != null) {
            val expressionCopy = JmcBooleanFormula()
            expressionCopy.formula = eval.getFormula()
            expressionCopy.integerVariableMap = eval.getIntegerVariableMap()
            copy.eval = expressionCopy
        }
        return copy
    }

    /**
     * Reads the value of this SymbolicBoolean.
     *
     * @return the symbolic value
     */
    override fun read(): AbstractBoolean {
        JmcRuntimeUtils.readEventWithoutYield(this, "org/mpisws/jmc/symbolic/bool/SymbolicBoolean", "value", "SZ")
        val copy: AbstractBoolean = this.clone()
        JmcRuntime.yield<Any>()
        return copy
    }

    /**
     * Writes the value of this SymbolicBoolean with another SymbolicBoolean value.
     *
     * @param value the value to be written.
     */
    override fun write(value: SymbolicBoolean) {
        val symbolicBoolean = value.read() as SymbolicBoolean

        if (symbolicBoolean.eval != null) {
            val expressionCopy = JmcBooleanFormula()
            expressionCopy.formula = symbolicBoolean.eval.getFormula()
            expressionCopy.integerVariableMap = symbolicBoolean.eval.getIntegerVariableMap()
            this.eval = expressionCopy
        } else {
            this.name = symbolicBoolean.name
        }

        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            symbolicBoolean,
            "org/mpisws/jmc/symbolic/bool/SymbolicBoolean",
            "value",
            "SZ"
        )
        JmcRuntime.yield<Any>()
    }

    /**
     * Writes the value of this SymbolicBoolean with a JmcBooleanFormula value.
     *
     * @param value the value to be written.
     */
    override fun write(value: JmcBooleanFormula) {
        val expressionCopy = JmcBooleanFormula()
        expressionCopy.formula = value.formula
        expressionCopy.integerVariableMap = value.integerVariableMap
        this.eval = expressionCopy

        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            value,
            "org/mpisws/jmc/symbolic/bool/SymbolicBoolean",
            "value",
            "SZ"
        )
        JmcRuntime.yield<Any>()
    }

    /**
     * Writes the concrete boolean value of this SymbolicBoolean.
     */
    private fun write() {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            value,
            "org/mpisws/jmc/symbolic/bool/SymbolicBoolean",
            "value",
            "SZ"
        )
        JmcRuntime.yield<Any>()
    }
}
