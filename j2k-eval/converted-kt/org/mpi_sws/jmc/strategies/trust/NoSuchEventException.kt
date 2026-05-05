package org.mpi_sws.jmc.strategies.trust

/**
 * Exception thrown when an event does not exist.
 *
 *
 * This exception indicates that an operation was attempted on an event that is not defined in
 * the system.
 */
class NoSuchEventException(key: Event.Key) : Exception("Event$key Does not exist!")
