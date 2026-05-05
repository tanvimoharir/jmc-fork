package org.mpi_sws.jmc.api.util.concurrent

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * A JMC-specific version of [java.util.concurrent.CompletableFuture] that allows for custom
 * execution and provides a way to set an underlying JmcFuture.
 *
 * @param <T> the type of the result of the future
</T> */
class JmcCompletableFuture<T> : CompletableFuture<T>() {
    private var underlyingFuture: JmcFuture<T>? = null

    fun setUnderlyingFuture(underlyingFuture: JmcFuture<T>?) {
        this.underlyingFuture = underlyingFuture
    }

    override fun <U> newIncompleteFuture(): CompletableFuture<U> {
        return JmcCompletableFuture()
    }

    override fun defaultExecutor(): Executor {
        return executor
    }

    class JmcAsyncRunnable<T>(private val supplier: Supplier<out T>?, future: JmcCompletableFuture<T>) : Runnable {
        private val future: JmcCompletableFuture<T?>
        private val runnable: Runnable?

        init {
            this.future = future
            this.runnable = null
        }

        fun setUnderlyingFuture(underlyingFuture: JmcFuture<T>?) {
            future.setUnderlyingFuture(underlyingFuture)
        }

        override fun run() {
            try {
                if (supplier == null) {
                    runnable!!.run()
                    future.complete(null)
                } else {
                    future.complete(supplier.get())
                }
            } catch (ex: Throwable) {
                future.completeExceptionally(ex)
            }
        }
    }

    companion object {
        private val executor = JmcExecutorService(2)

        fun <U> supplyAsync(supplier: Supplier<U>): CompletableFuture<U> {
            return asyncSupplyStage(executor, supplier)
        }

        fun runAsync(runnable: Runnable): CompletableFuture<Void> {
            return runAsync(runnable, executor)
        }

        fun <U> asyncSupplyStage(e: JmcExecutorService, f: Supplier<U>): CompletableFuture<U?> {
            if (f == null) throw NullPointerException()
            val d = JmcCompletableFuture<U?>()
            val underlyingFuture =
                e.submit {
                    try {
                        d.complete(f.get())
                    } catch (ex: Throwable) {
                        d.completeExceptionally(ex)
                    }
                }
            d.setUnderlyingFuture(underlyingFuture)
            return d
        }

        fun asyncRunStage(e: JmcExecutorService, f: Runnable): CompletableFuture<Void?> {
            if (f == null) throw NullPointerException()
            val d = JmcCompletableFuture<Void?>()
            val underlyingFuture =
                e.submit {
                    try {
                        f.run()
                        d.complete(null)
                    } catch (ex: Throwable) {
                        d.completeExceptionally(ex)
                    }
                }
            d.setUnderlyingFuture(underlyingFuture)
            return d
        }
    }
}
