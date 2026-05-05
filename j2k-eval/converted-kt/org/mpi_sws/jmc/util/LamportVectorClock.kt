package org.mpi_sws.jmc.util

import org.mpi_sws.jmc.util.TotalOrder.InvalidComparisonException

/** Represents a Lamport vector clock used by the algorithm. It is of variable length.  */
class LamportVectorClock : PartialOrder<LamportVectorClock> {
    /**
     * Returns the vector clock.
     *
     * @return The vector clock.
     */
    var vector: IntArray
        private set

    /**
     * Creates a new Lamport vector clock with the given size.
     *
     * @param size The size of the vector clock.
     */
    constructor(size: Int) {
        this.vector = IntArray(size)
        for (i in 0..<size) {
            vector[i] = 0
        }
    }

    /**
     * Creates a new Lamport vector clock with the given vector.
     *
     * @param vector The vector clock.
     */
    constructor(vector: IntArray) {
        this.vector = IntArray(vector.size)
        System.arraycopy(vector, 0, this.vector, 0, vector.size)
    }

    /**
     * Creates a new Lamport vector clock with the given vector, incrementing the value at the given
     * index.
     *
     * @param other The vector clock.
     * @param index The index of the component to increment.
     */
    constructor(other: LamportVectorClock, index: Int) {
        if (index >= other.vector.size) {
            this.vector = IntArray(index + 1)
            System.arraycopy(other.vector, 0, this.vector, 0, other.vector.size)
            other.vector = IntArray(index + 1)
            System.arraycopy(this.vector, 0, other.vector, 0, other.vector.size)
            for (i in other.vector.size..<index + 1) {
                vector[i] = 0
                other.vector[i] = 0
            }
        } else require(index >= 0) { "Index cannot be negative" }
        this.vector = IntArray(other.vector.size)
        System.arraycopy(other.vector, 0, this.vector, 0, other.vector.size)
        vector[index] = other.vector[index] + 1
    }

    val size: Int
        /**
         * Returns the size of the vector clock.
         *
         * @return The size of the vector clock.
         */
        get() = vector.size

    private fun grow(other: LamportVectorClock): Int {
        // Can't copy values. Need to initialize zeros here.
        if (other.vector.size > vector.size) {
            val newVector = IntArray(other.vector.size)
            System.arraycopy(this.vector, 0, newVector, 0, vector.size)
            this.vector = newVector
            return other.vector.size
        } else if (vector.size > other.vector.size) {
            val newVector = IntArray(vector.size)
            System.arraycopy(other.vector, 0, newVector, 0, other.vector.size)
            other.vector = newVector
            return vector.size
        }
        return vector.size
    }

    /**
     * Updates the vector clock with the given vector clock.
     *
     * @param other The other vector clock.
     */
    fun update(other: LamportVectorClock) {
        grow(other)
        if (vector.size != other.vector.size) {
            throw RuntimeException("Vector sizes do not match")
        }
        for (i in vector.indices) {
            vector[i] = kotlin.math.max(vector[i].toDouble(), other.vector[i].toDouble()).toInt()
        }
    }

    /**
     * Checks if this vector clock happens before the other vector clock. (less than or equal to)
     *
     * @param other The other vector clock.
     * @return True if this vector clock happened before the other vector clock, false otherwise.
     */
    fun happensBefore(other: LamportVectorClock): Boolean {
        var happenedBefore = false
        var happenedAfter = false
        var size = vector.size
        if (vector.size != other.vector.size) {
            size = grow(other)
        }
        for (i in 0..<size) {
            if (vector[i] <= other.vector[i]) {
                happenedBefore = true
            } else if (vector[i] > other.vector[i]) {
                happenedAfter = true
            }
        }
        return happenedBefore && !happenedAfter
    }

    /**
     * Returns the string representation of the vector clock.
     *
     * @return The string representation of the vector clock.
     */
    fun equals(other: LamportVectorClock): Boolean {
        if (vector.size != other.vector.size) {
            return false
        }
        for (i in vector.indices) {
            if (vector[i] != other.vector[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the maximum value in the vector clock.
     *
     * @return The maximum value in the vector clock.
     */
    fun max(): Int {
        var max = 0
        for (integer in vector) {
            if (integer > max) {
                max = integer
            }
        }
        return max
    }

    override fun compare(other: LamportVectorClock): PartialOrder.Relation {
        return if (this.happensBefore(other)) {
            PartialOrder.Relation.LT
        } else if (other.happensBefore(this)) {
            PartialOrder.Relation.GT
        } else if (this.equals(other)) {
            PartialOrder.Relation.EQ
        } else {
            PartialOrder.Relation.UNRELATED
        }
    }

    /** Represents a component of the Lamport vector clock.  */
    class Component
    /**
     * Constructs a new [Component] with the given index and vector clock.
     *
     * @param index The index of the component.
     * @param clock The vector clock.
     */(private val index: Int, private val clock: LamportVectorClock) : TotalOrder<Component> {
        @Throws(InvalidComparisonException::class)
        override fun compare(other: Component): TotalOrder.Relation {
            if (this.index != other.index) {
                throw InvalidComparisonException(
                    ("Cannot compare components with different indices: "
                            + this.index
                            + " and "
                            + other.index)
                )
            }
            val t1Component = clock.vector[index]
            val t2Component = other.clock.vector[other.index]
            return if (t1Component < t2Component) {
                TotalOrder.Relation.LT
            } else if (t1Component > t2Component) {
                TotalOrder.Relation.GT
            } else {
                TotalOrder.Relation.EQ
            }
        }
    }
}
