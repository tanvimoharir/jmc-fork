package org.mpi_sws.jmc.checker.exceptions

/**
 * Exception class for JMC unsupported features.
 *
 *
 * This exception is thrown when there are any concurrency related
 * features which are currently unsupported
 */
class JmcUnsupportedFeatureException : RuntimeException {
    /**
     * Constructs a new JmcUnsupportedFeatureException with the specified detail message.
     *
     * @param message the detail message
     */
    constructor(message: String?) : super(message)

    /**
     * Constructs a new JmcUnsupportedFeatureException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
