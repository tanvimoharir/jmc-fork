package org.mpi_sws.jmc.runtime

import java.util.logging.Logger

class ThreadPoolManager {
    private val idCounter = 1

    private val idCounterLock = Any()

    private val threadPoolStates: Map<Int, ThreadPoolState>

    private val threadPoolsLock = Any()

    init {
        this.threadPoolStates = HashMap()
    }

    enum class ThreadPoolState {
        RUNNING,
        SHUTTING_DOWN,
        TERMINATED,
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ThreadPoolManager::class.java.name)
    }
}
