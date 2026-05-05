package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils

class JmcAtomicLong @JvmOverloads constructor(initialValue: Long = 0L) {
    private var value: Long
    private val lock: JmcReentrantLock

    init {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            initialValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
            "value",
            "J"
        )
        value = initialValue
        JmcRuntime.yield<Any>()
        val lock = JmcReentrantLock()
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            lock,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
            "lock",
            "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;"
        )
        this.lock = lock
        JmcRuntime.yield<Any>()
    }

    fun get(): Long {
        JmcRuntimeUtils.readEventWithoutYield(
            this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
        )
        val out = value
        JmcRuntime.yield<Any>()
        return out
    }

    fun set(newValue: Long) {
        JmcRuntimeUtils.writeEventWithoutYield(
            this,
            newValue,
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
            "value",
            "J"
        )
        value = newValue
        JmcRuntime.yield<Any>()
    }

    fun compareAndSet(expect: Long, update: Long): Boolean {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
            )
            val currentValue = value
            JmcRuntime.yield<Any>()
            if (currentValue == expect) {
                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    update,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J"
                )
                value = update
                JmcRuntime.yield<Any>()
                return true
            } else {
                return false
            }
        } finally {
            lock.unlock()
        }
    }

    val andIncrement: Long
        get() {
            lock.lock()
            try {
                JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
                )
                val result = value
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J"
                )
                value = result + 1
                JmcRuntime.yield<Any>()
                return result
            } finally {
                lock.unlock()
            }
        }

    fun getAndSet(newValue: Long): Long {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
            )
            val oldValue = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
            )
            value = newValue
            JmcRuntime.yield<Any>()
            return oldValue
        } finally {
            lock.unlock()
        }
    }

    fun addAndGet(delta: Long): Long {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                value + delta,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
            )
            value = value + delta
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
            )
            val result = value
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    fun getAndAdd(delta: Long): Long {
        lock.lock()
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
            )
            val result = value
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                result + delta,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
            )
            value = result + delta
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    fun incrementAndGet(): Long {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                value + 1,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
            )
            value = value + 1
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
            )
            val result = value
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }

    val andDecrement: Long
        get() {
            lock.lock()
            try {
                JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
                )
                val result = value
                JmcRuntime.yield<Any>()

                JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result - 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J"
                )
                value = result - 1
                JmcRuntime.yield<Any>()
                return result
            } finally {
                lock.unlock()
            }
        }

    fun decrementAndGet(): Long {
        lock.lock()
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                this,
                value - 1,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
            )
            value = value - 1
            JmcRuntime.yield<Any>()

            JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J"
            )
            val result = value
            JmcRuntime.yield<Any>()
            return result
        } finally {
            lock.unlock()
        }
    }
}
