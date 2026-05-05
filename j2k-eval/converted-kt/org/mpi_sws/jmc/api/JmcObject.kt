package org.mpi_sws.jmc.api

import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.JmcRuntime
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils
import java.lang.reflect.InvocationTargetException

object JmcObject {
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun objectWait(o: Any, timeoutMillis: Long = 0) {
        var o = o
        val lock = JmcRuntimeUtils.getSyncLock(o)
            ?: throw HaltCheckerException.Companion.error(
                "Object not used in synchronized block: " + o.javaClass + "@" + handleHashCode(o)
            )
        o = lock.instance
        var event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.WAIT_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", o)
                .param("timeout", timeoutMillis)
                .build()
        try {
            JmcRuntime.updateEventAndYield<Any>(event)
        } catch (e: Exception) {
            throw InterruptedException("Wait interrupted: " + e.message)
        }

        lock.lock()

        event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.WAKEUP_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", o)
                .build()
        try {
            JmcRuntime.updateEventAndYield<Any>(event)
        } catch (e: Exception) {
            throw InterruptedException("Wakeup interrupted: " + e.message)
        }
    }

    fun objectNotify(o: Any) {
        var o = o
        val lock = JmcRuntimeUtils.getSyncLock(o)
            ?: throw HaltCheckerException.Companion.error(
                "Object not used in synchronized block: " + o.javaClass + "@" + handleHashCode(o)
            )
        o = lock.instance
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.NOTIFY_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", o)
                .build()
        try {
            JmcRuntime.updateEventAndYield<Any>(event)
        } catch (e: Exception) {
            // Ignore
        }
    }

    @JvmStatic
    fun objectNotifyAll(o: Any) {
        var o = o
        val lock = JmcRuntimeUtils.getSyncLock(o)
            ?: throw HaltCheckerException.Companion.error(
                "Object not used in synchronized block: " + o.javaClass + "@" + handleHashCode(o)
            )
        o = lock.instance
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", o)
                .build()
        try {
            JmcRuntime.updateEventAndYield<Any>(event)
        } catch (e: Exception) {
            // Ignore
        }
    }

    // ========== Native Object Method Handlers ==========
    /**
     * Handles hashCode() calls - invokes jmcHashCode() via reflection if it exists,
     * otherwise calls obj.hashCode()
     */
    fun handleHashCode(obj: Any?): Int {
        if (obj == null) return 0

        try {
            val method = obj.javaClass.getMethod("jmcHashCode")
            method.isAccessible = true
            return method.invoke(obj) as Int
        } catch (e: NoSuchMethodException) {
            return obj.hashCode()
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to invoke jmcHashCode", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to invoke jmcHashCode", e)
        }
    }

    fun toString(obj: Any): String {
        return obj.javaClass.name + "@" + Integer.toHexString(handleHashCode(obj))
    }

    /**
     * Handles toString() calls - invokes jmcToString() via reflection if it exists,
     * otherwise calls obj.toString()
     */
    fun handleToString(obj: Any?): String {
        if (obj == null) return "null"

        try {
            val method = obj.javaClass.getMethod("jmcToString")
            method.isAccessible = true
            return method.invoke(obj) as String
        } catch (e: NoSuchMethodException) {
            return toString(obj)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to invoke jmcToString", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to invoke jmcToString", e)
        }
    }

    /**
     * Handles equals(Object) calls - invokes jmcEquals(Object) via reflection if it exists,
     * otherwise calls obj.equals(other)
     */
    fun handleEquals(obj: Any?, other: Any?): Boolean {
        if (obj == null) return other == null

        try {
            val method = obj.javaClass.getMethod("jmcEquals", Any::class.java)
            method.isAccessible = true
            return method.invoke(obj, other) as Boolean
        } catch (e: NoSuchMethodException) {
            return obj == other
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to invoke jmcEquals", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to invoke jmcEquals", e)
        }
    }


    /**
     * Handles clone() calls - invokes clone__jmc__() via reflection
     * TODO :: Fix This
     */
    fun handleClone(obj: Any?): Any? {
        if (obj == null) return null

        try {
            val method = obj.javaClass.getMethod("clone__jmc__")
            method.isAccessible = true
            return method.invoke(obj)
        } catch (e: NoSuchMethodException) {
            // Try calling clone() directly (may fail if not Cloneable)
            try {
                val cloneMethod = obj.javaClass.getMethod("clone")
                cloneMethod.isAccessible = true
                return cloneMethod.invoke(obj)
            } catch (ex: Exception) {
                throw RuntimeException("Failed to invoke clone", ex)
            }
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to invoke clone__jmc__", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to invoke clone__jmc__", e)
        }
    }

    /**
     * Handles finalize() calls - invokes finalize__jmc__() via reflection
     * TODO :: Fix This
     */
    fun handleFinalize(obj: Any?) {
        if (obj == null) return
        try {
            val method = obj.javaClass.getDeclaredMethod("finalize__jmc__")
            method.isAccessible = true
            method.invoke(obj)
        } catch (e: NoSuchMethodException) {
            // No custom finalize, do nothing
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to invoke finalize__jmc__", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to invoke finalize__jmc__", e)
        }
    }
}
