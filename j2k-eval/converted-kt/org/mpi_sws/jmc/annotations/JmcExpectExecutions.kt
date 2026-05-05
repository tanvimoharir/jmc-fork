package org.mpi_sws.jmc.annotations

/**
 * This annotation is used to mark a test method or class to expect a certain number of executions
 * in the JMC model checker. It can be applied to methods or classes.
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
annotation class JmcExpectExecutions(
    /**
     * The expected number of executions for the annotated test method or class.
     *
     *
     * This value is used to verify that the JMC model checker produces the expected number of
     * executions during the test run.
     *
     * @return the expected number of executions
     */
    val value: Int
)
