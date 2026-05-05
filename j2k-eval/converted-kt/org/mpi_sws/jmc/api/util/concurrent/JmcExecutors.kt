package org.mpi_sws.jmc.api.util.concurrent

import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

/**
 * A replacement for [java.util.concurrent.Executors]. Currently only supports a
 * `newSingleThreadExecutor` and `newFixedThreadPool` methods, which return instances of [ ].
 */
object JmcExecutors {
    /**
     * Creates a single-threaded executor that uses a JMC executor service.
     *
     * @return a new single-threaded executor
     */
    fun newSingleThreadExecutor(): ExecutorService {
        return JmcExecutorService(1)
    }

    /**
     * Creates a fixed thread pool with the specified number of threads that uses a JMC executor
     * service.
     *
     * @param nThreads the number of threads in the pool
     * @return a new fixed thread pool executor
     */
    fun newFixedThreadPool(nThreads: Int): ExecutorService {
        return JmcExecutorService(nThreads)
    }

    /**
     * Minimal overload to match java.util.concurrent.Executors.newSingleThreadExecutor(ThreadFactory).
     * For now we ignore the provided ThreadFactory and delegate to the existing no-arg method.
     * (If thread factory semantics become important to model, we can incorporate it later.)
     */
    //        Added for iceberg error : java.util.concurrent.ExecutionException: java.lang.NoSuchMethodError:
    //            'java.util.concurrent.ExecutorService
    //            org.mpi_sws.jmc.api.util.concurrent.JmcExecutors.newFixedThreadPool(int, java.util.concurrent.ThreadFactory)'
    fun newFixedThreadPool(nThreads: Int, threadFactory: ThreadFactory?): ExecutorService {
        return JmcExecutorService(nThreads, JmcThreadFactory(threadFactory))
    }

    fun newSingleThreadExecutor(threadFactory: ThreadFactory?): ExecutorService {
        return JmcExecutorService(1, JmcThreadFactory(threadFactory))
    }


    /**
     * Creates a fixed thread pool with the specified name prefix and pool size.
     * This method is used to replace calls to ThreadPools.newExitingWorkerPool().
     * The exiting behavior (shutdown hook) is not needed in JMC's controlled execution environment.
     *
     * @param namePrefix the name prefix for threads (ignored in JMC)
     * @param poolSize the number of threads in the pool
     * @return a new fixed thread pool executor
     */
    fun newExitingWorkerPool(namePrefix: String?, poolSize: Int): ExecutorService {
        return JmcExecutorService(poolSize)
    }


    /**
     * Creates a scheduled thread pool with the specified number of threads.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @param corePoolSize the number of threads in the pool
     * @return a new scheduled thread pool executor
     */
    fun newScheduledThreadPool(corePoolSize: Int): ScheduledExecutorService {
        return JmcScheduledExecutorService(corePoolSize)
    }


    /**
     * Creates a scheduled thread pool with the specified number of threads and thread factory.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @param corePoolSize the number of threads in the pool
     * @param threadFactory the factory to use when creating new threads
     * @return a new scheduled thread pool executor
     */
    fun newScheduledThreadPool(corePoolSize: Int, threadFactory: ThreadFactory?): ScheduledExecutorService {
        return JmcScheduledExecutorService(corePoolSize, JmcThreadFactory(threadFactory))
    }

    /**
     * Creates a single-threaded scheduled executor.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @return a new single-threaded scheduled executor
     */
    fun newSingleThreadScheduledExecutor(): ScheduledExecutorService {
        return JmcScheduledExecutorService(1)
    }

    /**
     * Creates a single-threaded scheduled executor with the specified thread factory.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return a new single-threaded scheduled executor
     */
    fun newSingleThreadScheduledExecutor(threadFactory: ThreadFactory?): ScheduledExecutorService {
        return JmcScheduledExecutorService(1, JmcThreadFactory(threadFactory))
    }
}
