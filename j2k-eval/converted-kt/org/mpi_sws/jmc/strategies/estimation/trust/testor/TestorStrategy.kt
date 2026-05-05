package org.mpi_sws.jmc.strategies.estimation.trust.testor

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy
import org.mpi_sws.jmc.strategies.trust.*
import org.mpi_sws.jmc.util.FileUtil
import java.nio.file.Paths

class TestorStrategy @JvmOverloads constructor(
    randomSeed: Long? = System.nanoTime(),
    policy: SchedulingPolicy? = SchedulingPolicy.FIFO,
    debug: Boolean = false,
    reportPath: String? = "build/test-results/jmc-report",
    budget: Int = 2
) :
    TrustStrategy(randomSeed!!, policy, debug, reportPath), EstimationStrategy {
    private val LOGGER: Logger = LogManager.getLogger(TestorStrategy::class.java)
    protected val testor: Testor
    protected val estimatorCollector: StringBuilder = StringBuilder()

    init {
        if (policy == SchedulingPolicy.RANDOM) {
            LOGGER.warn(String.format("Random scheduling policy is %s", SchedulingPolicy.RANDOM.name))
        }
        this.testor = Testor(budget)
    }

    /**
     * @param iteration the number of the iteration.
     * @param report
     */
    override fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        try {
            super.initIteration(iteration, report)
        } catch (e: HaltCheckerException) {
            if (e.isOkay && algoInstance.isStackEmpty && testor.isDone) {
                recordEstimation(iteration)
                algoInstance.clear()
                testor.reset()
            } else if (e.isOkay && algoInstance.isStackEmpty) {
                resumeWithNextOption(iteration, report)
            } else {
                LOGGER.error("HaltExecutionException in initIteration: {}", e.message)
                throw HaltExecutionException.Companion.ok()
            }
        } finally {
            testor.resetReExecutionFlag()
        }
    }

    private fun resumeWithNextOption(iteration: Int, report: JmcModelCheckerReport) {
        while (!testor.isDone) {
            try {
                testor.updateStack(algoInstance)
                algoInstance.initIteration(iteration, report)
                return
            } catch (e: HaltCheckerException) {
                LOGGER.debug(e.message)
            }
        }
        recordEstimation(iteration)
        algoInstance.clear()
        testor.reset()
    }

    private fun recordEstimation(iteration: Int) {
        estimatorCollector.append(testor.realExpectedValue).append(System.lineSeparator())
    }

    /**
     * @param iteration
     */
    override fun resetIteration(iteration: Int) {
        resetIteration(iteration, false)
    }

    /**
     * @param event
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(event: JmcRuntimeEvent) {
        super.updateEvent(event)
        if (!testor.isReExecutionNeeded) {
            testor.updateTree(algoInstance)
        }
        if (event.taskId == 1L && event.type == JmcRuntimeEvent.Type.FINISH_EVENT) {
            if (!testor.isDone) {
                throw HaltExecutionException.Companion.reexecutionNeeded()
            }
        }
    }

    /**
     * @return
     */
    override fun nextTask(): SchedulingChoice<*>? {
        if (testor.isReExecutionNeeded) {
            LOGGER.debug("Re-execution needed, returning null to trigger re-execution")
            return SchedulingChoice.Companion.blockExecution()
        }
        return super.nextTask()
    }

    /**
     * @param report
     */
    override fun teardown(report: JmcModelCheckerReport) {
        super.teardown(report)
        saveResults()
    }

    protected fun saveResults() {
        FileUtil.unsafeStoreToFile(
            Paths.get("build/test-results/jmc-report/", "TestorEstimateResult.txt").toString(),
            estimatorCollector.toString()
        )
    }
}
