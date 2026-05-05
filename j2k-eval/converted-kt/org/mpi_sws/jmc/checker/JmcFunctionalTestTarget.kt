package org.mpi_sws.jmc.checker

/**
 * A functional test target for JMC that allows invoking a target method.
 *
 *
 * This class implements the [JmcTestTarget] interface and provides a way to invoke a
 * target method with a specified name.
 */
class JmcFunctionalTestTarget(private val name: String, private val target: Target) :
    JmcTestTarget {
    override fun name(): String {
        return name
    }

    override fun invoke() {
        target.invoke()
    }

    /**
     * Represents a target for JMC.
     */
    fun interface Target {
        fun invoke()
    }
}
