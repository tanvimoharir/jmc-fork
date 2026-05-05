package org.mpi_sws.jmc.api.symbolic.array

import org.mpi_sws.jmc.api.symbolic.integer.AbstractInteger
import org.mpi_sws.jmc.runtime.JmcRuntime

// TODO: implement symbolic array
/**
 * The [SymbolicArray] class is used to represent a symbolic array value.
 */
class SymbolicArray : AbstractArray {
    private val name: String
    private val isShared: Boolean

    /**
     * Creates a new symbolic array with the given name and shared status.
     *
     * @param name     the name of the symbolic array
     * @param isShared whether the array is shared across all tasks
     */
    constructor(name: String, isShared: Boolean) {
        val id = JmcRuntime.currentTask()
        this.name = "SymbolicInteger@" + name + "_" + id
        this.isShared = isShared
        write()
    }

    /**
     * Creates a new symbolic array with the given name. The array is shared across all tasks.
     *
     * @param name the name of the symbolic array
     */
    constructor(name: String) {
        val id = JmcRuntime.currentTask()
        this.name = "SymbolicInteger@" + name + "_" + id
        this.isShared = true
        write()
    }

    fun store(index: AbstractInteger?, value: AbstractInteger?) {
        // TODO
    }

    fun select(index: AbstractInteger?): AbstractInteger? {
        // TODO
        return null
    }

    /**
     * Makes a deep copy of the integer.
     *
     * @return a deep copy of the integer.
     */
    override fun clone(): AbstractArray? {
        // TODO
        return null
    }

    /**
     * @return
     */
    override fun read(): AbstractArray? {
        // TODO
        return null
    }

    /**
     * @param value
     */
    override fun write(value: AbstractArray?) {
        // TODO
    }

    private fun write() {
        // TODO
    }
}