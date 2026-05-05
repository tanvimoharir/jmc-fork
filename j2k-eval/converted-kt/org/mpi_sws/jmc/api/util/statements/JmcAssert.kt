package org.mpi_sws.jmc.api.util.statements

import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

object JmcAssert {
    fun check(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Assertion failed")
        }
    }

    fun check(condition: Boolean, message: String?) {
        if (!condition) {
            throw AssertionError(message)
        }
    }

    fun check(formula: JmcBooleanFormula?) {
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.SYMB_ASSERT_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("booleanFormula", formula)
                .build()
        val result = JmcRuntime.updateEventAndYield<Boolean>(event)
        check(result)
    }

    fun check(formula: JmcBooleanFormula?, message: String?) {
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.SYMB_ASSERT_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("booleanFormula", formula)
                .build()
        val result = JmcRuntime.updateEventAndYield<Boolean>(event)
        check(result, message)
    }
}
