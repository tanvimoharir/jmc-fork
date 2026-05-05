package org.mpi_sws.jmc.strategies.estimation.trust

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

open class TrustEstimationStrategy @JvmOverloads constructor(
    randomSeed: Long? = System.nanoTime(),
    policy: SchedulingPolicy? = SchedulingPolicy.FIFO,
    debug: Boolean = false,
    reportPath: String? = "build/test-results/jmc-report",
    tEst: TrustEstimator = TrustEstimator()
) :
    TrustStrategy(randomSeed!!, policy, debug, reportPath), EstimationStrategy {
    private val LOGGER: Logger = LogManager.getLogger(
        TrustEstimationStrategy::class.java
    )

    protected val tEst: TrustEstimator

    protected val estimatorCollector: StringBuilder = StringBuilder()

    protected val branchingCollector: StringBuilder = StringBuilder()

    init {
        if (policy == SchedulingPolicy.RANDOM) {
            LOGGER.warn(String.format("Random scheduling policy is %s", SchedulingPolicy.RANDOM.name))
        }
        this.tEst = tEst
    }

    /**
     * @param iteration the number of the iteration.
     * @param report
     */
    override fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        try {
            super.initIteration(iteration, report)
        } catch (e: HaltCheckerException) {
            if (e.isOkay && algoInstance.isStackEmpty) {
                LOGGER.debug("HaltCheckerException in initIteration: {}, clearing algoInstance", e.message)
                algoInstance.clear()
                estimatorCollector.append(tEst.getExpectedValue()).append(System.lineSeparator())
                branchingCollector.append(tEst.getTreeLogger().toString()).append(System.lineSeparator())
                branchingCollector.append("\$Iteration_").append(iteration).append(System.lineSeparator())
                tEst.reset()
            } else {
                LOGGER.error("HaltExecutionException in initIteration: {}", e.message)
                throw HaltExecutionException.Companion.ok()
            }
        } finally {
            tEst.resetReExecutionFlag()
        }
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
        if (!tEst.isReExecutionNeeded) {
            tEst.updateTree(algoInstance)
            if (!algoInstance.isStackEmpty && algoInstance.explorationStack.size() > 1) {
                throw HaltExecutionException.Companion.error("Exploration stack size exceeded 1")
            }
        }
    }

    /**
     * @return
     */
    override fun nextTask(): SchedulingChoice<*>? {
        if (tEst.isReExecutionNeeded) {
            LOGGER.debug("Re-execution needed, throwing HaltExecutionException")
            return SchedulingChoice.Companion.blockExecution()
        }
        return super.nextTask()
    }

    /**
     *
     */
    override fun teardown(report: JmcModelCheckerReport) {
        super.teardown(report)
        saveResults()
    }

    protected open fun saveResults() {
        FileUtil.unsafeStoreToFile(
            Paths.get("build/test-results/jmc-report/", "TrustEstimateResult.txt").toString(),
            estimatorCollector.toString()
        )
        FileUtil.unsafeStoreToFile(
            Paths.get("build/test-results/jmc-report/", "TrustBranchingResult.txt").toString(),
            branchingCollector.toString()
        )
    }
}
