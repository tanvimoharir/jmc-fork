package org.mpi_sws.jmc.runtime

/**
 * Exception thrown when a task does not exist.
 *
 *
 * This exception indicates that an operation was attempted on a task that is not found in the
 * system, typically due to an invalid thread ID.
 */
class TaskNotExists
/**
 * Constructs a new TaskNotExists exception with the specified thread ID.
 *
 * @param threadId the ID of the thread for which the task does not exist
 */
    (threadId: Long) : Exception("Task does not exist: $threadId")
