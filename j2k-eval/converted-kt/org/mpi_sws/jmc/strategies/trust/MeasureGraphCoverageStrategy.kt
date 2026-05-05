package org.mpi_sws.jmc.strategies.trust

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.strategies.SchedulingStrategy
import org.mpi_sws.jmc.util.*
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * A scheduling strategy that measures the coverage of execution graphs during the model checking
 * process.
 *
 *
 * This strategy records the coverage of execution graphs and stores them in a specified path. It
 * can also measure the coverage per iteration or at a specified frequency.
 */
class MeasureGraphCoverageStrategy(
    private val schedulingStrategy: SchedulingStrategy?,
    private val config: MeasureGraphCoverageStrategyConfig
) :
    SchedulingStrategy {
    private val simulator = ExecutionGraphSimulator()

    private val visitedGraphs =
        ConcurrentHashMap<String, Int>()

    private val coveredGraphs: MutableSet<String> = HashSet()
    private var measuringThread: MeasuringThread? = null
    private val coverages = ArrayList<Int>()

    private var timeStart: Long = 0

    init {
        if (config.isRecordPerIteration) {
            this.measuringThread = null
        } else {
            this.measuringThread = MeasuringThread(this, config.measuringFrequency)
        }

        FileUtil.unsafeEnsurePath(config.recordPath)
    }

    private fun updateCoverage() {
        val `val` = coveredGraphs.size
        coverages.add(`val`)
    }

    private class MeasuringThread(
        private val strategy: MeasureGraphCoverageStrategy,
        private val measuringFrequency: Duration?
    ) :
        Thread() {
        private val future =
            CompletableFuture<Void?>()

        override fun run() {
            while (!future.isDone) {
                try {
                    sleep(measuringFrequency!!.toMillis())
                    strategy.updateCoverage()
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        fun stopMeasuring() {
            future.complete(null)
        }
    }

    @Throws(HaltCheckerException::class)
    override fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        if (iteration == 0) {
            this.timeStart = System.currentTimeMillis()
            if (!config.isRecordPerIteration) {
                measuringThread!!.start()
            }
        }
        simulator.reset()
        schedulingStrategy!!.initIteration(iteration, report)
    }

    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(event: JmcRuntimeEvent) {
        schedulingStrategy!!.updateEvent(event)
        simulator.updateEvent(event)
    }

    override fun nextTask(): SchedulingChoice<*>? {
        return schedulingStrategy!!.nextTask()
    }

    override fun resetIteration(iteration: Int) {
        schedulingStrategy!!.resetIteration(iteration)
        val executionGraph = simulator.executionGraph
        val coverageGraph = simulator.coverageGraph
        val json = executionGraph!!.toJsonStringIgnoreLocation()
        val coverage = coverageGraph.toString()
        // System.out.println(coverage);
        try {
            val hash = StringUtil.sha256Hash(json!!)
            val hashCoverage = StringUtil.sha256Hash(coverage)
            if (!coveredGraphs.contains(hashCoverage)) {
                coveredGraphs.add(hashCoverage)
                if (config.isDebugEnabled) {
                    FileUtil.unsafeStoreToFile(
                        Paths.get(config.recordPath, coveredGraphs.size.toString() + ".txt")
                            .toString(),
                        coverage
                    )
                }
            }
            if (visitedGraphs.containsKey(hash)) {
                visitedGraphs[hash] = visitedGraphs[hash]!! + 1
            } else {
                visitedGraphs[hash] = 1
                if (config.isDebugEnabled) {
                    FileUtil.unsafeStoreToFile(
                        Paths.get(config.recordPath, visitedGraphs.size.toString() + ".json")
                            .toString(),
                        json
                    )
                }
            }
            if (config.isRecordPerIteration) {
                updateCoverage()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun teardown(report: JmcModelCheckerReport) {
        if (!config.isRecordPerIteration) {
            measuringThread!!.stopMeasuring()
            try {
                measuringThread.join()
            } catch (e: InterruptedException) {
                LOGGER.error("Error while waiting for measuring thread to finish", e)
                return
            }
        }
        val timeDiff = System.currentTimeMillis() - timeStart
        val d = Duration.ofMillis(timeDiff)
        simulator.reset()
        schedulingStrategy!!.teardown(report)
        if (config.shouldRecordGraphs()) {
            val fileOutputStream =
                FileUtil.unsafeCreateFile(
                    Paths.get(config.recordPath, "hash_coverage.txt").toString()
                )
            if (fileOutputStream != null) {
                for ((key, value) in visitedGraphs) {
                    val sb = StringBuilder()
                    sb.append(key).append(": ").append(value).append("\n")
                    try {
                        fileOutputStream.write(sb.toString().toByteArray())
                    } catch (e: Exception) {
                        LOGGER.error("Error while writing to file", e)
                    }
                }
                try {
                    fileOutputStream.close()
                } catch (e: Exception) {
                    LOGGER.error("Error while closing file output stream", e)
                }
            } else {
                LOGGER.error("Failed to create file for hash coverage")
            }
        }
        val gson = Gson()
        val jsonArray = JsonArray()
        for (coverage in coverages) {
            jsonArray.add(coverage)
        }
        val jsonObject = JsonObject()
        jsonObject.addProperty("time", d.toMillis())
        jsonObject.add("coverage", jsonArray)
        val json = gson.toJson(jsonObject)
        FileUtil.unsafeStoreToFile(
            Paths.get(config.recordPath, "coverage.json").toString(), json
        )

        LOGGER.info("Covered graphs: {}", coveredGraphs.size)
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            MeasureGraphCoverageStrategy::class.java
        )
    }
}
