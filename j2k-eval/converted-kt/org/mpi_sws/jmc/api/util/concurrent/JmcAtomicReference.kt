package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils

/**
 * A redefinition of [java.util.concurrent.atomic.AtomicReference] to support JMC model
 * checking. This class provides atomic operations on a reference variable, ensuring thread safety
 * through the use of a reentrant lock.
 * TODO : FIX THIS CLASS
 *
 * @param <V> the type of the reference held by this atomic reference
</V> */
class JmcAtomicReference<V> @JvmOverloads constructor(initialValue: V? = null) {
    private var value: V?

    private val lock: JmcReentrantLock

    /**
     * Constructs a new JmcAtomicReference with the specified initial value.
     *
     * @param initialValue the initial value of the atomic reference
     */
    /**
     * Constructs a new JmcAtomicReference with a null initial value.
     */
    // Added because of iceberg error: java.util.concurrent.ExecutionException:
    //* java.lang.NoSuchMethodError: org.mpi_sws.jmc.api.util.concurrent.JmcAtomicReference:
    //method void <init>() not found */
    init {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            initialValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
            "value",
            "Ljava/lang/Object;"
        )
        value = initialValue
        JmcRuntime.yield<Any>()
        val lock = JmcReentrantLock()
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            lock,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
            "lock",
            "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;"
        )
        this.lock = lock
        JmcRuntime.yield<Any>()
    }

    /**
     * Constructs a new JmcAtomicReference with a null initial value.
     */
    fun compareAndSet(expectedReference: V, newReference: V): Boolean {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;"
            )
            val readValue = value
            JmcRuntime.yield<Any>()
            if (readValue === expectedReference) {
                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newReference,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;"
                )
                value = newReference
                JmcRuntime.yield<Any>()
                return true
            }
            return false
        } finally {
            lock.unlock()
        }
    }

    fun get(): V? {
        JmcRuntimeUtils.readEventWithoutYield(
            this,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
            "value",
            "Ljava/lang/Object;"
        )
        val result = value
        JmcRuntime.yield<Any>()
        return result
    }

    fun set(newValue: V) {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            newValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
            "value",
            "Ljava/lang/Object;"
        )
        value = newValue
        JmcRuntime.yield<Any>()
    }

    fun getAndSet(newValue: V): V? {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;"
            )
            val result = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;"
            )
            value = newValue
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
