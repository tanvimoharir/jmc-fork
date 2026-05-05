package org.mpi_sws.jmc.api.util.concurrent

import java.util.concurrent.ThreadFactory

/**
 * A thread factory that creates [JmcThread] instances. Reimplementation of [ ]
 */
class JmcThreadFactory : ThreadFactory {
    private val baseFactory: ThreadFactory?

    /** Create a new thread factory that wraps the given base factory.  */
    constructor(baseFactory: ThreadFactory?) {
        this.baseFactory = baseFactory
    }

    /** Default JmcThread factory.  */
    constructor() {
        this.baseFactory = null
    }

    override fun newThread(r: Runnable): Thread {
        if (JmcThread::class.java.isAssignableFrom(r.javaClass)) {
            return r as JmcThread
        }
        if (baseFactory == null) {
            return JmcThread(r)
        }
        return JmcThread(baseFactory.newThread(r))
    }
}
