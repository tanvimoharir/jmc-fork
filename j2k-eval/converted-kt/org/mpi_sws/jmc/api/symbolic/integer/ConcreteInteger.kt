package org.mpi_sws.jmc.api.symbolic.integer

/**
 * Concrete implementation of AbstractInteger.
 * This class represents a concrete integer value.
 */
class ConcreteInteger : AbstractInteger {
    /**
     * Default constructor initializes the value to 0.
     */
    constructor() {
        this.value = 0
    }

    /**
     * Constructor that initializes the value to the given integer.
     *
     * @param value the integer value to set
     */
    constructor(value: Int) {
        this.value = value
    }

    /**
     * Creates a deep copy of this ConcreteInteger.
     *
     * @return a new instance of ConcreteInteger with the same value
     */
    override fun clone(): ConcreteInteger {
        return ConcreteInteger(this.value)
    }

    /**
     * Checks if this ConcreteInteger is equal to another object.
     *
     * @param o the object to compare with
     * @return true if the object is a ConcreteInteger with the same value, false otherwise
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as ConcreteInteger
        return this.value == that.value
    }

    /**
     * Reads the value of this ConcreteInteger.
     *
     * @return a new instance of ConcreteInteger with the same value
     */
    override fun read(): AbstractInteger {
        return ConcreteInteger(this.value)
    }

    /**
     * Writes the value of this ConcreteInteger.
     * This method does nothing in this implementation.
     *
     * @param value the AbstractInteger value to write
     */
    override fun write(value: AbstractInteger) {
        // Do nothing
    }

    /**
     * Writes the value of this ConcreteInteger.
     * This method does nothing in this implementation.
     *
     * @param value the ArithmeticStatement value to write
     */
    override fun write(value: ArithmeticStatement) {
        // Do nothing
    }
}
