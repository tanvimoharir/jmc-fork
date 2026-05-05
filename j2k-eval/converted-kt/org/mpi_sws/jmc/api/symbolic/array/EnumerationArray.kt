package org.mpi_sws.jmc.api.symbolic.array

import org.mpi_sws.jmc.api.symbolic.SymbolicFormula
import org.mpi_sws.jmc.api.symbolic.bool.PropositionalFormula
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticFormula
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger
import org.mpi_sws.jmc.api.util.statements.JmcAssert

/**
 * A simple array implementation that supports symbolic indexing by enumerating all possible
 * indices (this class will be replaced by a more efficient implementation in the future).
 */
class EnumerationArray<T>(length: Int) {
    /**
     * The core underlying array.
     */
    var array: Array<T> = arrayOfNulls<Any>(length) as Array<T?>

    /**
     * Sets the value at the given index.
     *
     * @param index the index to set the value at.
     * @param value the value to set.
     */
    fun set(index: Int, value: T) {
        if (index >= 0 && index < array.size) {
            array[index] = value
        } else {
            JmcAssert.check(false, "Symbolic array index out of bounds")
        }
    }

    /**
     * Gets the value at the given index.
     *
     * @param index the index to get the value from.
     * @return the value at the given index.
     */
    fun get(index: Int): T? {
        if (index >= 0 && index < array.size) {
            return array[index]
        } else {
            JmcAssert.check(false, "Symbolic array index out of bounds")
            return null
        }
    }

    /**
     * Gets the length of the array.
     *
     * @return the length of the array.
     */
    fun length(): Int {
        return array.size
    }

    /**
     * Gets the value at the given symbolic index.
     *
     * @param index the symbolic index to get the value from.
     * @return the value at the given symbolic index.
     */
    fun get(index: SymbolicInteger): T {
        val a = ArithmeticFormula()
        val op1 = a.geq(index, 0)
        val op2 = a.lt(index, array.size)
        val prop = PropositionalFormula()
        val op3 = prop.and(op1!!, op2!!)
        JmcAssert.check(op3, "Array index out of bounds")
        val i = enumerateIndex(0, index)
        return array[i]
    }

    /**
     * Enumerates the index to find the concrete value of the symbolic index.
     *
     * @param i     the current index to check.
     * @param index the symbolic index.
     * @return the concrete value of the symbolic index.
     */
    private fun enumerateIndex(i: Int, index: SymbolicInteger): Int {
        val a = ArithmeticFormula()
        val op1 = a.eq(index, i)
        val f = SymbolicFormula()
        return if (f.evaluate(op1)) {
            i
        } else {
            enumerateIndex(i + 1, index)
        }
    }
}
