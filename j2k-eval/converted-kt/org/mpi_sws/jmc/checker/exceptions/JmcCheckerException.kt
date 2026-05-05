package org.mpi_sws.jmc.checker.exceptions

/**
 * Exception class for JMC checker errors.
 *
 *
 * This exception is thrown when there are issues related to the JMC checker, such as
 * configuration errors or runtime exceptions during the checking process.
 */
open class JmcCheckerException : Exception {
    /**
     * Constructs a new JmcCheckerException with the specified detail message.
     *
     * @param message the detail message
     */
    constructor(message: String?) : super(message)

    /**
     * Constructs a new JmcCheckerException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
