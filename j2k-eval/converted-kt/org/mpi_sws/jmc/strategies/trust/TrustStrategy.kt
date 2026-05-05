package org.mpi_sws.jmc.strategies.trust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy
import org.mpi_sws.jmc.strategies.tracker.*
import org.mpi_sws.jmc.util.FileUtil
import java.nio.file.Paths
import java.util.*
import java.util.List
import kotlin.collections.MutableList

/**
 * A wrapper around the [Algo] algorithm that implements a scheduling strategy based on trust.
 * The class implements the [ReplayableSchedulingStrategy] and [ ] and uses the [TrackActiveTasksStrategy] to
 * track active tasks during the execution.
 */
open class TrustStrategy : TrackActiveTasksStrategy, ReplayableSchedulingStrategy {
    private val LOGGER: Logger = LogManager.getLogger(TrustStrategy::class.java)

    protected val algoInstance: Algo
    private val policy: SchedulingPolicy?
    private val random: Random

    private val debug: Boolean
    private val reportPath: String?
    private var recordedTrace: MutableList<SchedulingChoice<*>?>?

    @JvmOverloads
    constructor(
        randomSeed: Long = System.nanoTime(),
        policy: SchedulingPolicy? = SchedulingPolicy.FIFO,
        debug: Boolean = false,
        reportPath: String? = "build/test-results/jmc-report"
    ) : super(
        List.of<Tracker>(TrackTasks())
    ) {
        this.random = Random(randomSeed)
        this.algoInstance = Algo(false, "off")
        this.policy = policy
        this.debug = debug
        this.reportPath = reportPath
        this.recordedTrace = null
    }

    constructor(
        randomSeed: Long,
        policy: SchedulingPolicy?,
        debug: Boolean,
        reportPath: String?,
        solver: String?
    ) : super(
        List.of<Tracker>(TrackTasks())
    ) {
        this.random = Random(randomSeed)
        this.algoInstance = Algo(false, solver)
        this.policy = policy
        this.debug = debug
        this.reportPath = reportPath
        this.recordedTrace = null
    }

    constructor(
        randomSeed: Long,
        policy: SchedulingPolicy?,
        debug: Boolean,
        reportPath: String?,
        hasTreeLogger: Boolean,
        solver: String?
    ) : super(
        List.of<Tracker>(TrackTasks())
    ) {
        this.random = Random(randomSeed)
        this.algoInstance = Algo(hasTreeLogger, solver)
        this.policy = policy
        this.debug = debug
        this.reportPath = reportPath
        this.recordedTrace = null
    }

    override fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        super.initIteration(iteration, report)
        algoInstance.initIteration(iteration, report)
        if (debug) {
            algoInstance.writeExecutionGraphToFile(
                Paths.get(this.reportPath, "iteration-guiding-$iteration.json")
                    .toString()
            )
        }
    }

    override fun nextTask(): SchedulingChoice<*>? {
        if (recordedTrace != null) {
            // If we have a recorded trace, return the next task from it
            val next = recordedTrace!!.removeAt(0)
            LOGGER.debug("Returning recorded task: {}", next)
            if (next!!.isEnd) {
                // If we are at the end event only the main thread (1) needs to be active and
                // continue.
                // For sanity, we check that the set of active tasks contains only the main thread.
                val activeTasks = activeTasks
                if (activeTasks.size != 1 || !activeTasks.contains(1L)) {
                    LOGGER.error(
                        "End of trace reached but active tasks are not as expected: {}",
                        activeTasks
                    )
                    throw RuntimeException(
                        "End of trace reached but active tasks are not as expected: "
                                + activeTasks
                    )
                }
                return SchedulingChoice.Companion.task(1L) // Return task ID 1 for end of trace
            }
            return next
        }

        // Always add 1 to the return value the strategy expects 1-indexed tasks but we store
        // 0-indexed tasks

        // Otherwise, return an active, schedule-able task based on the policy
        val activeTasks = activeTasks
        // If the algorithm has a task to execute, return it
        val nextTask = algoInstance.nextTask()
        if (nextTask != null) {
            if (!activeTasks.contains(nextTask.taskId)) {
                LOGGER.debug("Guiding trace led us to a task that is not active: {}", nextTask)
            }
            return nextTask
        }

        val activeScheduleAbleTasks =
            algoInstance.schedulableTasks.stream() // Adding 1 here for all further uses of the task ID
                .map { t: Long? -> t!! + 1 }
                .filter { o: Long? -> activeTasks.contains(o) }
                .toList()

        // If the policy is FIFO, return the first active, schedule-able task
        return SchedulingChoice.Companion.task(
            when (policy) {
                SchedulingPolicy.FIFO -> if (activeScheduleAbleTasks.isEmpty())
                    null
                else
                    activeScheduleAbleTasks[0]

                SchedulingPolicy.LIFO -> if (activeScheduleAbleTasks.isEmpty())
                    null
                else
                    activeScheduleAbleTasks[activeScheduleAbleTasks.size - 1]

                SchedulingPolicy.RANDOM -> {
                    val size = activeScheduleAbleTasks.size
                    if (size == 0) null else activeScheduleAbleTasks[random.nextInt(size)]
                }
            }
        )
    }

    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(event: JmcRuntimeEvent) {
        super.updateEvent(event)
        if (recordedTrace != null && !recordedTrace!!.isEmpty()) {
            // If we are replaying a recorded trace, we do not update the algorithm with new events
            LOGGER.debug("Skipping event update during trace replay: {}", event)
            return
        }
        val trustEvents = EventFactory.fromRuntimeEvent(event)
        for (e in trustEvents) {
            LOGGER.debug("Received event: {}", e)
            try {
                algoInstance.updateEvent(e!!)
            } catch (ex: Exception) {
                LOGGER.error("Failed to update event: {}", e, ex)
                throw RuntimeException(ex)
            }
        }
    }

    override fun resetIteration(iteration: Int) {
        resetIteration(iteration, true)
    }

    protected fun resetIteration(iteration: Int, checkConsistency: Boolean) {
        LOGGER.debug("Resetting iteration {} with clearGraph={}", iteration, checkConsistency)
        super.resetIteration(iteration)
        if (debug) {
            algoInstance.logStackState()
            if (checkConsistency && !algoInstance.executionGraph.checkExtensiveConsistency()) {
                throw HaltCheckerException.Companion.error("Explored an inconsistent execution graph")
            }
            algoInstance.writeExecutionGraphToFile(
                Paths.get(this.reportPath, "iteration-complete-$iteration.json")
                    .toString()
            )
        }
    }

    val executionGraph: ExecutionGraph?
        get() = algoInstance.executionGraph

    override fun teardown(report: JmcModelCheckerReport) {
        super.teardown(report)
        algoInstance.teardown(report)
        val tLogger = algoInstance.treeLog
        val inConGraphLogger = algoInstance.inconsistentGraphLog
        val blockedGraphLogger = algoInstance.blockedGraphLog
        val leafSizeLogger = algoInstance.leafSizeLog
        if (tLogger != null) {
            recordTreeLoggger(tLogger, inConGraphLogger, blockedGraphLogger, leafSizeLogger)
        }
    }

    private fun recordTreeLoggger(
        tLogger: StringBuilder,
        inConGraphLogger: StringBuilder?,
        blockedGraphLogger: StringBuilder?,
        leafSizeLogger: StringBuilder?
    ) {
        if (inConGraphLogger != null) {
            tLogger.append(System.lineSeparator()).append("\$INCONSISTENT GRAPH:").append(System.lineSeparator())
                .append(inConGraphLogger)
        }
        if (blockedGraphLogger != null) {
            tLogger.append(System.lineSeparator()).append("\$BLOCKED GRAPH:").append(System.lineSeparator())
                .append(blockedGraphLogger)
        }
        if (leafSizeLogger != null) {
            tLogger.append(System.lineSeparator()).append("\$LEAF SIZE LOG:").append(System.lineSeparator())
                .append(leafSizeLogger)
        }
        val filePath = Paths.get(this.reportPath, "trust-tree-logger.txt").toString()
        LOGGER.info("Recording tree logger to {}", filePath)
        FileUtil.unsafeStoreToFile(filePath, tLogger.toString())
    }

    @Throws(JmcCheckerException::class)
    override fun recordTrace() {
        val filePath = Paths.get(this.reportPath, "replay.json").toString()
        LOGGER.info("Recording trace to {}", filePath)
        algoInstance.recordTaskSchedule(filePath)
    }

    @Throws(JmcCheckerException::class)
    override fun replayRecordedTrace() {
        recordedTrace =
            FileUtil.readTaskSchedule(Paths.get(this.reportPath, "replay.json").toString())
    }

    enum class SchedulingPolicy {
        FIFO,
        RANDOM,
        LIFO,  // TODO : add RR
    }
}
