package org.mpi_sws.jmc.checker.exceptions

/**
 * Exception class for JMC invalid configuration errors.
 *
 *
 * This exception is thrown when there are issues related to the configuration of the JMC
 * checker, such as missing or invalid settings.
 */
class JmcInvalidConfigurationException
/**
 * Constructs a new JmcInvalidConfigurationException with the specified detail message.
 *
 * @param message the detail message
 */
    (message: String?) : JmcCheckerException(message)
