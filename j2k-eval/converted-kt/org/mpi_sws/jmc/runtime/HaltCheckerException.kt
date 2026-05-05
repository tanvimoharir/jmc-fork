package org.mpi_sws.jmc.runtime

/** Exception thrown to halt execution of the current and all subsequent executions.  */
class HaltCheckerException : RuntimeException {
    /**
     * Returns whether the exploration was successful without any errors.
     *
     * @return true if the exploration was successful, false otherwise
     */
    var isOkay: Boolean = false
        private set

    /**
     * Returns whether the exploration was halted due to a timeout.
     *
     * @return true if the exploration was halted due to a timeout, false otherwise
     */
    var isTimeout: Boolean = false
        private set

    private constructor(ok: Boolean, message: String, timeout: Boolean) : super(message) {
        this.isOkay = ok
        this.isTimeout = timeout
    }

    private constructor(message: String, cause: Throwable) : super(message, cause) {
        this.isOkay = false
        this.isTimeout = false
    }

    companion object {
        /**
         * Constructs a new [HaltCheckerException] indicating that the exploration stopped
         * naturally without any errors.
         */
        fun ok(): HaltCheckerException {
            return HaltCheckerException(true, "OK", false)
        }

        /**
         * Constructs a new [HaltCheckerException] indicating that the exploration was halted due
         * to a timeout.
         */
        fun timeout(): HaltCheckerException {
            return HaltCheckerException(false, "Timeout", true)
        }

        /**
         * Constructs a new [HaltCheckerException] indicating that the exploration was halted due
         * to an error.
         *
         * @param message the error message
         */
        @JvmStatic
        fun error(message: String): HaltCheckerException {
            return HaltCheckerException(false, message, false)
        }

        /**
         * Constructs a new [HaltCheckerException] indicating that the exploration was halted due
         * to an error, with a cause.
         *
         * @param message the error message
         * @param cause the cause of the error
         */
        fun error(message: String, cause: Throwable): HaltCheckerException {
            return HaltCheckerException(message, cause)
        }
    }
}
