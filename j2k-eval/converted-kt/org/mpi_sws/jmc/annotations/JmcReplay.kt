package org.mpi_sws.jmc.annotations

/**
 * This annotation is used to mark a test method or class to be replayed in the JMC model checker.
 * It can be applied to methods or classes.
 *
 *
 * When applied, it indicates that the annotated test should be executed in a replay mode,
 * allowing the JMC model checker to replay previously recorded execution.
 *
 *
 * To be used when a bug is encountered in the model checking process.
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
annotation class JmcReplay 
