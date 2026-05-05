package org.mpi_sws.jmc.annotations

/**
 * This annotation is used to mark methods or classes that should be ignored by the JMC instrumentation.
 * It can be applied to classes only.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JmcIgnoreInstrumentation 
