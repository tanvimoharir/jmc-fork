package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils

// TODO : FIX THIS CLASS
class JmcAtomicStampedReference<V>(initialValue: V, initialStamp: Int) {
    private var stamp: Int

    private var value: V

    private val lock: JmcReentrantLock

    init {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            initialValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
            "value",
            "Ljava/lang/Object;"
        )
        value = initialValue
        JmcRuntime.yield<Any>()

        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            initialStamp,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
            "stamp",
            "I"
        )
        stamp = initialStamp
        JmcRuntime.yield<Any>()

        lock = JmcReentrantLock()
    }

    fun compareAndSet(
        expectedReference: V, newReference: V, expectedStamp: Int, newStamp: Int
    ): Boolean {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "value",
                "Ljava/lang/Object;"
            )
            val readValue = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "stamp",
                "I"
            )
            val readStamp = stamp

            if (readValue === expectedReference && readStamp == expectedStamp) {
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newReference,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;"
                )
                value = newReference
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newStamp,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "stamp",
                    "I"
                )
                stamp = newStamp
                JmcRuntime.yield<Any>()
                return true
            }
            JmcRuntime.yield<Any>()
            return false
        } finally {
            lock.unlock()
        }
    }

    val reference: V
        get() {
            lock.lock()
            try {
                JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;"
                )
                val result = value
                JmcRuntime.yield<Any>()
                return result
            } finally {
                lock.unlock()
            }
        }

    fun getStamp(): Int {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "stamp",
                "I"
            )
            val result = stamp
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    fun set(newReference: V, newStamp: Int) {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newReference,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "value",
                "Ljava/lang/Object;"
            )
            value = newReference
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newStamp,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "stamp",
                "I"
            )
            stamp = newStamp
            JmcRuntime.yield<Any>()
        } finally {
            lock.unlock()
        }
    }

    fun get(stampHolder: IntArray): V {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "value",
                "Ljava/lang/Object;"
            )
            val result = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "stamp",
                "I"
            )
            val resultStamp = stamp
            JmcRuntime.yield<Any>()

            stampHolder[0] = resultStamp
            return result
        } finally {
            lock.unlock()
        }
    }
}
