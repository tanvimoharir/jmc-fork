package org.mpi_sws.jmc.strategies.trust

import java.time.Duration

/**
 * Configuration class for the MeasureGraphCoverageStrategy.
 *
 *
 * This class provides a builder pattern to create instances of the configuration with various
 * options such as enabling debug mode, recording graphs, setting the record path, measuring
 * frequency, and whether to record per iteration.
 */
class MeasureGraphCoverageStrategyConfig private constructor() {
    var isDebugEnabled: Boolean = false
        private set
    private var recordGraphs = false
    var recordPath: String? = null
        private set
    var measuringFrequency: Duration? = null
        private set
    var isRecordPerIteration: Boolean = false
        private set

    fun shouldRecordGraphs(): Boolean {
        return recordGraphs
    }

    class MeasureGraphCoverageStrategyConfigBuilder {
        private var debug = false
        private var recordGraphs = false
        private var recordPath: String? = null
        private var measuringFrequency: Duration? = null
        private var recordPerIteration = false

        fun debug(debug: Boolean): MeasureGraphCoverageStrategyConfigBuilder {
            this.debug = debug
            return this
        }

        fun recordGraphs(recordGraphs: Boolean): MeasureGraphCoverageStrategyConfigBuilder {
            this.recordGraphs = recordGraphs
            return this
        }

        fun recordPath(recordPath: String?): MeasureGraphCoverageStrategyConfigBuilder {
            this.recordPath = recordPath
            return this
        }

        fun withFrequency(
            measuringFrequency: Duration?
        ): MeasureGraphCoverageStrategyConfigBuilder {
            this.measuringFrequency = measuringFrequency
            return this
        }

        fun recordPerIteration(): MeasureGraphCoverageStrategyConfigBuilder {
            this.recordPerIteration = true
            return this
        }

        fun build(): MeasureGraphCoverageStrategyConfig {
            require(!(this.recordPath == null || recordPath!!.isEmpty())) { "Record path cannot be null or empty" }
            require(!(this.measuringFrequency == null && !this.recordPerIteration)) { "Measuring frequency or record per iteration must be set" }
            require(!(this.measuringFrequency != null && this.recordPerIteration)) { "Measuring frequency and record per iteration cannot be used together" }
            val config = MeasureGraphCoverageStrategyConfig()
            config.isDebugEnabled = this.debug
            config.recordGraphs = this.recordGraphs
            config.recordPath = this.recordPath
            config.measuringFrequency = this.measuringFrequency
            config.isRecordPerIteration = this.recordPerIteration
            return config
        }
    }

    companion object {
        fun builder(): MeasureGraphCoverageStrategyConfigBuilder {
            return MeasureGraphCoverageStrategyConfigBuilder()
        }
    }
}
