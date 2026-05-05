package org.mpi_sws.jmc.api.symbolic.bool

import org.mpi_sws.jmc.api.symbolic.InstructionType
import org.mpi_sws.jmc.solver.SolverUtil
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.BooleanFormulaManager
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula

/**
 * This class provides methods to create and manipulate propositional formulas
 * using symbolic boolean variables.
 */
class PropositionalFormula {
    /**
     * The BooleanFormulaManager instance used to create and manipulate boolean formulas.
     */
    private val bmgr: BooleanFormulaManager? = SolverUtil.getBmgr()

    /**
     * A map to store symbolic boolean variables and their corresponding BooleanFormula instances.
     */
    private val booleanVariableMap: MutableMap<String?, BooleanFormula?> = HashMap()

    /**
     * Creates a boolean formula representing the negation of the given symbolic boolean formula.
     *
     * @param op the JmcBooleanFormula to negate
     * @return a new JmcBooleanFormula representing the negation
     */
    fun not(op: JmcBooleanFormula): JmcBooleanFormula {
        return makeUnaryOperation(op, InstructionType.NOT)
    }

    /**
     * Creates a boolean formula representing the negation of the given symbolic boolean variable.
     *
     * @param var the SymbolicBoolean variable to negate
     * @return a new JmcBooleanFormula representing the negation
     */
    fun not(`var`: SymbolicBoolean): JmcBooleanFormula {
        return makeUnaryOperation(`var`, InstructionType.NOT)
    }

    /**
     * Creates a boolean formula representing an atomic literal of the given symbolic boolean variable.
     *
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the atomic literal
     */
    fun atomicLiteral(`var`: SymbolicBoolean): JmcBooleanFormula {
        return makeUnaryOperation(`var`, InstructionType.ATOM)
    }

    /**
     * Creates a boolean formula representing an atomic literal of the given JmcBooleanFormula.
     *
     * @param op the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the atomic literal
     */
    fun atomicLiteral(op: JmcBooleanFormula): JmcBooleanFormula {
        return makeUnaryOperation(op, InstructionType.ATOM)
    }

    /**
     * Helper method to create a unary operation on a JmcBooleanFormula based on the specified operation type.
     *
     * @param var       the JmcBooleanFormula operand
     * @param operation the type of unary operation to perform
     * @return a new JmcBooleanFormula representing the result of the unary operation
     */
    private fun makeUnaryOperation(`var`: JmcBooleanFormula, operation: InstructionType): JmcBooleanFormula {
        val booleanFormula = JmcBooleanFormula()

        when (operation) {
            InstructionType.ATOM -> {
                booleanFormula.formula = `var`.formula
                break
            }

            InstructionType.NOT -> {
                val formula = bmgr!!.not(`var`.formula)
                booleanFormula.formula = formula
                break
            }

            else -> {
                throw UnsupportedOperationException("Unsupported operation: $operation")
            }
        }
        booleanFormula.setJmcFormula(`var`, null, operation)
        booleanFormula.booleanVariableMap = `var`.booleanVariableMap
        booleanFormula.integerVariableMap = `var`.integerVariableMap
        return booleanFormula
    }

    /**
     * Helper method to create a unary operation on a SymbolicBoolean based on the specified operation type.
     *
     * @param var       the SymbolicBoolean operand
     * @param operation the type of unary operation to perform
     * @return a new JmcBooleanFormula representing the result of the unary operation
     */
    private fun makeUnaryOperation(`var`: SymbolicBoolean, operation: InstructionType): JmcBooleanFormula {
        val symBool = `var`.read() as SymbolicBoolean
        if (symBool.eval != null) {
            return makeUnaryOperation(symBool.eval, operation)
        } else {
            booleanVariableMap.clear()
            val booleanFormula = JmcBooleanFormula()

            when (operation) {
                InstructionType.ATOM -> {
                    booleanFormula.formula = makeBooleanFormula(symBool)
                    break
                }

                InstructionType.NOT -> {
                    val formula =
                        bmgr!!.not(makeBooleanFormula(symBool))
                    booleanFormula.formula = formula
                    break
                }

                else -> {
                    throw UnsupportedOperationException("Unsupported operation: $operation")
                }
            }
            booleanFormula.setJmcFormula(symBool, null, operation)
            booleanFormula.booleanVariableMap = booleanVariableMap
            return booleanFormula
        }
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the conjunction
     */
    fun and(op1: JmcBooleanFormula, op2: JmcBooleanFormula): JmcBooleanFormula {
        return makeBinaryOperation(op1, op2, InstructionType.AND)
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the conjunction
     */
    fun and(var1: SymbolicBoolean, var2: SymbolicBoolean): JmcBooleanFormula {
        return makeBinaryOperation(var1, var2, InstructionType.AND)
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the conjunction
     */
    fun and(op1: JmcBooleanFormula, `var`: SymbolicBoolean): JmcBooleanFormula {
        val op2 = atomicLiteral(`var`)
        return makeBinaryOperation(op1, op2, InstructionType.AND)
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the conjunction
     */
    fun and(`var`: SymbolicBoolean?, op: JmcBooleanFormula?): JmcBooleanFormula {
        return and(`var`, op)
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the disjunction
     */
    fun or(op1: JmcBooleanFormula, op2: JmcBooleanFormula): JmcBooleanFormula {
        return makeBinaryOperation(op1, op2, InstructionType.OR)
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the disjunction
     */
    fun or(var1: SymbolicBoolean, var2: SymbolicBoolean): JmcBooleanFormula {
        return makeBinaryOperation(var1, var2, InstructionType.OR)
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the disjunction
     */
    fun or(op1: JmcBooleanFormula, `var`: SymbolicBoolean): JmcBooleanFormula {
        val op2 = atomicLiteral(`var`)
        return makeBinaryOperation(op1, op2, InstructionType.OR)
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the disjunction
     */
    fun or(`var`: SymbolicBoolean?, op: JmcBooleanFormula?): JmcBooleanFormula {
        return or(`var`, op)
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the implication
     */
    fun implies(op1: JmcBooleanFormula, op2: JmcBooleanFormula): JmcBooleanFormula {
        return makeBinaryOperation(op1, op2, InstructionType.IMPLIES)
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the implication
     */
    fun implies(var1: SymbolicBoolean, var2: SymbolicBoolean): JmcBooleanFormula {
        return makeBinaryOperation(var1, var2, InstructionType.IMPLIES)
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the implication
     */
    fun implies(op1: JmcBooleanFormula, `var`: SymbolicBoolean): JmcBooleanFormula {
        val op2 = atomicLiteral(`var`)
        return makeBinaryOperation(op1, op2, InstructionType.IMPLIES)
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the implication
     */
    fun implies(`var`: SymbolicBoolean?, op: JmcBooleanFormula?): JmcBooleanFormula {
        return implies(`var`, op)
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the biconditional
     */
    fun iff(op1: JmcBooleanFormula, op2: JmcBooleanFormula): JmcBooleanFormula {
        return makeBinaryOperation(op1, op2, InstructionType.IFF)
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the biconditional
     */
    fun iff(var1: SymbolicBoolean, var2: SymbolicBoolean): JmcBooleanFormula {
        return makeBinaryOperation(var1, var2, InstructionType.IFF)
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the biconditional
     */
    fun iff(op1: JmcBooleanFormula, `var`: SymbolicBoolean): JmcBooleanFormula {
        val op2 = atomicLiteral(`var`)
        return makeBinaryOperation(op1, op2, InstructionType.IFF)
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the biconditional
     */
    fun iff(`var`: SymbolicBoolean?, op: JmcBooleanFormula?): JmcBooleanFormula {
        return iff(`var`, op)
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    fun xor(op1: JmcBooleanFormula, op2: JmcBooleanFormula): JmcBooleanFormula {
        return makeBinaryOperation(op1, op2, InstructionType.XOR)
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    fun xor(var1: SymbolicBoolean, var2: SymbolicBoolean): JmcBooleanFormula {
        return makeBinaryOperation(var1, var2, InstructionType.XOR)
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    fun xor(op1: JmcBooleanFormula, `var`: SymbolicBoolean): JmcBooleanFormula {
        val op2 = atomicLiteral(`var`)
        return makeBinaryOperation(op1, op2, InstructionType.XOR)
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    fun xor(`var`: SymbolicBoolean?, op: JmcBooleanFormula?): JmcBooleanFormula {
        return xor(`var`, op)
    }

    /**
     * Helper method to create a binary operation on two JmcBooleanFormulas based on the specified operation type.
     *
     * @param left      the left JmcBooleanFormula operand
     * @param right     the right JmcBooleanFormula operand
     * @param operation the type of binary operation to perform
     * @return a new JmcBooleanFormula representing the result of the binary operation
     */
    private fun makeBinaryOperation(
        left: JmcBooleanFormula, right: JmcBooleanFormula, operation: InstructionType
    ): JmcBooleanFormula {
        val booleanFormula = JmcBooleanFormula()
        val formula: BooleanFormula

        when (operation) {
            InstructionType.AND -> {
                formula = bmgr!!.and(left.formula, right.formula)
                break
            }

            InstructionType.OR -> {
                formula = bmgr!!.or(left.formula, right.formula)
                break
            }

            InstructionType.IMPLIES -> {
                formula = bmgr!!.implication(left.formula, right.formula)
                break
            }

            InstructionType.IFF -> {
                formula = bmgr!!.equivalence(left.formula, right.formula)
                break
            }

            InstructionType.XOR -> {
                formula = bmgr!!.xor(left.formula, right.formula)
                break
            }

            else -> {
                throw UnsupportedOperationException("Unsupported operation: $operation")
            }
        }
        booleanFormula.formula = formula
        booleanFormula.setJmcFormula(left, right, operation)
        booleanFormula.integerVariableMap = unionIntegerVariableMap(
            left.integerVariableMap, right.integerVariableMap
        )
        booleanFormula.booleanVariableMap = unionBooleanVariableMap(
            left.booleanVariableMap, right.booleanVariableMap
        )
        return booleanFormula
    }

    /**
     * Helper method to create a binary operation on two SymbolicBooleans based on the specified operation type.
     *
     * @param left      the left SymbolicBoolean operand
     * @param right     the right SymbolicBoolean operand
     * @param operation the type of binary operation to perform
     * @return a new JmcBooleanFormula representing the result of the binary operation
     */
    private fun makeBinaryOperation(
        left: SymbolicBoolean, right: SymbolicBoolean, operation: InstructionType
    ): JmcBooleanFormula {
        if (left.eval != null && right.eval != null) {
            val symbol1 = left.read() as SymbolicBoolean
            val symbol2 = right.read() as SymbolicBoolean

            return makeBinaryOperation(symbol1, symbol2, operation)
        } else if (left.eval != null) {
            val symbol1 = left.read() as SymbolicBoolean
            val symbol2 = atomicLiteral(right)

            return makeBinaryOperation(symbol1.eval, symbol2, operation)
        } else if (right.eval != null) {
            val symbol2 = right.read() as SymbolicBoolean
            val symbol1 = atomicLiteral(left)

            return makeBinaryOperation(symbol1, symbol2.eval, operation)
        } else {
            booleanVariableMap.clear()
            val boolFormula = JmcBooleanFormula()
            val symbol1 = left.read() as SymbolicBoolean
            val symbol2 = right.read() as SymbolicBoolean
            val leftOperand = makeBooleanFormula(symbol1)
            val rightOperand = makeBooleanFormula(symbol2)
            val formula: BooleanFormula
            when (operation) {
                InstructionType.AND -> {
                    formula = bmgr!!.and(leftOperand, rightOperand)
                    break
                }

                InstructionType.OR -> {
                    formula = bmgr!!.or(leftOperand, rightOperand)
                    break
                }

                InstructionType.IMPLIES -> {
                    formula = bmgr!!.implication(leftOperand, rightOperand)
                    break
                }

                InstructionType.IFF -> {
                    formula = bmgr!!.equivalence(leftOperand, rightOperand)
                    break
                }

                InstructionType.XOR -> {
                    formula = bmgr!!.xor(leftOperand, rightOperand)
                    break
                }

                else -> {
                    throw UnsupportedOperationException("Unsupported operation: $operation")
                }
            }
            boolFormula.formula = formula
            boolFormula.setJmcFormula(symbol1, symbol2, operation)
            boolFormula.booleanVariableMap = booleanVariableMap
            return boolFormula
        }
    }

    /**
     * Finds a BooleanFormula for the given SymbolicBoolean.
     *
     * @param symbolicBoolean the SymbolicBoolean to find the BooleanFormula for
     * @return the corresponding BooleanFormula
     */
    private fun makeBooleanFormula(
        symbolicBoolean: SymbolicBoolean
    ): BooleanFormula? {
        return findVariable(symbolicBoolean.name)
    }

    /**
     * Finds or creates a BooleanFormula for the given variable name.
     *
     * @param name the name of the variable
     * @return the corresponding BooleanFormula
     */
    private fun findVariable(name: String?): BooleanFormula? {
        if (booleanVariableMap.containsKey(name)) {
            return booleanVariableMap[name]
        } else {
            val symBoolVariable = SolverUtil.getSymBoolVariable(name)
            booleanVariableMap[name] = symBoolVariable.getVar()
            return symBoolVariable.getVar()
        }
    }

    /**
     * Merges two maps of integer variables into a single map.
     *
     * @param map1 the first map of integer variables
     * @param map2 the second map of integer variables
     * @return a new map containing all entries from both input maps
     */
    private fun unionIntegerVariableMap(
        map1: Map<String?, IntegerFormula?>,
        map2: Map<String?, IntegerFormula?>
    ): MutableMap<String?, IntegerFormula?> {
        val unionMap: MutableMap<String?, IntegerFormula?> = HashMap()
        unionMap.putAll(map1)
        unionMap.putAll(map2)
        return unionMap
    }

    /**
     * Merges two maps of boolean variables into a single map.
     *
     * @param map1 the first map of boolean variables
     * @param map2 the second map of boolean variables
     * @return a new map containing all entries from both input maps
     */
    fun unionBooleanVariableMap(
        map1: Map<String?, BooleanFormula?>,
        map2: Map<String?, BooleanFormula?>
    ): MutableMap<String?, BooleanFormula?> {
        val unionMap: MutableMap<String?, BooleanFormula?> = HashMap()
        unionMap.putAll(map1)
        unionMap.putAll(map2)
        return unionMap
    }
}
