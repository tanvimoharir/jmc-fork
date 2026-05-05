package org.mpi_sws.jmc.api.symbolic.integer

import org.mpi_sws.jmc.api.symbolic.InstructionType

/**
 * ArithmeticStatement class represents an arithmetic operation between two abstract integers.
 * It can be used to perform addition, subtraction, multiplication, division, and modulus operations.
 */
class ArithmeticStatement {
    /**
     * The left operand of the arithmetic operation.
     */
    private var left: AbstractInteger? = null

    /**
     * The right operand of the arithmetic operation.
     */
    private var right: AbstractInteger? = null

    /**
     * The type of arithmetic operation to be performed.
     */
    private var operator: InstructionType? = null

    /**
     * Default constructor for ArithmeticStatement.
     * Initializes the left and right operands to null and the operator to null.
     */
    constructor()

    /**
     * Constructor for ArithmeticStatement.
     * Initializes the left and right operands and the operator.
     *
     * @param var1     The first operand (AbstractInteger).
     * @param var2     The second operand (AbstractInteger).
     * @param operator The type of arithmetic operation to be performed.
     */
    constructor(var1: AbstractInteger, var2: AbstractInteger, operator: InstructionType?) {
        this.left = var1.read()
        this.right = var2.read()
        this.operator = operator
    }

    /**
     * Constructor for ArithmeticStatement.
     * Initializes the left operand to a ConcreteInteger and the right operand to an AbstractInteger.
     *
     * @param var1     The first operand (ConcreteInteger).
     * @param var2     The second operand (AbstractInteger).
     * @param operator The type of arithmetic operation to be performed.
     */
    constructor(var1: AbstractInteger, var2: Int, operator: InstructionType?) {
        this.left = var1.read()
        this.right = ConcreteInteger(var2)
        this.operator = operator
    }

    /**
     * Constructor for ArithmeticStatement.
     * Initializes the left operand to a ConcreteInteger and the right operand to an AbstractInteger.
     *
     * @param var1     The first operand (int).
     * @param var2     The second operand (AbstractInteger).
     * @param operator The type of arithmetic operation to be performed.
     */
    constructor(var1: Int, var2: AbstractInteger, operator: InstructionType?) {
        this.left = ConcreteInteger(var1)
        this.right = var2.read()
        this.operator = operator
    }

    /**
     * Performs addition between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    fun add(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1.read()
        this.right = var2.read()
        this.operator = InstructionType.ADD
    }

    /**
     * Performs addition between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    fun add(var1: AbstractInteger, var2: Int) {
        this.left = var1.read()
        this.right = ConcreteInteger(var2)
        this.operator = InstructionType.ADD
    }

    /**
     * Performs addition between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    fun add(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2.read()
        this.operator = InstructionType.ADD
    }

    /**
     * Performs subtraction between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    fun sub(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1.read()
        this.right = var2.read()
        this.operator = InstructionType.SUB
    }

    /**
     * Performs subtraction between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    fun sub(var1: AbstractInteger, var2: Int) {
        this.left = var1.read()
        this.right = ConcreteInteger(var2)
        this.operator = InstructionType.SUB
    }

    /**
     * Performs subtraction between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    fun sub(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2.read()
        this.operator = InstructionType.SUB
    }

    /**
     * Performs multiplication between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    fun mul(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1.read()
        this.right = var2.read()
        this.operator = InstructionType.MUL
    }

    /**
     * Performs multiplication between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    fun mul(var1: AbstractInteger, var2: Int) {
        this.left = var1.read()
        this.right = ConcreteInteger(var2)
        this.operator = InstructionType.MUL
    }

    /**
     * Performs multiplication between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    fun mul(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2.read()
        this.operator = InstructionType.MUL
    }

    /**
     * Performs division between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    fun div(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1.read()
        this.right = var2.read()
        this.operator = InstructionType.DIV
    }

    /**
     * Performs division between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    fun div(var1: AbstractInteger, var2: Int) {
        this.left = var1.read()
        this.right = ConcreteInteger(var2)
        this.operator = InstructionType.DIV
    }

    /**
     * Performs division between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    fun div(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2.read()
        this.operator = InstructionType.DIV
    }

    /**
     * Performs modulus between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    fun mod(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1.read()
        this.right = var2.read()
        this.operator = InstructionType.MOD
    }

    /**
     * Performs modulus between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    fun mod(var1: AbstractInteger, var2: Int) {
        this.left = var1.read()
        this.right = ConcreteInteger(var2)
        this.operator = InstructionType.MOD
    }

    /**
     * Performs modulus between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    fun mod(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2.read()
        this.operator = InstructionType.MOD
    }

    /**
     * Clones the current ArithmeticStatement object.
     *
     * @return A new ArithmeticStatement object with the same values as the current object.
     */
    override fun clone(): ArithmeticStatement {
        val copy = ArithmeticStatement()
        copy.left = left!!.clone()
        copy.right = right!!.clone()
        copy.operator = operator
        return copy
    }

    /**
     * Returns the left operand of the arithmetic operation.
     *
     * @return The left operand (AbstractInteger).
     */
    fun getLeft(): AbstractInteger? {
        return this.left
    }

    /**
     * Returns the right operand of the arithmetic operation.
     *
     * @return The right operand (AbstractInteger).
     */
    fun getRight(): AbstractInteger? {
        return this.right
    }

    /**
     * Returns the type of arithmetic operation to be performed.
     *
     * @return The type of arithmetic operation (InstructionType).
     */
    fun getOperator(): InstructionType? {
        return this.operator
    }

    /**
     * Sets the left operand of the arithmetic operation.
     *
     * @param left The left operand (AbstractInteger).
     */
    fun setLeft(left: AbstractInteger?) {
        this.left = left
    }

    /**
     * Sets the right operand of the arithmetic operation.
     *
     * @param right The right operand (AbstractInteger).
     */
    fun setRight(right: AbstractInteger?) {
        this.right = right
    }

    /**
     * Sets the type of arithmetic operation to be performed.
     *
     * @param operator The type of arithmetic operation (InstructionType).
     */
    fun setOperator(operator: InstructionType?) {
        this.operator = operator
    }
}
