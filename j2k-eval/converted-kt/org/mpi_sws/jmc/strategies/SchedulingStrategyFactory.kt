package org.mpi_sws.jmc.strategies

import org.mpi_sws.jmc.strategies.estimation.dag.DagEstimationStrategy
import org.mpi_sws.jmc.strategies.estimation.dag.absDag.AbsDagEstimationStrategy
import org.mpi_sws.jmc.strategies.estimation.dag.fjDag.FjDagEstimationStrategy
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimationStrategy
import org.mpi_sws.jmc.strategies.estimation.trust.testor.TestorStrategy
import org.mpi_sws.jmc.strategies.estimation.trust.wgTrust.WgTrustEstimationStrategy
import org.mpi_sws.jmc.strategies.trust.*

/**
 * Factory class for creating scheduling strategies.
 */
object SchedulingStrategyFactory {
    // Set of valid strategies
    private val validStrategies: MutableSet<String> = HashSet()

    init {
        validStrategies.add("random")
        validStrategies.add("trust")
        validStrategies.add("dag-estimation")
        validStrategies.add("abs-dag-estimation")
        validStrategies.add("fj-dag-estimation")
        validStrategies.add("trust-estimation")
        validStrategies.add("wg-trust-estimation")
        validStrategies.add("testor")
    }

    /**
     * Creates a new scheduling strategy.
     *
     * @param name   the name of the strategy
     * @param config the configuration for the strategy
     * @return the scheduling strategy
     */
    @Throws(JmcInvalidStrategyException::class)
    fun createSchedulingStrategy(
        name: String, config: SchedulingStrategyConfiguration
    ): SchedulingStrategy? {
        if (!isValidStrategy(name)) {
            throw JmcInvalidStrategyException("Invalid strategy: $name")
        }
        if (name == "random") {
            return RandomSchedulingStrategy(config.seed, config.reportPath)
        } else if (name == "trust") {
            return TrustStrategy(
                config.seed,
                config.trustSchedulingPolicy,
                config.debug,
                config.reportPath,
                config.solver
            )
        } else if (name == "dag-estimation") {
            return DagEstimationStrategy(config.seed)
        } else if (name == "abs-dag-estimation") {
            return AbsDagEstimationStrategy(config.seed)
        } else if (name == "fj-dag-estimation") {
            return FjDagEstimationStrategy(config.seed)
        } else if (name == "trust-estimation") {
            return TrustEstimationStrategy(
                config.seed,
                config.trustSchedulingPolicy,
                config.debug,
                config.reportPath
            )
        } else if (name == "wg-trust-estimation") {
            return WgTrustEstimationStrategy(
                config.seed,
                config.trustSchedulingPolicy,
                config.debug,
                config.reportPath
            )
        } else if (name == "testor") {
            return TestorStrategy(
                config.seed,
                config.trustSchedulingPolicy,
                config.debug,
                config.reportPath,
                config.budget
            )
        }
        return null
    }

    /**
     * Checks if a strategy is valid.
     *
     * @param name the name of the strategy
     * @return true if the strategy is valid, false otherwise
     */
    fun isValidStrategy(name: String): Boolean {
        return validStrategies.contains(name)
    }
}
