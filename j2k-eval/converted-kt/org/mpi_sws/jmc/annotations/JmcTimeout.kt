package org.mpi_sws.jmc.annotations

import java.time.temporal.ChronoUnit

/**
 * This annotation is used to specify a timeout for a test method or class when using the JMC model
 * checker. It can be applied to methods or classes.
 *
 *
 * The timeout value is specified in the specified time unit, and if the test exceeds this
 * duration, it will be considered failed.
 *
 *
 * Either this or the [JmcCheckConfiguration.numIterations] should be specified
 * mandatorily for each test
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
annotation class JmcTimeout(
    /**
     * The timeout value for the annotated test method or class.
     *
     *
     * This value is used to determine how long the test should run before it is considered
     * failed due to timeout.
     *
     * @return the timeout value
     */
    val value: Long,
    /**
     * The time unit for the timeout value.
     *
     *
     * This specifies the unit of time for the timeout value, such as seconds, milliseconds, etc.
     *
     * @return the time unit for the timeout
     */
    val unit: ChronoUnit = ChronoUnit.SECONDS
)
