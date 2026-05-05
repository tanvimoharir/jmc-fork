package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import java.util.*

/**
 * A reentrant lock that can be used to synchronize access to shared resources. Replacement for
 * [java.util.concurrent.locks.ReentrantLock]
 *
 *
 * Yields control to the runtime for lock and unlock.
 */
class JmcReentrantLock {
    private var token = 0
    private val lockObj: Any?

    constructor() {
        JmcRuntimeUtils.writeEventWithoutYield(
            this, 0, "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock", "token", "I"
        )
        token = 0
        JmcRuntime.yield<Any>()
        this.lockObj = null
    }

    constructor(lockObj: Any?) {
        this.lockObj = lockObj
    }

    val instance: Any
        /** Returns the instance to be used for locking.  */
        get() = Objects.requireNonNullElse(lockObj, this)

    /** Acquires the lock.  */
    fun lock() {
        JmcRuntimeUtils.lockAcquireEvent(
            "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
            "token",
            token,
            "I",
            instance
        )

        token = 1

        // Removing call to an actual reentrant lock
        // lock.lock();
        // Since we use the same primitive for synchronized blocks with wait/notify,
        // we cannot do actual lock and unlock here and block.
        // Instead, we just yield to the runtime to handle the locking.
        // The runtime will manage which task has the lock and which are waiting.
        JmcRuntimeUtils.lockAcquiredEventWithoutYield(
            instance,
            "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
            "token",
            token,
            "I",
            1
        )
    }

    /** Releases the lock.  */
    fun unlock() {
        token = 0

        JmcRuntimeUtils.lockReleaseEvent(
            instance,
            "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
            "token",
            token,
            "I",
            0
        )
    }
}
