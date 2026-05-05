package org.mpi_sws.jmc.strategies

import org.mpi_sws.jmc.strategies.trust.TrustStrategy.SchedulingPolicy

/**
 * Configuration class for scheduling strategies.
 *
 *
 * This class encapsulates the configuration parameters for scheduling strategies, including
 * seed, trust scheduling policy, report path, and debug mode.
 *
 *
 * It provides a builder pattern for constructing instances of the configuration, allowing for
 * flexible and readable configuration of scheduling strategies.
 */
class SchedulingStrategyConfiguration private constructor() {
    var seed: Long? = null
        private set
    var trustSchedulingPolicy: SchedulingPolicy? = null
        private set
    var reportPath: String? = null
        private set
    var debug: Boolean = false
        private set
    var budget: Int = 0
        private set
    var solver: String? = null
        private set

    class Builder {
        private var seed: Long?
        private var trustSchedulingPolicy: SchedulingPolicy?
        private var reportPath: String?
        private var debug: Boolean
        private var budget: Int
        private var solver: String?

        init {
            this.seed = null
            this.trustSchedulingPolicy = SchedulingPolicy.RANDOM
            this.reportPath = "build/test-results/jmc-report"
            this.debug = false
            this.budget = 2
            this.solver = "off"
        }

        fun trustSchedulingPolicy(trustSchedulingPolicy: SchedulingPolicy?): Builder {
            this.trustSchedulingPolicy = trustSchedulingPolicy
            return this
        }

        fun reportPath(reportPath: String?): Builder {
            this.reportPath = reportPath
            return this
        }

        fun solver(solver: String?): Builder {
            this.solver = solver
            return this
        }

        fun debug(): Builder {
            this.debug = true
            return this
        }

        fun seed(seed: Long?): Builder {
            this.seed = seed
            return this
        }

        fun budget(budget: Int): Builder {
            require(budget >= 1) { "Budget must be at least 1" }
            this.budget = budget
            return this
        }

        fun build(): SchedulingStrategyConfiguration {
            val config = SchedulingStrategyConfiguration()
            config.seed = this.seed
            config.trustSchedulingPolicy = this.trustSchedulingPolicy
            config.reportPath = this.reportPath
            config.debug = this.debug
            config.budget = this.budget
            config.solver = this.solver
            return config
        }
    }

    fun interface SchedulingStrategyConstructor {
        fun create(config: SchedulingStrategyConfiguration?): SchedulingStrategy?
    }
}
