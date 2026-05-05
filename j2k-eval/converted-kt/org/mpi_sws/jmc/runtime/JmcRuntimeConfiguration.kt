package org.mpi_sws.jmc.runtime

import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy
import org.mpi_sws.jmc.strategies.SchedulingStrategy

/**
 * Represents the configuration for the JMC runtime.
 *
 *
 * This class encapsulates various settings that control the behavior of the JMC runtime,
 * including scheduling strategies, debugging options, report paths, and retry configurations.
 *
 *
 * Use the [JmcRuntimeConfiguration.Builder] to create a configuration instance.
 *
 *
 * The user does not have to specify this explicitly. The [ ] provided is used to create and instance of this
 * class
 */
class JmcRuntimeConfiguration private constructor() {
    var strategy: SchedulingStrategy? = null
        private set

    var debug: Boolean? = null
        private set

    var reportPath: String? = null
        private set

    var schedulerTries: Int = 10
        private set

    var schedulerTrySleepTimeNanos: Long = 100
        private set

    class Builder {
        private var strategy: SchedulingStrategy
        private var debug: Boolean
        private var reportPath: String?
        private var schedulerTries: Int
        private var schedulerTrySleepTimeNanos: Long

        init {
            this.strategy =
                RandomSchedulingStrategy(
                    System.nanoTime(), "build/test-results/jmc-report"
                )
            this.debug = false
            this.reportPath = "build/test-results/jmc-report"
            this.schedulerTries = 10
            this.schedulerTrySleepTimeNanos = 100
        }

        fun strategy(strategy: SchedulingStrategy): Builder {
            this.strategy = strategy
            return this
        }

        fun debug(debug: Boolean): Builder {
            this.debug = debug
            return this
        }

        fun reportPath(reportPath: String?): Builder {
            this.reportPath = reportPath
            return this
        }

        fun schedulerTries(schedulerTries: Int): Builder {
            this.schedulerTries = schedulerTries
            return this
        }

        fun schedulerTrySleepTimeNanos(schedulerTrySleepTimeNanos: Long): Builder {
            this.schedulerTrySleepTimeNanos = schedulerTrySleepTimeNanos
            return this
        }

        fun build(): JmcRuntimeConfiguration {
            val config = JmcRuntimeConfiguration()
            config.strategy = strategy
            config.debug = debug
            config.reportPath = reportPath
            config.schedulerTries = schedulerTries
            config.schedulerTrySleepTimeNanos = schedulerTrySleepTimeNanos
            return config
        }
    }
}
