package org.mpi_sws.jmc.api.symbolic.integer

import org.mpi_sws.jmc.api.symbolic.InstructionType
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import org.mpi_sws.jmc.solver.SolverUtil

/**
 * The [SymbolicInteger] class represents a symbolic integer variable.
 * It extends the [AbstractInteger] class and provides methods to read and write
 * the symbolic integer value, as well as to assign expressions or other symbolic integers.
 */
class SymbolicInteger : AbstractInteger {
    /**
     * Gets the name of the symbolic integer variable.
     *
     * @return the name of the symbolic integer variable.
     */
    /**
     * Sets the name of the symbolic integer variable.
     *
     * @param name the name to be set.
     */
    /**
     * @property [.name] is used to store the name of the symbolic integer variable.
     */
    var name: String

    /**
     * @property [.eval] is used to store the arithmetic statement assigned to the symbolic integer variable.
     */
    private var eval: ArithmeticStatement? = null

    /**
     * @property [.value] is used to store the concrete value of the integer.
     */
    override val value: Int = 0

    /**
     * Default constructor is private to prevent its direct usage.
     */
    private constructor() {
        val parts = toString().split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        this.name = "SymbolicInteger@" + parts[parts.size - 1]
        write()
    }

    /**
     * Private constructor to create a symbolic integer with a specific name and value.
     *
     * @param name  the name of the symbolic integer variable.
     * @param value the concrete value of the integer.
     */
    private constructor(name: String, value: Int) {
        this.name = name
        this.setValue(value)
    }

    /**
     * Public constructor to create a symbolic integer with a specific name.
     *
     * @param name the name of the symbolic integer variable.
     */
    constructor(name: String) {
        val id = JmcRuntime.currentTask()
        this.name = "SymbolicInteger@" + name + "_" + id
        write()
    }

    /**
     * Assigns an arithmetic statement to the symbolic integer variable.
     *
     * @param expression the arithmetic statement to be assigned.
     */
    fun assign(expression: ArithmeticStatement) {
        write(expression)
    }

    /**
     * Assigns another symbolic integer to this symbolic integer variable.
     *
     * @param symbolicInteger the symbolic integer to be assigned.
     */
    fun assign(symbolicInteger: SymbolicInteger) {
        write(symbolicInteger)
    }

    /**
     * Makes a deep copy of the symbolic integer.
     *
     * @return a deep copy of the symbolic integer.
     */
    override fun clone(): SymbolicInteger {
        val copy = SymbolicInteger(name, getValue())
        if (eval != null) {
            copy.setEval(eval!!.clone())
        }
        return copy
    }

    /**
     * Gets the arithmetic statement assigned to the symbolic integer variable.
     *
     * @return the arithmetic statement assigned to the symbolic integer variable.
     */
    fun getEval(): ArithmeticStatement? {
        return if (eval != null) {
            eval
        } else {
            null
        }
    }

    /**
     * Sets the arithmetic statement assigned to the symbolic integer variable.
     *
     * @param eval the arithmetic statement to be set.
     */
    fun setEval(eval: ArithmeticStatement?) {
        this.eval = eval
    }

    /**
     * Reads the value of the symbolic integer variable.
     *
     * @return a deep copy of the symbolic integer variable.
     */
    override fun read(): AbstractInteger {
        JmcRuntimeUtils.readEventWithoutYield(
            this,
            "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
            "value",
            "SI"
        )
        val copy: AbstractInteger = this.clone()

        JmcRuntime.yield<Any>()
        return copy
    }

    /**
     * Writes the value to the symbolic integer variable with another symbolic integer value.
     *
     * @param value the value to be written.
     */
    override fun write(value: AbstractInteger) {
        val symbolicInteger = value.read() as SymbolicInteger
        if (symbolicInteger.getEval() != null) {
            this.eval = symbolicInteger.getEval()!!.clone()
        } else {
            this.name = symbolicInteger.name
        }

        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            symbolicInteger,
            "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
            "value",
            "SI"
        )
        JmcRuntime.yield<Any>()
    }

    /**
     * Writes the value to the symbolic integer variable with an arithmetic statement value.
     *
     * @param value the value to be written.
     */
    override fun write(value: ArithmeticStatement) {
        this.eval = value.clone()

        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            value,
            "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
            "value",
            "SI"
        )
        JmcRuntime.yield<Any>()
    }

    /**
     * Writes the initial value to the symbolic integer variable.
     */
    private fun write() {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            value,
            "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
            "value",
            "SI"
        )
        JmcRuntime.yield<Any>()
    }

    val intValue: Int
        /**
         * Evaluates and returns the integer value of the symbolic integer variable.
         *
         * @return the integer value of the symbolic integer variable.
         */
        get() {
            if (this.getEval() != null) {
                val leftValue = if (getEval()!!.left is SymbolicInteger) {
                    left.getIntValue()
                } else {
                    getEval()!!.left.value
                }
                val rightValue = if (getEval()!!.right is SymbolicInteger) {
                    right.getIntValue()
                } else {
                    getEval()!!.right.value
                }
                when (getEval()!!.operator) {
                    InstructionType.ADD -> return leftValue + rightValue
                    InstructionType.SUB -> return leftValue - rightValue
                    InstructionType.MUL -> return leftValue * rightValue
                    InstructionType.DIV -> {
                        if (rightValue == 0) {
                            throw ArithmeticException("[JMC Formula Message] Division by zero")
                        }
                        return leftValue / rightValue
                    }

                    InstructionType.MOD -> {
                        if (rightValue == 0) {
                            throw ArithmeticException("[JMC Formula Message] Modulo by zero")
                        }
                        return leftValue % rightValue
                    }

                    else -> throw IllegalArgumentException(
                        "[JMC Formula Message] Unsupported operator"
                    )
                }
            } else {
                return SolverUtil.getSymIntVarValue(this.name)
            }
        }
}
