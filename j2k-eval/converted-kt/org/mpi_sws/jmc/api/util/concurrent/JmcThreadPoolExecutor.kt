package org.mpi_sws.jmc.api.util.concurrent

import org.mpi_sws.jmc.runtime.JmcRuntime
import java.util.concurrent.*

/**
 * A thread pool executor that runs tasks in new threads. The thread creation is wrapped with the
 * [JmcThreadFactory] to create [JmcThread] instances. Reimplementation of [ ]
 */
class JmcThreadPoolExecutor : ThreadPoolExecutor {
    constructor(nThreads: Int) : super(
        nThreads,
        nThreads,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(),
        JmcThreadFactory()
    )

    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit,
        workQueue: BlockingQueue<Runnable?>
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        JmcThreadFactory()
    )

    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit,
        workQueue: BlockingQueue<Runnable?>,
        threadFactory: ThreadFactory?
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        JmcThreadFactory(threadFactory)
    )

    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit,
        workQueue: BlockingQueue<Runnable?>,
        handler: RejectedExecutionHandler
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        JmcThreadFactory(),
        handler
    )

    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit,
        workQueue: BlockingQueue<Runnable?>,
        threadFactory: ThreadFactory?,
        handler: RejectedExecutionHandler
    ) : super(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        JmcThreadFactory(threadFactory),
        handler
    )

    override fun <T> newTaskFor(callable: Callable<T>): RunnableFuture<T> {
        return JmcFuture(callable, JmcRuntime.addNewTask())
    }


    override fun <T> newTaskFor(runnable: Runnable, value: T): RunnableFuture<T> {
        return JmcFuture(runnable, value, JmcRuntime.addNewTask())
    }
}
