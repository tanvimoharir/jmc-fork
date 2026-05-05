package org.mpi_sws.jmc.strategies.trust

import org.mpi_sws.jmc.api.JmcObject
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

class Location(var instance: Any?, var param: String?) {
    override fun hashCode(): Int {
        return (JmcObject.handleHashCode(instance).toString() + param).hashCode()
    }

    companion object {
        fun fromRuntimeEvent(runtimeEvent: JmcRuntimeEvent): Location {
            var instance = runtimeEvent.getParam<Any>("instance")
            if (instance == null) {
                // This is because the call is a static method call
                instance = runtimeEvent.getParam("owner")
            }
            val param = runtimeEvent.getParam<String>("name")
            return Location(instance, param)
        }
    }
}
