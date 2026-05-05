package org.mpi_sws.jmc.strategies

import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException

/**
 * Exception thrown when an invalid strategy is encountered.
 *
 *
 * This exception indicates that the strategy parameter provided is not valid or recognized.
 */
class JmcInvalidStrategyException(message: String?) : JmcCheckerException(message)
