package org.mpi_sws.jmc.api.util.concurrent

/**
 * A redefinition of the [java.util.concurrent.atomic.AtomicReferenceArray] class. This class
 * provides an array of references that can be atomically updated. It uses a [ ] to ensure thread safety.
 * TODO: Currently, this implementation does not communicate with the JMC runtime.
 * TODO : FIX THIS CLASS
 *
 * @param <V> the type of elements in this array
</V> */
class JmcAtomicReferenceArray<V>(length: Int) {
    // TODO: No initial write here.
    private val array = arrayOfNulls<Any>(length) as Array<V?>
    private val lock = JmcReentrantLock()

    fun getAndSet(index: Int, newValue: V): V? {
        lock.lock()
        try {
            if (index < 0 || index >= array.size) {
                throw ArrayIndexOutOfBoundsException(index)
            } else {
                val oldValue = array[index]
                array[index] = newValue
                return oldValue
            }
        } finally {
            lock.unlock()
        }
    }

    fun set(index: Int, newValue: V) {
        lock.lock()
        try {
            if (index < 0 || index >= array.size) {
                throw ArrayIndexOutOfBoundsException(index)
            } else {
                array[index] = newValue
            }
        } finally {
            lock.unlock()
        }
    }

    fun get(index: Int): V? {
        lock.lock()
        try {
            if (index < 0 || index >= array.size) {
                throw ArrayIndexOutOfBoundsException(index)
            } else {
                return array[index]
            }
        } finally {
            lock.unlock()
        }
    }

    fun length(): Int {
        return array.size
    }
}
