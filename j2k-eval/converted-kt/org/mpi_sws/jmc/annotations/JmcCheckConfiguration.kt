package org.mpi_sws.jmc.annotations

import org.mpi_sws.jmc.strategies.trust.TrustStrategy.SchedulingPolicy

/**
 * Configuration annotation for JMC checks.
 *
 *
 * The annotation allows users to specify parameters for the tests and is mandatory
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS
)
@Retention(
    AnnotationRetention.RUNTIME
)
annotation class JmcCheckConfiguration(
    /**
     * The strategy to use for the JMC check.
     *
     *
     * Available strategies include:
     *
     *
     *  * `random` - Randomly explores the state space.
     *  * `trust` - Uses Trust to exhaustively enumerate all executions.
     *
     *
     * @return the strategy name
     */
    val strategy: String = "random",
    val solver: String = "off",
    val schedulingPolicy: SchedulingPolicy = SchedulingPolicy.RANDOM,
    /**
     * The number of iterations to run for the JMC check.
     *
     *
     * Either this parameter or a [JmcTimeout] annotation should be specified for each test
     *
     * @return the number of iterations
     */
    val numIterations: Int = 0,
    /**
     * Enables debug logs and additional information based on the strategy used.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    val debug: Boolean = false,
    /**
     * The path where the JMC report will be generated.
     *
     *
     * By default, the report is generated in "build/test-results/jmc-report".
     *
     * @return the report path
     */
    val reportPath: String = "build/test-results/jmc-report",
    /**
     * The seed for the random number generator used in the JMC check.
     *
     *
     * By default, the seed is set to 0, which means a new random seed will be created at
     * runtime.
     *
     * @return the seed value
     */
    val seed: Long = 0,
    val budget: Int = 2,
    val timeout: Long = -1L
)
