package org.mpi_sws.jmc.runtime

/**
 * Exception thrown when a task is already paused.
 *
 *
 * This exception indicates that an operation was attempted on a task that is already in a paused
 * state, which is not allowed.
 */
class TaskAlreadyPaused : Exception()
