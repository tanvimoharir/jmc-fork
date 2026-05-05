package org.mpi_sws.jmc.runtime

/**
 * Exception thrown to halt execution of the current execution.
 */
class HaltExecutionException
/**
 * Constructs a new [HaltExecutionException] with the given type and message.
 */(val type: Type, message: String?) : RuntimeException(message) {
    val isReexecutionNeeded: Boolean
        get() = type == Type.REEXECTION_NEEDED

    /**
     * Exception type when the model checker stops the execution.
     */
    enum class Type {
        PROGRAM_ERROR,
        CONSISTENCY_VIOLATION,
        DEADLOCK,
        RACE_CONDITION,
        REEXECTION_NEEDED,
        ALL_OK,
    }

    companion object {
        /**
         * Constructs a new [HaltExecutionException] of type error with the given message.
         */
        fun error(message: String?): HaltExecutionException {
            return HaltExecutionException(Type.PROGRAM_ERROR, message)
        }

        /**
         * Constructs a new [HaltExecutionException] of type ALL_OK with the given message.
         */
        fun ok(): HaltExecutionException {
            return HaltExecutionException(Type.ALL_OK, "All OK")
        }

        fun reexecutionNeeded(): HaltExecutionException {
            return HaltExecutionException(Type.REEXECTION_NEEDED, "Re-execution needed")
        }
    }
}
