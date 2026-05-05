package org.mpi_sws.jmc.checker

/** Represents a target for JMC.  */
interface JmcTestTarget {
    /** Returns the name of the target.  */
    fun name(): String

    /** Invokes the target.  */
    fun invoke()
}
