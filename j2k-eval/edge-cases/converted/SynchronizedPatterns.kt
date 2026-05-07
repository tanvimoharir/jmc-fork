package edgecases

/**
 * Edge case: Java synchronization patterns.
 * Hypothesis: The converter may not handle synchronized blocks, volatile fields,
 * and lock patterns idiomatically.
 */
class SynchronizedPatterns {
    @kotlin.concurrent.Volatile
    private var running: Boolean = true
    private val lock: Any = Any()
    private var counter: Int = 0

    // synchronized method
    @kotlin.jvm.Synchronized
    fun increment() {
        counter++
    }

    val andReset: Int
        // synchronized block
        get() {
            synchronized(lock) {
                val value: Int = counter
                counter = 0
                return value
            }
        }

    // wait/notify pattern
    @kotlin.Throws(java.lang.InterruptedException::class)
    fun waitForCondition() {
        synchronized(lock) {
            while (counter == 0) {
                (lock as java.lang.Object).wait()
            }
        }
    }

    fun signalCondition() {
        synchronized(lock) {
            counter++
            (lock as java.lang.Object).notifyAll()
        }
    }

    // ReentrantLock with try-finally
    private val reentrantLock: java.util.concurrent.locks.ReentrantLock = java.util.concurrent.locks.ReentrantLock()
    private val notEmpty: java.util.concurrent.locks.Condition = reentrantLock.newCondition()

    fun lockPattern() {
        reentrantLock.lock()
        try {
            counter++
            notEmpty.signal()
        } finally {
            reentrantLock.unlock()
        }
    }

    companion object {
        // Double-checked locking (classic Java pattern)
        @kotlin.concurrent.Volatile
        var instance: SynchronizedPatterns? = null
            get() {
                if (field == null) {
                    synchronized(SynchronizedPatterns::class.java) {
                        if (field == null) {
                            field = SynchronizedPatterns()
                        }
                    }
                }
                return field
            }
            private set
    }
}