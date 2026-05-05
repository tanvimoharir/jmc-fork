package org.mpi_sws.jmc.annotations.strategies

import java.time.temporal.ChronoUnit

/**
 * This annotation is used to configure the JMC graph coverage measurement for a test method or
 * class.
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
annotation class JmcMeasureGraphCoverage(
    /** Enable debug mode for the graph coverage measurement.  */
    val debug: Boolean = false,
    /** Enable recording of the execution graphs.  */
    val recordGraphs: Boolean = false,
    /**
     * The path where the execution graphs will be recorded.
     *
     *
     * Default is "build/test-results/jmc-coverage".
     */
    val recordPath: String = "build/test-results/jmc-report",
    /**
     * The frequency at which the graph coverage will be measured.
     *
     *
     * Default is null.
     */
    val recordUnit: ChronoUnit = ChronoUnit.SECONDS,
    /**
     * The frequency at which the graph coverage will be measured, in milliseconds.
     *
     *
     * Should be specified with the [JmcMeasureGraphCoverage.recordUnit] parameter
     *
     *
     * Default is null.
     */
    val recordFrequency: Long = 0L,
    /**
     * Record the graph coverage per iteration of the test. Should not be specified with
     * `recordUnit` and `recordFrequency`
     */
    val recordPerIteration: Boolean = false
)
