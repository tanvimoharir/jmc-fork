package org.mpi_sws.jmc.api.symbolic

import org.mpi_sws.jmc.api.symbolic.bool.AbstractBoolean
import org.mpi_sws.jmc.api.symbolic.bool.ConcreteBoolean
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean
import org.mpi_sws.jmc.api.symbolic.integer.AbstractInteger
import org.mpi_sws.jmc.api.symbolic.integer.ConcreteInteger
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger
import org.mpi_sws.jmc.solver.SolverUtil

class JmcConcreteFormula {
    /**
     * The left operand of the formula.
     */
    private var leftOperand: SymbolicOperand? = null

    /**
     * The right operand of the formula.
     */
    private var rightOperand: SymbolicOperand? = null

    /**
     * The list of operands for operators that take multiple operands.
     */
    private var operands: List<SymbolicOperand?>? = null

    /**
     * The operator of the formula.
     */
    private var operator: InstructionType? = null

    /**
     * Sets the left operand of the formula.
     *
     * @param leftOperand the left operand to set
     */
    fun setLeftOperand(leftOperand: SymbolicOperand?) {
        this.leftOperand = leftOperand
    }

    /**
     * Sets the right operand of the formula.
     *
     * @param rightOperand the right operand to set
     */
    fun setRightOperand(rightOperand: SymbolicOperand?) {
        this.rightOperand = rightOperand
    }

    /**
     * Sets the operator of the formula.
     *
     * @param operator the operator to set
     */
    fun setOperator(operator: InstructionType?) {
        this.operator = operator
    }

    /**
     * Sets the list of operands
     *
     * @param operands the list of operands to set
     */
    fun setOperands(operands: List<SymbolicOperand?>?) {
        this.operands = operands
    }

    /**
     * Evaluates the formula based on the operator and operands.
     *
     * @return the result of the evaluation
     */
    fun evaluate(): Boolean {
        return if (operator == null) {
            throw RuntimeException("Operator is not set")
        } else if (operator == InstructionType.EQ) {
            evalEqual()
        } else if (operator == InstructionType.NEQ) {
            evalNeq()
        } else if (operator == InstructionType.GT) {
            evalGreater()
        } else if (operator == InstructionType.LT) {
            evalLess()
        } else if (operator == InstructionType.GEQ) {
            evalGreaterEqual()
        } else if (operator == InstructionType.LEQ) {
            evalLessEqual()
        } else if (operator == InstructionType.AND) {
            evalAnd()
        } else if (operator == InstructionType.OR) {
            evalOr()
        } else if (operator == InstructionType.IMPLIES) {
            evalImplies()
        } else if (operator == InstructionType.IFF) {
            evalIff()
        } else if (operator == InstructionType.XOR) {
            evalXor()
        } else if (operator == InstructionType.NOT) {
            evalNot()
        } else if (operator == InstructionType.ATOM) {
            evalAtom()
        } else if (operator == InstructionType.DISTINCT) {
            evalDistinct()
        } else {
            throw RuntimeException("Unsupported operator")
        }
    }

    /**
     * Evaluates the DISTINCT operator.
     *
     * @return true if all operands are distinct, false otherwise
     */
    private fun evalDistinct(): Boolean {
        if (operands == null || operands!!.size == 0) {
            throw RuntimeException("Distinct operator must have at least two operands")
        }

        if (operands!!.size == 1) {
            return true // Single element is always distinct
        }

        var seenValues: HashSet<Int?>? = HashSet()
        for (operand in operands!!) {
            if (operand is AbstractInteger) {
                val value = getIntValue(operand)
                if (!seenValues!!.add(value)) {
                    seenValues.clear()
                    seenValues = null
                    return false // Duplicate found
                }
            } else {
                throw RuntimeException("Invalid operand for operator DISTINCT")
            }
        }
        seenValues!!.clear()
        seenValues = null
        return true // All elements are distinct
    }

    /**
     * Evaluates the ATOM operator.
     *
     * @return the boolean value of the left operand
     */
    private fun evalAtom(): Boolean {
        require(rightOperand == null) { "Right operand must be null for ATOM operator" }
        return if (leftOperand is AbstractBoolean) {
            getBoolValue(leftOperand)
        } else if (leftOperand is JmcBooleanFormula) {
            leftOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operand for operator ATOM")
        }
    }

    /**
     * Evaluates the NOT operator.
     *
     * @return the negation of the boolean value of the left operand
     */
    private fun evalNot(): Boolean {
        require(rightOperand == null) { "Right operand must be null for NOT operator" }
        return if (leftOperand is AbstractBoolean) {
            !getBoolValue(leftOperand)
        } else if (leftOperand is JmcBooleanFormula) {
            !leftOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operand for operator NOT")
        }
    }

    /**
     * Evaluates the EQ (equal) operator.
     *
     * @return true if the left and right operands are equal, false otherwise
     */
    private fun evalEqual(): Boolean {
        if (leftOperand is AbstractInteger
            && rightOperand is AbstractInteger
        ) {
            return getIntValue(leftOperand) == getIntValue(rightOperand)
        } else {
            throw RuntimeException("Invalid operands for operator EQ")
        }
    }

    /**
     * Evaluates the NEQ (not equal) operator.
     *
     * @return true if the left and right operands are not equal, false otherwise
     */
    private fun evalNeq(): Boolean {
        if (leftOperand is AbstractInteger
            && rightOperand is AbstractInteger
        ) {
            return getIntValue(leftOperand) != getIntValue(rightOperand)
        } else {
            throw RuntimeException("Invalid operands for operator NEQ")
        }
    }

    /**
     * Evaluates the GT (greater than) operator.
     *
     * @return true if the left operand is greater than the right operand, false otherwise
     */
    private fun evalGreater(): Boolean {
        if (leftOperand is AbstractInteger
            && rightOperand is AbstractInteger
        ) {
            return getIntValue(leftOperand) > getIntValue(rightOperand)
        } else {
            throw RuntimeException("Invalid operands for operator GT")
        }
    }

    /**
     * Evaluates the LT (less than) operator.
     *
     * @return true if the left operand is less than the right operand, false otherwise
     */
    private fun evalLess(): Boolean {
        if (leftOperand is AbstractInteger
            && rightOperand is AbstractInteger
        ) {
            return getIntValue(leftOperand) < getIntValue(rightOperand)
        } else {
            throw RuntimeException("Invalid operands for operator LT")
        }
    }

    /**
     * Evaluates the LEQ (less than or equal to) operator.
     *
     * @return true if the left operand is less than or equal to the right operand, false otherwise
     */
    private fun evalLessEqual(): Boolean {
        if (leftOperand is AbstractInteger
            && rightOperand is AbstractInteger
        ) {
            return getIntValue(leftOperand) <= getIntValue(rightOperand)
        } else {
            throw RuntimeException("Invalid operands for operator LEQ")
        }
    }

    /**
     * Evaluates the GEQ (greater than or equal to) operator.
     *
     * @return true if the left operand is greater than or equal to the right operand, false otherwise
     */
    private fun evalGreaterEqual(): Boolean {
        if (leftOperand is AbstractInteger
            && rightOperand is AbstractInteger
        ) {
            return getIntValue(leftOperand) >= getIntValue(rightOperand)
        } else {
            throw RuntimeException("Invalid operands for operator GEQ")
        }
    }

    /**
     * Evaluates the AND operator.
     *
     * @return true if both operands are true, false otherwise
     */
    private fun evalAnd(): Boolean {
        return if (leftOperand is AbstractBoolean
            && rightOperand is AbstractBoolean
        ) {
            getBoolValue(leftOperand) && getBoolValue(rightOperand)
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is AbstractBoolean
        ) {
            leftOperand.concreteEvaluation() && getBoolValue(rightOperand)
        } else if (leftOperand is AbstractBoolean
            && rightOperand is JmcBooleanFormula
        ) {
            getBoolValue(leftOperand) && rightOperand.concreteEvaluation()
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is JmcBooleanFormula
        ) {
            leftOperand.concreteEvaluation() && rightOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operands for operator AND")
        }
    }

    /**
     * Evaluates the OR operator.
     *
     * @return true if at least one operand is true, false otherwise
     */
    private fun evalOr(): Boolean {
        return if (leftOperand is AbstractBoolean
            && rightOperand is AbstractBoolean
        ) {
            getBoolValue(leftOperand) || getBoolValue(rightOperand)
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is AbstractBoolean
        ) {
            leftOperand.concreteEvaluation() || getBoolValue(rightOperand)
        } else if (leftOperand is AbstractBoolean
            && rightOperand is JmcBooleanFormula
        ) {
            getBoolValue(leftOperand) || rightOperand.concreteEvaluation()
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is JmcBooleanFormula
        ) {
            leftOperand.concreteEvaluation() || rightOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operands for operator OR")
        }
    }

    /**
     * Evaluates the IMPLIES operator.
     *
     * @return true if the implication holds, false otherwise
     */
    private fun evalImplies(): Boolean {
        return if (leftOperand is AbstractBoolean
            && rightOperand is AbstractBoolean
        ) {
            !getBoolValue(leftOperand) || getBoolValue(rightOperand)
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is AbstractBoolean
        ) {
            !leftOperand.concreteEvaluation() || getBoolValue(rightOperand)
        } else if (leftOperand is AbstractBoolean
            && rightOperand is JmcBooleanFormula
        ) {
            !getBoolValue(leftOperand) || rightOperand.concreteEvaluation()
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is JmcBooleanFormula
        ) {
            !leftOperand.concreteEvaluation() || rightOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operands for operator IMPLIES")
        }
    }

    /**
     * Evaluates the IFF (if and only if) operator.
     *
     * @return true if both operands are equal, false otherwise
     */
    private fun evalIff(): Boolean {
        return if (leftOperand is AbstractBoolean
            && rightOperand is AbstractBoolean
        ) {
            getBoolValue(leftOperand) == getBoolValue(rightOperand)
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is AbstractBoolean
        ) {
            leftOperand.concreteEvaluation() == getBoolValue(rightOperand)
        } else if (leftOperand is AbstractBoolean
            && rightOperand is JmcBooleanFormula
        ) {
            getBoolValue(leftOperand) == rightOperand.concreteEvaluation()
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is JmcBooleanFormula
        ) {
            leftOperand.concreteEvaluation() == rightOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operands for operator IFF")
        }
    }

    /**
     * Evaluates the XOR operator.
     *
     * @return true if exactly one operand is true, false otherwise
     */
    private fun evalXor(): Boolean {
        return if (leftOperand is AbstractBoolean
            && rightOperand is AbstractBoolean
        ) {
            getBoolValue(leftOperand) != getBoolValue(rightOperand)
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is AbstractBoolean
        ) {
            leftOperand.concreteEvaluation() != getBoolValue(rightOperand)
        } else if (leftOperand is AbstractBoolean
            && rightOperand is JmcBooleanFormula
        ) {
            getBoolValue(leftOperand) != rightOperand.concreteEvaluation()
        } else if (leftOperand is JmcBooleanFormula
            && rightOperand is JmcBooleanFormula
        ) {
            leftOperand.concreteEvaluation() != rightOperand.concreteEvaluation()
        } else {
            throw RuntimeException("Invalid operands for operator XOR")
        }
    }

    /**
     * Retrieves the integer value from an AbstractInteger.
     *
     * @param abstractInteger the AbstractInteger to retrieve the value from
     * @return the integer value
     */
    fun getIntValue(abstractInteger: AbstractInteger): Int {
        return if (abstractInteger is ConcreteInteger) {
            abstractInteger.getValue()
        } else if (abstractInteger is SymbolicInteger) {
            abstractInteger.intValue
        } else {
            throw RuntimeException("Unsupported type of AbstractInteger")
        }
    }

    /**
     * Retrieves the boolean value from an AbstractBoolean.
     *
     * @param abstractBoolean the AbstractBoolean to retrieve the value from
     * @return the boolean value
     */
    fun getBoolValue(abstractBoolean: AbstractBoolean): Boolean {
        return if (abstractBoolean is ConcreteBoolean) {
            abstractBoolean.getValue()
        } else if (abstractBoolean is SymbolicBoolean) {
            if (abstractBoolean.eval != null) {
                abstractBoolean.eval.concreteEvaluation()
            } else {
                SolverUtil.getSymBoolVarValue(abstractBoolean.name)
            }
        } else {
            throw RuntimeException("Unsupported type of AbstractBoolean")
        }
    }
}
