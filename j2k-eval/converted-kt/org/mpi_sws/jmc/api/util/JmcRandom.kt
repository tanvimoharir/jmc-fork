package org.mpi_sws.jmc.api.util

import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue
import java.util.*

/**
 * A JMC-specific implementation of java.util.Random that allows for model checking. This class
 * overrides the next method to yield control to the JMC runtime, allowing for reactive event
 * handling and model checking.
 */
class JmcRandom : Random {
    /** Default constructor for JmcRandom. Ignores the seed  */
    constructor()

    /**
     * To ensure compatibility with the java.util.Random API, this constructor is provided. Ignores
     * the seed.
     */
    constructor(seed: Long) : super()

    /**
     * This method is overridden to yield control to the JMC runtime. It allows the JMC model
     * checker to handle reactive events and return a random value.
     *
     * @param bits the number of bits to generate
     * @return a random integer value based on the specified number of bits
     */
    public override fun next(bits: Int): Int {
        val `val` =
            JmcRuntime.updateEventAndYield<PrimitiveValue>(
                JmcRuntimeEvent.Builder()
                    .type(JmcRuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE)
                    .taskId(JmcRuntime.currentTask())
                    .param("bits", bits)
                    .build()
            )
        return `val`?.asInteger() ?: super.next(bits)
    }
}
