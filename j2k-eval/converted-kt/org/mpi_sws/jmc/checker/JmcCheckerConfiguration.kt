package org.mpi_sws.jmc.checker

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.checker.exceptions.JmcInvalidConfigurationException
import org.mpi_sws.jmc.runtime.JmcRuntimeConfiguration
import org.mpi_sws.jmc.strategies.JmcInvalidStrategyException
import org.mpi_sws.jmc.strategies.SchedulingStrategy
import org.mpi_sws.jmc.strategies.SchedulingStrategyConfiguration
import org.mpi_sws.jmc.strategies.SchedulingStrategyConfiguration.SchedulingStrategyConstructor
import org.mpi_sws.jmc.strategies.SchedulingStrategyFactory
import org.mpi_sws.jmc.strategies.trust.TrustStrategy.SchedulingPolicy
import java.time.Duration

/**
 * Configuration for the JMC checker.
 *
 *
 * This class encapsulates the configuration parameters for running the JMC checker, including
 * the number of iterations, strategy type, debug mode, report path, seed, and timeout.
 *
 *
 * Use the [JmcCheckerConfiguration.Builder] to create a configuration instance.
 */
class JmcCheckerConfiguration private constructor() {
    /**
     * Returns the number of iterations to run the checker.
     *
     * @return the number of iterations
     */
    var numIterations: Int? = null
        private set

    /**
     * Returns the type of scheduling strategy to be used.
     *
     * @return the strategy type as a string
     */
    var strategyType: String? = null
        private set

    private var solver: String? = null

    private var strategyConstructor: SchedulingStrategyConstructor? = null

    /**
     * Returns the debug mode status.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    var debug: Boolean = false
        private set

    private var seed: Long? = null

    private var budget = 0

    /**
     * Returns the path where the report will be saved.
     *
     * @return the report path as a string
     */
    var reportPath: String? = null
        private set

    /**
     * Returns the timeout duration for the checker.
     *
     * @return the timeout duration, or null if no timeout is set
     */
    var timeout: Duration? = null
        private set

    private var schedulingPolicy: SchedulingPolicy? = null

    fun getSolver(): String? {
        return solver
    }

    /**
     * Returns the seed for the checker.
     *
     * @return the seed, or null if no seed is set
     */
    fun getSeed(): Long? {
        return seed
    }

    fun getBudget(): Int {
        return budget
    }

    /**
     * Sets the seed for the checker.
     *
     * @param seed the seed to set.
     */
    fun setSeed(seed: Long?) {
        this.seed = seed
    }

    fun setSolver(solver: String?) {
        this.solver = solver
    }

    fun setBudget(budget: Int) {
        this.budget = budget
    }

    fun setSchedulingPolicy(schedulingPolicy: SchedulingPolicy?) {
        this.schedulingPolicy = schedulingPolicy
    }

    fun getSchedulingPolicy(): SchedulingPolicy? {
        return schedulingPolicy
    }

    /**
     * Converts this configuration to a runtime configuration.
     *
     * @return a [JmcRuntimeConfiguration] based on this configuration
     * @throws JmcInvalidStrategyException if the strategy type is invalid or the strategy cannot be
     * created
     */
    @Throws(JmcInvalidStrategyException::class)
    fun toRuntimeConfiguration(): JmcRuntimeConfiguration {
        val strategy: SchedulingStrategy?
        val strategyConfigurationBuilder =
            SchedulingStrategyConfiguration.Builder().seed(seed).budget(budget).solver(solver)
                .trustSchedulingPolicy(schedulingPolicy)
        if (debug) {
            strategyConfigurationBuilder.debug()
            strategyConfigurationBuilder.reportPath(reportPath)
        }
        strategy = if (strategyConstructor != null) {
            strategyConstructor!!.create(strategyConfigurationBuilder.build())
        } else {
            SchedulingStrategyFactory.createSchedulingStrategy(
                strategyType!!, strategyConfigurationBuilder.build()
            )
        }
        if (strategy == null) {
            throw JmcInvalidStrategyException("Strategy is null")
        }
        return JmcRuntimeConfiguration.Builder()
            .strategy(strategy)
            .debug(debug)
            .reportPath(reportPath)
            .build()
    }

    /**
     * Builder for JmcCheckerConfiguration
     */
    class Builder {
        private var numIterations = 0

        private var strategyType = "random"

        private var solver = "off"

        private var strategyConstructor: SchedulingStrategyConstructor? = null

        private var debug = false

        private var reportPath = "build/test-results/jmc-report"

        private var timeout: Duration? = null

        private var seed: Long

        private var budget = 2

        private var schedulingPolicy: SchedulingPolicy

        init {
            this.schedulingPolicy = SchedulingPolicy.RANDOM
            this.seed = System.nanoTime()
        }

        fun numIterations(numIterations: Int): Builder {
            this.numIterations = numIterations
            return this
        }

        fun strategyType(strategyType: String): Builder {
            this.strategyType = strategyType
            return this
        }

        fun solver(solver: String): Builder {
            this.solver = solver
            return this
        }

        fun strategyConstructor(
            strategyConstructor: SchedulingStrategyConstructor?
        ): Builder {
            this.strategyConstructor = strategyConstructor
            return this
        }

        fun debug(debug: Boolean): Builder {
            this.debug = debug
            return this
        }

        fun reportPath(bugsPath: String): Builder {
            this.reportPath = bugsPath
            return this
        }

        fun seed(seed: Long): Builder {
            this.seed = seed
            return this
        }

        fun budget(budget: Int): Builder {
            this.budget = budget
            return this
        }

        fun timeout(timeout: Duration?): Builder {
            this.timeout = timeout
            return this
        }

        fun timeout(timeout: Long): Builder {
            if (timeout < 0L) {
                this.timeout = null
                return this
            }
            this.timeout = Duration.ofMillis(timeout)
            return this
        }

        fun schedulingPolicy(schedulingPolicy: SchedulingPolicy): Builder {
            this.schedulingPolicy = schedulingPolicy
            return this
        }

        @Throws(JmcInvalidConfigurationException::class)
        fun build(): JmcCheckerConfiguration {
            if (numIterations == 0 && timeout == null) {
                throw JmcInvalidConfigurationException(
                    "Either numIterations or timeout must be set"
                )
            }
            LOGGER.info("Using seed: {}", seed)
            val config = JmcCheckerConfiguration()
            config.numIterations = numIterations
            config.strategyType = strategyType
            config.strategyConstructor = strategyConstructor
            config.debug = debug
            config.reportPath = reportPath
            config.solver = solver
            config.seed = seed
            config.budget = budget
            config.timeout = timeout
            config.schedulingPolicy = schedulingPolicy
            return config
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcCheckerConfiguration::class.java
        )

        /**
         * Creates a JmcCheckerConfiguration from the given annotation.
         *
         * @param annotation the JmcCheckConfiguration annotation
         * @return a JmcCheckerConfiguration instance
         * @throws JmcCheckerException if the configuration is invalid
         */
        @Throws(JmcCheckerException::class)
        fun fromAnnotation(annotation: JmcCheckConfiguration): JmcCheckerConfiguration {
            if (!SchedulingStrategyFactory.isValidStrategy(annotation.strategy)) {
                throw JmcInvalidStrategyException("Invalid strategy: " + annotation.strategy)
            }
            return Builder()
                .numIterations(annotation.numIterations)
                .strategyType(annotation.strategy)
                .solver(annotation.solver)
                .debug(annotation.debug)
                .reportPath(annotation.reportPath)
                .seed(annotation.seed)
                .budget(annotation.budget)
                .timeout(annotation.timeout)
                .schedulingPolicy(annotation.schedulingPolicy)
                .build()
        }
    }
}
