package org.mpi_sws.jmc.strategies.estimation.dag.fjDag

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy
import org.mpi_sws.jmc.util.FileUtil
import java.nio.file.Paths
import java.util.random.RandomGenerator
import java.util.random.RandomGeneratorFactory

class FjDagEstimationStrategy(seed: Long?) : RandomSchedulingStrategy(seed!!, "build/test-results/jmc-report"),
    EstimationStrategy {
    private val LOGGER: Logger = LogManager.getLogger(
        FjDagEstimationStrategy::class.java
    )

    // TODO : Fix the hard coded path
    private val est = FjDagEstimator()

    val estimatorCollector: StringBuilder = StringBuilder()


    override fun nextTask(): SchedulingChoice<*>? {
        val activeThreads = activeTasks
        val taskToSchedule: Long
        if (activeThreads.isEmpty()) {
            return null
        }
        if (activeThreads.size == 1) {
            taskToSchedule = activeThreads.toTypedArray()[0] as Long
        } else {
            if (!est.isForkComplete) {
                if (!activeThreads.contains(1L)) {
                    LOGGER.error("Main task is not active, something went wrong!")
                    throw HaltCheckerException.Companion.error("Main task is not active, something went wrong!")
                }
                // Force scheduling the main task to complete the forking of all tasks
                taskToSchedule = 1L
            } else {
                // At this point we have multiple active threads, and the fork is complete, we must forbid
                // scheduling the main task if it is still active
                if (activeThreads.contains(1L) && activeThreads.size > 1) {
                    activeThreads.remove(1L)
                }
                val index = RandomGeneratorFactory.of<RandomGenerator>("Xoshiro256PlusPlus").create()
                    .nextInt(activeThreads.size)
                taskToSchedule = activeThreads.toTypedArray()[index] as Long
            }
        }
        return makeSchedulingChoice(taskToSchedule)
    }

    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(event: JmcRuntimeEvent) {
        super.updateEvent(event)
        val events = compileRuntimeEvent(event)
        est.updateEvent(events, activeTasks)
    }

    override fun resetIteration(iteration: Int) {
        super.resetIteration(iteration)
        LOGGER.debug("Finished iteration {} with expected value: {}", iteration, est.getExpectedValue())
        estimatorCollector.append(est.getExpectedValue()).append(System.lineSeparator())
        est.reset()
    }

    override fun teardown(report: JmcModelCheckerReport) {
        super.teardown(report)
        // TODO : Fix the hard coded path
        saveResults()
    }

    protected fun saveResults() {
        FileUtil.unsafeStoreToFile(
            Paths.get("build/test-results/jmc-report/", "FjDagEstimateResult.txt").toString(),
            estimatorCollector.toString()
        )
    }
}
