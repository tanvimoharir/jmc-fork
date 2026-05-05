package org.mpi_sws.jmc.strategies.estimation.dag.absDag

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator
import org.mpi_sws.jmc.strategies.estimation.dag.absDag.AbsDagEstimationStrategy
import org.mpi_sws.jmc.util.FileUtil
import java.nio.file.Paths

class AbsDagEstimationStrategy : RandomSchedulingStrategy, EstimationStrategy {
    private val est: MetaGraphEstimator

    val estimatorCollector: StringBuilder = StringBuilder()

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    constructor(seed: Long?) : super(seed!!, "build/test-results/jmc-report") {
        // TODO : Fix the hard coded path
        est = AbsDagEstimator()
    }

    constructor(seed: Long, est: MetaGraphEstimator) : super(seed, "build/test-results/jmc-report") {
        // TODO : Fix the hard coded path
        this.est = est
    }

    /**
     * @param event
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(event: JmcRuntimeEvent) {
        super.updateEvent(event)
        val events = compileRuntimeEvent(event)
        est.updateEvent(events, activeTasks)
    }

    /**
     * @param iteration
     */
    override fun resetIteration(iteration: Int) {
        super.resetIteration(iteration)
        LOGGER.debug("Finished iteration {} with expected value: {}", iteration, est.expectedValue)
        estimatorCollector.append(est.expectedValue).append(System.lineSeparator())
        est.reset()
    }

    override fun teardown(report: JmcModelCheckerReport) {
        super.teardown(report)
        // TODO : Fix the hard coded path
        saveResults()
    }

    protected fun saveResults() {
        FileUtil.unsafeStoreToFile(
            Paths.get("build/test-results/jmc-report/", "AbsDagEstimateResult.txt").toString(),
            estimatorCollector.toString()
        )
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            AbsDagEstimationStrategy::class.java
        )
    }
}
