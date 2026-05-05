package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils

/**
 * A redefinition of [java.util.concurrent.atomic.AtomicInteger] for JMC model checking. This
 * class provides atomic operations on an integer value, ensuring thread safety through the use of a
 * reentrant lock.
 */
class JmcAtomicInteger @JvmOverloads constructor(initialValue: Int = 0) {
    private var value: Int
    private val lock: JmcReentrantLock

    /**
     * Constructs a new JmcAtomicInteger with the specified initial value.
     *
     * @param initialValue the initial value of the atomic integer
     */
    /**
     * Constructs a new JmcAtomicInteger with an initial value of 0.
     */
    init {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            initialValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
            "value",
            "I"
        )
        value = initialValue
        JmcRuntime.yield<Any>()
        val lock = JmcReentrantLock()
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            lock,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
            "lock",
            "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;"
        )
        this.lock = lock
        JmcRuntime.yield<Any>()
    }

    /**
     * Returns the current value of this atomic integer. Invokes a read event to the JMC runtime.
     *
     * @return the current value
     */
    fun get(): Int {
        JmcRuntimeUtils.readEventWithoutYield(
            this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
        )
        val out = value
        JmcRuntime.yield<Any>()
        return out
    }

    /**
     * Sets the value of this atomic integer to the given value. Invokes a write event to the JMC
     * runtime.
     *
     * @param newValue the new value to set
     */
    fun set(newValue: Int) {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            newValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
            "value",
            "I"
        )
        value = newValue
        JmcRuntime.yield<Any>()
    }

    /**
     * Atomically sets the value to the given updated value if the current value is equal to the
     * expected value. Invokes a read followed by a write event to the JMC runtime.
     *
     * @param expectedValue the expected value
     * @param newValue      the new value to set if the current value equals the expected value
     * @return true if successful, false otherwise
     */
    fun compareAndSet(expectedValue: Int, newValue: Int): Boolean {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
            )
            if (value == expectedValue) {
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I"
                )
                value = newValue
                JmcRuntime.yield<Any>()
                return true
            }
            JmcRuntime.yield<Any>()
            return false
        } finally {
            lock.unlock()
        }
    }

    val andIncrement: Int
        /**
         * Atomically increments the current value by 1 and returns the previous value. Invokes a read
         * followed by a write event to the JMC runtime.
         *
         * @return the previous value before incrementing
         */
        get() {
            lock.lock()
            try {
                JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
                )
                val result = value
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I"
                )
                value = result + 1
                JmcRuntime.yield<Any>()
                return result
            } finally {
                lock.unlock()
            }
        }

    /**
     * Atomically sets the value to the given new value and returns the previous value. Invokes a
     * read followed by a write event to the JMC runtime.
     *
     * @param newValue the new value to set
     * @return the previous value before setting the new value
     */
    fun getAndSet(newValue: Int): Int {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
            )
            val oldValue = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                "value",
                "I"
            )
            value = newValue
            JmcRuntime.yield<Any>()
            return oldValue
        } finally {
            lock.unlock()
        }
    }

    /**
     * Atomically adds the given delta to the current value and returns the updated value. Invokes a
     * read followed by a write event to the JMC runtime.
     *
     * @param delta the value to add
     * @return the updated value after addition
     */
    fun addAndGet(delta: Int): Int {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                value + delta,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                "value",
                "I"
            )
            value = value + delta
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
            )
            val result = value
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    fun getAndAdd(delta: Int): Int {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
            )
            val result = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                result + delta,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                "value",
                "I"
            )
            value = result + delta
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    /**
     * Atomically increments the current value by 1 and returns the previous value. Invokes a read
     * followed by a write event to the JMC runtime.
     *
     * @return the previous value before incrementing
     */
    fun incrementAndGet(): Int {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                value + 1,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                "value",
                "I"
            )
            value = value + 1
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
            )
            val result = value
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    val andDecrement: Int
        get() {
            lock.lock()
            try {
                JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
                )
                val result = value
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result - 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I"
                )
                value = result - 1
                JmcRuntime.yield<Any>()
                return result
            } finally {
                lock.unlock()
            }
        }

    fun decrementAndGet(): Int {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                value - 1,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger",
                "value",
                "I"
            )
            value = value - 1
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I"
            )
            val result = value
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    /**
     * @return
     */
    override fun toString(): String {
        return super.toString()
    }
}
