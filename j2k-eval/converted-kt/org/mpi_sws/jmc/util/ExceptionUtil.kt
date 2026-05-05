package org.mpi_sws.jmc.util

/**
 * Utility class for exception handling.
 *
 *
 * This class provides methods to check if a given Throwable is an AssertionError or contains an
 * AssertionError in its cause chain.
 */
object ExceptionUtil {
    /**
     * Checks if the given Throwable is an AssertionError or contains an AssertionError in its cause
     * chain.
     *
     * @param t the Throwable to check
     * @return true if the Throwable is an AssertionError or contains one in its cause chain, false
     * otherwise
     */
    fun isAssertionError(t: Throwable?): Boolean {
        if (t is AssertionError) {
            return true
        }
        if (t is RuntimeException) {
            val firstCause = t.cause
            if (firstCause != null) {
                return firstCause.cause is AssertionError
            }
        }
        return false
    }
}
