package org.mpi_sws.jmc.api.symbolic.integer

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.symbolic.InstructionType
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.solver.SolverUtil
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.BooleanFormulaManager
import org.sosy_lab.java_smt.api.IntegerFormulaManager
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import java.util.stream.Collectors

/**
 * Class [ArithmeticFormula] is used to create symbolic arithmetic formulas based on
 * abstract integers.
 */
class ArithmeticFormula {
    /**
     * @property [.integerVariableMap] is used to store the symbolic integer variable map.
     */
    private var integerVariableMap: MutableMap<String?, IntegerFormula?> = HashMap()

    /**
     * @property [.bmgr] is used to store the boolean formula manager of the symbolic solver.
     */
    private val bmgr: BooleanFormulaManager? = SolverUtil.getBmgr()

    /**
     * @property [.imgr] is used to store the integer formula manager of the symbolic solver.
     */
    private val imgr: IntegerFormulaManager? = SolverUtil.getImgr()

    /**
     * Creates a symbolic equality operation based on two abstract integers.
     *
     *
     * The method creates a symbolic equality operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the equality
     * operation. If one of the abstract integers is a symbolic integer and the other is a concrete
     * integer, it creates an integer formula for the left operand and a number formula for the
     * right operand and creates a boolean formula for the equality operation. If both abstract
     * integers are concrete integers, it creates a number formula for the left and right operands
     * and creates a boolean formula for the equality operation. If the type of the abstract
     * integers is not supported, it prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean equality formula.
     */
    fun eq(var1: AbstractInteger, var2: AbstractInteger): JmcBooleanFormula {
        return makeBooleanFormula(var1, var2, InstructionType.EQ)
    }

    /**
     * Creates a symbolic equality operation based on an abstract integer and an integer value.
     *
     *
     * This method creates a symbolic equality operation based on an abstract integer and an
     * integer value. It creates a concrete integer object with the integer value and calls the
     * [.eq] method with the abstract integer and the
     * concrete integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean equality formula.
     */
    fun eq(var1: AbstractInteger, var2: Int): JmcBooleanFormula {
        val concreteInteger = ConcreteInteger(var2)
        return eq(var1, concreteInteger)
    }

    /**
     * Creates a symbolic equality operation based on an integer value and an abstract integer.
     *
     *
     * This method creates a symbolic equality operation based on an integer value and an
     * abstract integer by calling the [.eq] method with the abstract
     * integer and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean equality formula.
     */
    fun eq(var1: Int, var2: AbstractInteger): JmcBooleanFormula {
        return eq(var2, var1)
    }

    /**
     * Creates a symbolic inequality operation based on two abstract integers.
     *
     *
     * This method creates a symbolic inequality operation based on two abstract integers. First,
     * it clears the integer variable map which stores the symbolic integer variable which are used
     * in the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the inequality
     * operation. If one of the abstract integers is a symbolic integer and the other is a concrete
     * integer, it creates an integer formula for the left operand and a number formula for the
     * right operand and creates a boolean formula for the inequality operation. If both abstract
     * integers are concrete integers, it creates a number formula for the left and right operands
     * and creates a boolean formula for the inequality operation. If the type of the abstract
     * integers is not supported, it prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean inequality formula.
     */
    fun neq(var1: AbstractInteger, var2: AbstractInteger): JmcBooleanFormula {
        return makeBooleanFormula(var1, var2, InstructionType.NEQ)
    }

    /**
     * Creates a symbolic inequality operation based on an abstract integer and an integer value.
     *
     *
     * This method creates a symbolic inequality operation based on an abstract integer and an
     * integer value. It creates a concrete integer object with the integer value and calls the
     * [.neq] method with the abstract integer and the
     * concrete integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean inequality formula.
     */
    fun neq(var1: AbstractInteger, var2: Int): JmcBooleanFormula {
        val concreteInteger = ConcreteInteger(var2)
        return neq(var1, concreteInteger)
    }

    /**
     * Creates a symbolic inequality operation based on an integer value and an abstract integer.
     *
     *
     * This method creates a symbolic inequality operation based on an integer value and an
     * abstract integer by calling the [.neq] method with the abstract
     * integer and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean inequality formula.
     */
    fun neq(var1: Int, var2: AbstractInteger): JmcBooleanFormula {
        return neq(var2, var1)
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on two abstract integers.
     *
     *
     * This method creates a symbolic geq operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the geq operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the geq operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the geq operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean greater than or equal formula.
     */
    fun geq(var1: AbstractInteger, var2: AbstractInteger): JmcBooleanFormula {
        return makeBooleanFormula(var1, var2, InstructionType.GEQ)
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on an abstract integer and an
     * integer value.
     *
     *
     * This method creates a symbolic geq operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the [ ][.geq] method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean greater than or equal formula.
     */
    fun geq(var1: AbstractInteger, var2: Int): JmcBooleanFormula {
        val concreteInteger = ConcreteInteger(var2)
        return geq(var1, concreteInteger)
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on an integer value and an
     * abstract integer.
     *
     *
     * This method creates a symbolic geq operation based on an integer value and an abstract
     * integer by calling the [.geq] method with the abstract integer
     * and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean greater than or equal formula.
     */
    fun geq(var1: Int, var2: AbstractInteger): JmcBooleanFormula {
        return geq(var2, var1)
    }

    /**
     * Creates a symbolic greater than (gt) operation based on two abstract integers.
     *
     *
     * This method creates a symbolic gt operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the gt operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the gt operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the gt operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean greater than formula.
     */
    fun gt(var1: AbstractInteger, var2: AbstractInteger): JmcBooleanFormula {
        return makeBooleanFormula(var1, var2, InstructionType.GT)
    }

    /**
     * Creates a symbolic greater than (gt) operation based on an abstract integer and an integer
     * value.
     *
     *
     * This method creates a symbolic gt operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the [ ][.gt] method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean greater than formula.
     */
    fun gt(var1: AbstractInteger, var2: Int): JmcBooleanFormula {
        val concreteInteger = ConcreteInteger(var2)
        return gt(var1, concreteInteger)
    }

    /**
     * Creates a symbolic greater than (gt) operation based on an integer value and an abstract
     * integer.
     *
     *
     * This method creates a symbolic gt operation based on an integer value and an abstract
     * integer by calling the [.gt] method with the abstract integer and
     * the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean greater than formula.
     */
    fun gt(var1: Int, var2: AbstractInteger): JmcBooleanFormula {
        return gt(var2, var1)
    }

    /**
     * Creates a symbolic less than or equal (leq) operation based on two abstract integers.
     *
     *
     * This method creates a symbolic leq operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the leq operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the leq operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the leq operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean less than or equal formula.
     */
    fun leq(var1: AbstractInteger, var2: AbstractInteger): JmcBooleanFormula {
        return makeBooleanFormula(var1, var2, InstructionType.LEQ)
    }

    /**
     * Creates a symbolic less than or equal (leq) operation based on an abstract integer and an
     * integer value.
     *
     *
     * This method creates a symbolic leq operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the [ ][.leq] method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean less than or equal formula.
     */
    fun leq(var1: AbstractInteger, var2: Int): JmcBooleanFormula {
        val concreteInteger = ConcreteInteger(var2)
        return leq(var1, concreteInteger)
    }

    /**
     * Creates a symbolic less than or equal (leq) operation based on an integer value and an
     * abstract integer.
     *
     *
     * This method creates a symbolic leq operation based on an integer value and an abstract
     * integer by calling the [.leq] method with the abstract integer
     * and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean less than or equal formula.
     */
    fun leq(var1: Int, var2: AbstractInteger): JmcBooleanFormula {
        return leq(var2, var1)
    }

    /**
     * Creates a symbolic less than (lt) operation based on two abstract integers.
     *
     *
     * This method creates a symbolic lt operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the lt operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the lt operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the lt operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean less than formula.
     */
    fun lt(var1: AbstractInteger, var2: AbstractInteger): JmcBooleanFormula {
        return makeBooleanFormula(var1, var2, InstructionType.LT)
    }

    /**
     * Creates a symbolic less than (lt) operation based on an abstract integer and an integer
     * value.
     *
     *
     * This method creates a symbolic lt operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the [ ][.lt] method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean less than formula.
     */
    fun lt(var1: AbstractInteger, var2: Int): JmcBooleanFormula {
        val concreteInteger = ConcreteInteger(var2)
        return lt(var1, concreteInteger)
    }

    /**
     * Creates a symbolic less than (lt) operation based on an integer value and an abstract
     * integer.
     *
     *
     * This method creates a symbolic lt operation based on an integer value and an abstract
     * integer by calling the [.lt] method with the abstract integer and
     * the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean less than formula.
     */
    fun lt(var1: Int, var2: AbstractInteger): JmcBooleanFormula {
        return lt(var2, var1)
    }

    /**
     * Creates a symbolic distinct operation based on a list of abstract integers.
     *
     *
     * This method creates a symbolic distinct operation based on a list of abstract integers.
     * First, it clears the integer variable map which stores the symbolic integer variable which
     * are used in the operation. Then, it creates a symbolic operation object and iterates through
     * the list of abstract integers to create an integer formula for each abstract integer. It then
     * creates a boolean formula for the distinct operation using the list of integer formulas.
     *
     * @param vars the list of abstract integers.
     * @return the boolean distinct formula.
     */
    fun distinct(vars: List<AbstractInteger>): JmcBooleanFormula {
        integerVariableMap = HashMap()
        val formula = JmcBooleanFormula()

        val formulas = ArrayList<IntegerFormula?>()
        for (`var` in vars) {
            formulas.add(makeIntegerFormula(`var`))
        }
        val distinctFormula = imgr!!.distinct(formulas)

        formula.formula = distinctFormula
        formula.integerVariableMap = integerVariableMap

        // Explicitly upcast List<AbstractInteger> to List<SymbolicOperand>
        val operandList =
            vars.stream().map { `var`: AbstractInteger? -> `var` }.collect(Collectors.toList())
        formula.setJmcFormula(operandList, InstructionType.DISTINCT)
        return formula
    }

    /**
     * Creates a symbolic boolean formula based on two abstract integers and an operator.
     *
     *
     * This method creates a symbolic boolean formula based on two abstract integers and an
     * operator. First, it clears the integer variable map which stores the symbolic integer
     * variable which are used in the operation. Then, it creates a symbolic boolean formula object
     * and creates integer formulas for the left and right operands using the abstract integers. It
     * then creates a boolean formula for the operation using the left and right operands and the
     * operator. Finally, it sets the formula, integer variable map, and JMC formula in the symbolic
     * boolean formula object and returns it.
     *
     * @param var1     the first abstract integer.
     * @param var2     the second abstract integer.
     * @param operator the operator for the operation.
     * @return the symbolic boolean formula.
     */
    private fun makeBooleanFormula(
        var1: AbstractInteger, var2: AbstractInteger, operator: InstructionType
    ): JmcBooleanFormula {
        integerVariableMap = HashMap()
        val formula = JmcBooleanFormula()

        val leftOperand = makeIntegerFormula(var1)
        val rightOperand = makeIntegerFormula(var2)
        val arithmeticFormula =
            makeArithmeticFormula(leftOperand!!, rightOperand!!, operator)

        formula.formula = arithmeticFormula
        formula.integerVariableMap = integerVariableMap
        formula.setJmcFormula(var1, var2, operator)

        return formula
    }

    /**
     * Creates a symbolic arithmetic formula based on two integer formulas and an operator.
     *
     *
     * This method creates a symbolic arithmetic formula based on two integer formulas and an
     * operator. It uses a switch statement to determine the operator and creates the corresponding
     * boolean formula using the integer formula manager. If the operator is not supported, it
     * prints an error message and exits the program.
     *
     * @param leftOperand  the left integer formula.
     * @param rightOperand the right integer formula.
     * @param operator     the operator for the operation.
     * @return the boolean formula for the operation.
     */
    private fun makeArithmeticFormula(
        leftOperand: IntegerFormula,
        rightOperand: IntegerFormula,
        operator: InstructionType
    ): BooleanFormula {
        return when (operator) {
            InstructionType.EQ -> imgr!!.equal(leftOperand, rightOperand)
            InstructionType.NEQ -> bmgr!!.not(imgr!!.equal(leftOperand, rightOperand))
            InstructionType.GEQ -> imgr!!.greaterOrEquals(leftOperand, rightOperand)
            InstructionType.GT -> imgr!!.greaterThan(leftOperand, rightOperand)
            InstructionType.LEQ -> imgr!!.lessOrEquals(leftOperand, rightOperand)
            InstructionType.LT -> imgr!!.lessThan(leftOperand, rightOperand)
            else -> {
                LOGGER.error("[Symbolic Execution] Unsupported operator [{}]", operator)
                throw HaltExecutionException.Companion.error("unsupported operator [$operator]")
            }
        }
    }

    /**
     * Creates an integer formula based on an abstract integer.
     *
     *
     * This method creates an integer formula based on an abstract integer. It checks the type
     * of the abstract integer and calls the corresponding handler method to create the integer
     * formula. If the abstract integer is a concrete integer, it calls the [ ][.handleConcrInt] method. If the abstract integer is a symbolic integer, it
     * calls the [.handleSymbInt] method.
     *
     * @param abstInteger the abstract integer.
     * @return the integer formula.
     */
    private fun makeIntegerFormula(abstInteger: AbstractInteger): IntegerFormula? {
        return if (abstInteger is ConcreteInteger) {
            handleConcrInt(abstInteger)
        } else {
            handleSymbInt((abstInteger.read() as SymbolicInteger))
        }
    }

    /**
     * Handles the creation of an integer formula for a symbolic integer.
     *
     *
     * This method handles the creation of an integer formula for a symbolic integer. If the
     * symbolic integer has no evaluation, it finds the variable in the integer variable map. If the
     * symbolic integer has an evaluation, it creates integer formulas for the left and right
     * operands and creates the corresponding integer formula based on the operator. If the operator
     * is not supported, it prints an error message and exits the program.
     *
     * @param symbolicInteger the symbolic integer.
     * @return the integer formula.
     */
    private fun handleSymbInt(symbolicInteger: SymbolicInteger): IntegerFormula? {
        if (symbolicInteger.eval == null) {
            return findVariable(symbolicInteger.name)
        }
        val leftOperand =
            if (symbolicInteger.eval!!.left is SymbolicInteger)
                makeIntegerFormula(symbolicInteger.eval!!.left)
            else
                imgr.makeNumber(symbolicInteger.eval!!.left.value.toLong())
        val rightOperand =
            if (symbolicInteger.eval!!.right is SymbolicInteger)
                makeIntegerFormula(symbolicInteger.eval!!.right)
            else
                imgr.makeNumber(symbolicInteger.eval!!.right.value.toLong())

        return when (symbolicInteger.eval!!.operator) {
            InstructionType.ADD -> imgr!!.add(leftOperand, rightOperand)
            InstructionType.SUB -> imgr!!.subtract(leftOperand, rightOperand)
            InstructionType.MUL -> imgr!!.multiply(leftOperand, rightOperand)
            InstructionType.DIV -> imgr!!.divide(leftOperand, rightOperand)
            InstructionType.MOD -> imgr!!.modulo(leftOperand, rightOperand)
            else -> {
                LOGGER.error(
                    "[Symbolic Execution] Unsupported operator [{}]", symbolicInteger.eval!!
                        .operator
                )
                throw HaltExecutionException.Companion.error("unsupported operator [" + symbolicInteger.eval!!.operator + "]")
            }
        }
    }

    /**
     * Handles the creation of an integer formula for a concrete integer.
     *
     *
     * This method handles the creation of an integer formula for a concrete integer by
     * creating a number formula using the integer formula manager with the value of the concrete
     * integer.
     *
     * @param concreteInteger the concrete integer.
     * @return the integer formula.
     */
    private fun handleConcrInt(concreteInteger: ConcreteInteger): IntegerFormula {
        return imgr.makeNumber(concreteInteger.value.toLong())
    }

    /**
     * Finds a variable in the integer variable map or creates a new one if it does not exist.
     *
     *
     * This method finds a variable in the integer variable map based on its name. If the
     * variable exists in the map, it returns the corresponding integer formula. If the variable does
     * not exist in the map, it creates a new symbolic integer variable using SolverUtil, adds it to
     * the map, and returns the integer formula.
     *
     * @param name the name of the variable.
     * @return the integer formula for the variable.
     */
    private fun findVariable(name: String?): IntegerFormula? {
        if (integerVariableMap.containsKey(name)) {
            return integerVariableMap[name]
        } else {
            val symIntVariable = SolverUtil.getSymIntVariable(name!!)
            integerVariableMap[name] = symIntVariable.getVar()
            return symIntVariable.getVar()
        }
    }

    companion object {
        /**
         * @property [.LOGGER] is used to print the log messages.
         */
        private val LOGGER: Logger = LogManager.getLogger(
            ArithmeticFormula::class.java
        )
    }
}
