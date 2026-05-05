package org.mpi_sws.jmc.runtime.scheduling

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice

/**
 * Represents a scheduling choice in the JMC runtime.
 *
 *
 * This class encapsulates a scheduling choice, which can either be a task to be executed, a
 * blocking task, or an end of the schedule. It also allows for the inclusion of additional values
 * associated with the scheduling choice.
 *
 * @param <T> the type of value associated with the scheduling choice
</T> */
class SchedulingChoice<T : SchedulingChoiceValue?> {
    /**
     * Returns the ID of the task associated with this scheduling choice.
     *
     * @return the ID of the task
     */
    val taskId: Long?

    /**
     * Checks if this scheduling choice is a blocking task.
     *
     * @return true if it is a blocking task, false otherwise
     */
    val isBlockTask: Boolean

    /**
     * Checks if this scheduling choice blocks execution.
     *
     * @return true if it blocks execution, false otherwise
     */
    val isBlockExecution: Boolean

    /**
     * Returns the value associated with this scheduling choice.
     *
     * @return the value of type T
     */
    val value: T

    /**
     * Constructs a new SchedulingChoice object.
     *
     * @param taskId the ID of the task associated with this scheduling choice
     * @param isBlockTask indicates if this choice is a blocking task
     * @param isBlockExecution indicates if this choice blocks execution
     */
    private constructor(taskId: Long?, isBlockTask: Boolean, isBlockExecution: Boolean) {
        this.taskId = taskId
        this.isBlockTask = isBlockTask
        this.isBlockExecution = isBlockExecution
        this.value = null
    }

    /**
     * Constructs a new SchedulingChoice object with a value.
     *
     * @param taskId the ID of the task associated with this scheduling choice
     * @param value the value associated with this scheduling choice
     */
    constructor(taskId: Long?, value: T) {
        this.taskId = taskId
        this.value = value
        this.isBlockTask = false
        this.isBlockExecution = false
    }

    val isEnd: Boolean
        /**
         * Checks if this scheduling choice is the end of the schedule.
         *
         * @return true if it is the end of the schedule, false otherwise
         */
        get() = taskId == null && !isBlockTask && !isBlockExecution

    override fun toString(): String {
        return ("SchedulingChoice{"
                + "taskId="
                + taskId
                + ", isBlockTask="
                + isBlockTask
                + ", isBlockExecution="
                + isBlockExecution
                + '}')
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            SchedulingChoice::class.java
        )

        /**
         * Creates a scheduling choice that blocks a specific task.
         *
         * @param taskId the ID of the task to block
         * @return a SchedulingChoice that blocks the specified task
         */
        @JvmStatic
        fun blockTask(taskId: Long?): SchedulingChoice<*> {
            return SchedulingChoice<SchedulingChoiceValue>(taskId, true, false)
        }

        /**
         * Creates a scheduling choice that blocks execution.
         *
         * @return a SchedulingChoice that blocks execution
         */
        fun blockExecution(): SchedulingChoice<*> {
            return SchedulingChoice<SchedulingChoiceValue>(null, false, true)
        }

        /**
         * Creates a scheduling choice for a specific task without any value.
         *
         * @param taskId the ID of the task
         * @return a SchedulingChoice for the specified task
         */
        fun task(taskId: Long?): SchedulingChoice<*> {
            return SchedulingChoice<SchedulingChoiceValue>(taskId, false, false)
        }

        /**
         * Creates a scheduling choice for a specific task with a value.
         *
         * @param taskId the ID of the task
         * @param value the value associated with the scheduling choice
         * @param <T> the type of value associated with the scheduling choice
         * @return a SchedulingChoice for the specified task with the given value
        </T> */
        @JvmStatic
        fun <T : SchedulingChoiceValue?> task(taskId: Long?, value: T): SchedulingChoice<T> {
            return SchedulingChoice(taskId, value)
        }

        /**
         * Creates a scheduling choice that indicates the end of the task schedule.
         *
         * @return a SchedulingChoice that signifies the end of the schedule
         */
        fun end(): SchedulingChoice<*> {
            // Used to indicate the end of the task schedule. Since the events occur prior to
            // a scheduling choice, the guiding schedule needs to end with a dummy event that is popped
            // in
            // the end.
            return SchedulingChoice<SchedulingChoiceValue>(null, false, false)
        }
    }
}
