package org.mpi_sws.jmc.checker.exceptions

/**
 * Exception class for JMC checker timeout errors.
 *
 *
 * This exception is thrown when the JMC checker exceeds the configured timeout limit during
 * execution.
 */
class JmcCheckerTimeoutException
/**
 * Constructs a new JmcCheckerTimeoutException with the specified detail message.
 *
 * @param message the detail message
 */
    (message: String?) : JmcCheckerException(message)
