package org.mpi_sws.jmc.runtime

/**
 * Exception thrown to halt execution of the current task. Used in the wrapped Thread and Future
 * interfaces when instrumenting.
 */
class HaltTaskException
/**
 * Constructs a new HaltTaskException object.
 *
 * @param taskId the ID of the task that threw the exception
 */(
    /**
     * Returns the ID of the task which should be halted.
     *
     * @return the ID of the task which should be halted.
     */
    // The ID of the task that threw the exception.
    val taskId: Long?, private val type: Type
) : RuntimeException() {
    val isBlocked: Boolean
        get() = type == Type.BLOCKED

    val isTaskError: Boolean
        get() = type == Type.TASK_ERROR

    /**
     * Exception type when the model checker stops a task.
     */
    enum class Type {
        TASK_ERROR,
        BLOCKED
    }

    companion object {
        fun error(taskId: Long?, type: Type): HaltTaskException {
            return HaltTaskException(taskId, type)
        }

        fun blocked(taskId: Long?): HaltTaskException {
            return HaltTaskException(taskId, Type.BLOCKED)
        }
    }
}
