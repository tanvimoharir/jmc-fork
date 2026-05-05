package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils

/**
 * A redefinition of [java.util.concurrent.atomic.AtomicBoolean] that communicates with JMC
 * runtime to perform read, write, and compare-and-set operations.
 */
class JmcAtomicBoolean @JvmOverloads constructor(initialValue: Boolean = false) {
    private var value: Boolean
    private val lock: JmcReentrantLock

    /**
     * Constructs a new JmcAtomicBoolean with the specified initial value.
     *
     * @param initialValue the initial value of the atomic boolean
     */
    /**
     * Constructs a new JmcAtomicBoolean with an initial value of false.
     */
    init {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            initialValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean",
            "value",
            "Z"
        )
        this.value = initialValue
        JmcRuntime.yield<Any>()
        val lock = JmcReentrantLock()
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            lock,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean",
            "lock",
            "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;"
        )
        this.lock = lock
        JmcRuntime.yield<Any>()
    }

    /**
     * Returns the current value of this atomic boolean. Invokes a read event to the JMC runtime.
     *
     * @return the current value
     */
    fun get(): Boolean {
        JmcRuntimeUtils.readEventWithoutYield(
            this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z"
        )
        val out = value
        JmcRuntime.yield<Any>()
        return out
    }

    /**
     * Sets the value of this atomic boolean to the given value. Invokes a write event to the JMC
     * runtime.
     *
     * @param newValue the new value to set
     */
    fun set(newValue: Boolean) {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            newValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean",
            "value",
            "Z"
        )
        value = newValue
        JmcRuntime.yield<Any>()
    }

    /**
     * Atomically sets the value to the given updated value if the current value is equal to the
     * expected value. Invokes a read event followed by a write event to the JMC runtime.
     *
     * @param expectedValue the expected value
     * @param newValue      the new value to set if the current value equals the expected value
     * @return true if successful, false otherwise
     */
    fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z"
            )
            if (value == expectedValue) {
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean",
                    "value",
                    "Z"
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

    /**
     * @return
     */
    override fun toString(): String {
        return super.toString()
    }
}
