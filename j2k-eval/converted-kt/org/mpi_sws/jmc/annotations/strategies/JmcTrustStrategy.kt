package org.mpi_sws.jmc.annotations.strategies

import org.mpi_sws.jmc.annotations.JmcCheckConfiguration
import org.mpi_sws.jmc.strategies.trust.TrustStrategy.SchedulingPolicy

/**
 * This annotation is used to configure the JMC trust strategy for a test method or class. It allows
 * specifying the scheduling policy, seed, debug mode, and report path for the trust strategy.
 *
 *
 * It can be applied to methods or classes and is equivalent to using the [ ][JmcCheckConfiguration.strategy] with value "trust".
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
annotation class JmcTrustStrategy(
    /**
     * The seed for the scheduling strategy.
     */
    val seed: Long = 0,
    /**
     * The scheduling policy for the trust strategy.
     *
     *
     * - RANDOM: Randomly selects a thread to schedule. - FIFO: Selects the thread that has been
     * waiting the longest.
     */
    val schedulingPolicy: SchedulingPolicy = SchedulingPolicy.RANDOM,
    /**
     * Debug flag to enable graph logging.
     */
    val debug: Boolean = false,
    /**
     * The path to store the execution graphs explored.
     */
    val reportPath: String = "build/test-results/jmc-report",
    val loggerTree: Boolean = false,
    val solver: String = "off"
)
