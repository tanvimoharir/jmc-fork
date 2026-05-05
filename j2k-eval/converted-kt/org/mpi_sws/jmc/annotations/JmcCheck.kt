package org.mpi_sws.jmc.annotations

import org.junit.platform.commons.annotation.Testable

/** A mandatory annotation to mark a test method or class to be run with the JMC model checker.  */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS
)
@Retention(
    AnnotationRetention.RUNTIME
)
@Testable
annotation class JmcCheck 
