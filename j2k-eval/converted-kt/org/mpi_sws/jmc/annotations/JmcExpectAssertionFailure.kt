package org.mpi_sws.jmc.annotations

/**
 * This annotation is used to mark a test method or class to expect an assertion failure in the JMC
 * model checker. It can be applied to methods or classes.
 *
 *
 * When this annotation is present, the JMC model checker will expect an assertion failure during
 * the execution of the annotated test method or class.
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
annotation class JmcExpectAssertionFailure 
