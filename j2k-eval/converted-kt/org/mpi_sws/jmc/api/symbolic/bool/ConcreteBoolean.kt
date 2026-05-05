package org.mpi_sws.jmc.api.symbolic.bool

/**
 * The [ConcreteBoolean] class is used to represent a concrete boolean value.
 */
class ConcreteBoolean : AbstractBoolean {
    /**
     * Default constructor that initializes the boolean value to false.
     */
    constructor() {
        this.setValue(false)
    }

    /**
     * Constructor that initializes the boolean value to the specified value.
     *
     * @param value the boolean value to be set.
     */
    constructor(value: Boolean) {
        this.setValue(value)
    }

    /**
     * Creates a deep copy of this ConcreteBoolean.
     *
     * @return a new instance of ConcreteBoolean with the same value.
     */
    override fun clone(): ConcreteBoolean {
        return ConcreteBoolean(this.getValue())
    }

    /**
     * Checks if this ConcreteBoolean is equal to another object.
     *
     * @param o the object to compare with.
     * @return true if the object is a ConcreteBoolean with the same value, false otherwise.
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as ConcreteBoolean
        return this.getValue() == that.getValue()
    }

    /**
     * Reads the value of this ConcreteBoolean.
     *
     * @return a new instance of ConcreteBoolean with the same value.
     */
    override fun read(): AbstractBoolean {
        val copy: AbstractBoolean = ConcreteBoolean(this.getValue())
        return copy
    }

    override fun write(value: JmcBooleanFormula) {
        // Do nothing
    }

    override fun write(value: SymbolicBoolean) {
        // Do nothing
    }
}
