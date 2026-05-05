package org.mpi_sws.jmc.integrations.junit5.descriptors

/**
 * A JMC executable test descriptor for JUnit 5.
 *
 *
 * This interface defines a contract for executing JMC checks as part of the JUnit 5 test
 * lifecycle. Implementations of this interface should provide the logic to execute the JMC checks.
 */
interface JmcExecutableTestDescriptor {
    @Throws(Exception::class)
    fun execute()
}
