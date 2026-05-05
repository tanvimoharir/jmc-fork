package org.mpi_sws.jmc.runtime

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.api.JmcObject
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula
import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock
import org.mpi_sws.jmc.api.util.concurrent.JmcThread
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.ExecutorService

/**
 * Utility class for JMC runtime operations.
 *
 *
 * This class provides methods to create and manage JMC runtime events, synchronize method
 * execution, and handle thread join operations. It is primarily used for bytecode instrumentation
 * and is not intended for direct use within the codebase.
 */
object JmcRuntimeUtils {
    private val LOGGER: Logger = LogManager.getLogger(JmcRuntimeUtils::class.java)

    private val syncMethodLocksStore = JmcSyncLocksStore()

    // TODO: check if we need to change the type of the list here
    private val staticInitializedClassesList: MutableList<Class<*>> = ArrayList()
    private val staticInitializedClasses: MutableSet<String> = HashSet()

    fun SymEvent(formula: JmcBooleanFormula?): Boolean {
        val event =
            JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.SYMB_OP_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("booleanFormula", formula)
                .build()
        val result = JmcRuntime.updateEventAndYield<Any>(event) as? Boolean
            ?: throw RuntimeException("Expected a boolean result from symbolic event evaluation")
        // If result is not boolean, throw an exception
        return result
    }

    /**
     * Creates a read event for the specified instance, owner, name, and descriptor.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param instance the instance on which the field is accessed
     */
    @JvmStatic
    fun readEvent(owner: String?, name: String?, descriptor: String?, instance: Any?) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["newValue"] = null
        var2["owner"] = owner
        var2["name"] = name
        var2["descriptor"] = descriptor
        JmcRuntime.updateEventAndYield<Any>(builder.params(var2).param("instance", instance).build())
    }

    /**
     * Creates a read event for the specified instance, owner, name, and descriptor without
     * yielding.
     *
     *
     * This method updates the JMC runtime event without yielding control to the scheduler.
     *
     * @param instance the instance on which the field is accessed
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     */
    fun readEventWithoutYield(
        instance: Any?, owner: String?, name: String?, descriptor: String?
    ) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["owner"] = owner
        var2["name"] = name
        var2["descriptor"] = descriptor
        var2["instance"] = instance
        JmcRuntime.updateEvent(builder.params(var2).build())
    }

    /**
     * Creates a write event for the specified value, owner, name, descriptor, and instance without
     * yielding.
     *
     * @param value the new value being written
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param instance the instance on which the field is accessed
     */
    fun writeEventWithoutYield(
        instance: Any?, value: Any?, owner: String?, name: String?, descriptor: String?
    ) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["newValue"] = value
        var2["owner"] = owner
        var2["name"] = name
        var2["descriptor"] = descriptor
        var2["instance"] = instance
        JmcRuntime.updateEvent(builder.params(var2).build())
    }

    /**
     * Creates a write event for the specified value, owner, name, descriptor, and instance.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param value the new value being written
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param instance the instance on which the field is accessed
     */
    @JvmStatic
    fun writeEvent(
        value: Any?, owner: String?, name: String?, descriptor: String?, instance: Any?
    ) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["newValue"] = value
        var2["owner"] = owner
        var2["name"] = name
        var2["descriptor"] = descriptor
        JmcRuntime.updateEventAndYield<Any>(builder.params(var2).param("instance", instance).build())
    }

    /**
     * Creates a lock acquire event for the specified owner, name, value, descriptor, and instance.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param owner the owner of the lock
     * @param name the name of the lock
     * @param value the value of the lock
     * @param descriptor the descriptor of the lock
     * @param instance the instance on which the lock is acquired
     */
    fun lockAcquireEvent(
        owner: String?, name: String?, value: Any?, descriptor: String?, instance: Any?
    ) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["owner"] = owner
        var2["name"] = name
        var2["value"] = value
        var2["descriptor"] = descriptor
        JmcRuntime.updateEventAndYield<Any>(builder.params(var2).param("instance", instance).build())
    }

    /**
     * Creates a lock acquired event for the specified instance, owner, name, value, descriptor, and
     * new value without yielding.
     *
     *
     * This method updates the JMC runtime event without yielding control to the scheduler.
     *
     * @param instance the instance on which the lock is acquired
     * @param owner the owner of the lock
     * @param name the name of the lock
     * @param value the value of the lock
     * @param descriptor the descriptor of the lock
     * @param newValue the new value after acquiring the lock
     */
    fun lockAcquiredEventWithoutYield(
        instance: Any?,
        owner: String?,
        name: String?,
        value: Any?,
        descriptor: String?,
        newValue: Any?
    ) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["owner"] = owner
        var2["name"] = name
        var2["value"] = value
        var2["newValue"] = newValue
        var2["descriptor"] = descriptor
        JmcRuntime.updateEvent(builder.params(var2).param("instance", instance).build())
    }

    /**
     * Creates a lock release event for the specified instance, owner, name, value, descriptor, and
     * new value.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param instance the instance on which the lock is released
     * @param owner the owner of the lock
     * @param name the name of the lock
     * @param value the value of the lock
     * @param descriptor the descriptor of the lock
     * @param newValue the new value after releasing the lock
     */
    fun lockReleaseEvent(
        instance: Any?,
        owner: String?,
        name: String?,
        value: Any?,
        descriptor: String?,
        newValue: Any?
    ) {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT).taskId(JmcRuntime.currentTask())

        val var2 = HashMap<String, Any?>()
        var2["owner"] = owner
        var2["name"] = name
        var2["value"] = value
        var2["newValue"] = newValue
        var2["descriptor"] = descriptor
        JmcRuntime.updateEventAndYield<Any>(builder.params(var2).param("instance", instance).build())
    }

    /**
     * Joins the specified thread, waiting for it to finish for a specified time.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param t the thread to join
     * @param millis the maximum time to wait in milliseconds
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    /**
     * Joins the specified thread, waiting indefinitely for it to finish.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     *
     * Join calls used by the instrumentation to replace existing join calls. Why do these exist?
     * While bytecode instrumentation allows us to change base class, we cannot control the order in
     * which the classes are loaded. So blindly replacing calls to join join1 doesn't work and hence
     * we need to do it at runtime. These calls are added instead of thread.join calls at runtime.
     *
     * @param t the thread to join
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun join(t: Thread, millis: Long = 0L) {
        val jmcThread = t as JmcThread
        jmcThread.join1(millis)
    }

    /**
     * Joins the specified thread, waiting for it to finish for a specified time and nanoseconds.
     *
     *
     * This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param t the thread to join
     * @param millis the maximum time to wait in milliseconds
     * @param nanos additional nanoseconds to wait
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    @Throws(InterruptedException::class)
    fun join(t: Thread, millis: Long, nanos: Int) {
        var millis = millis
        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++
        }
        val jmcThread = t as JmcThread
        jmcThread.join1(millis)
    }

    /**
     * Checks if the given object is an instance of [JmcThread] and should be instrumented for
     * thread calls.
     *
     * @param t the object to check
     * @return true if the object is an instance of [JmcThread], false otherwise
     */
    fun shouldInstrumentThreadCall(t: Any): Boolean {
        return JmcThread::class.java.isAssignableFrom(t.javaClass)
    }

    // Synchronized method and blocks calls are replaced with calls to
    // These methods which maintains state in a static global instance
    // of `JmcSyncLockStore`.
    // For synchronized methods,
    // 1. In the constructor of the class or the static initializer
    //      `registerSyncLock` is called
    // 2. Then each method start is replaced with a
    //      `try {lock() ... } finally {unlock()}
    // 3. The lock is done using `syncMethodLock`
    // 4. The unlock is done using `synchMethodUnlock`
    // 5. The difference between a static sync method and an
    //      instance sync method is in the parameters passed.
    //      The object instance for the former and the classname
    //      as string for the latter
    // For synchronized blocks
    // 1. The block is seen in the bytecode as a try catch with
    //      `MONITORENTER` and `MONITOREXIT`
    // 2. We just replace the enter and exit with lock() and unlock()
    /**
     * Locks the corresponding lock of the given instance.
     *
     *
     * This method acquires a lock on the instance's hash code.
     *
     * @param instance the instance to lock
     */
    fun syncMethodLock(instance: Any?) {
        syncMethodLocksStore.getLock(JmcObject.handleHashCode(instance))!!.lock()
    }

    /**
     * Unlocks the corresponding lock of the given instance.
     *
     *
     * This method releases a lock on the instance's hash code.
     *
     * @param instance the instance to unlock
     */
    fun syncMethodUnLock(instance: Any?) {
        syncMethodLocksStore.getLock(JmcObject.handleHashCode(instance))!!.unlock()
    }

    /**
     * Locks the corresponding lock of the given class's static synchronized method.
     *
     *
     * This method acquires a lock on the class name's hash code.
     *
     * @param className the class name to lock
     */
    fun syncMethodLock(className: String?) {
        syncMethodLocksStore.getLock(JmcObject.handleHashCode(className))!!.lock()
    }

    /**
     * Unlocks the corresponding lock of the given class's static synchronized method.
     *
     *
     * This method releases a lock on the class name's hash code.
     *
     * @param className the class name to unlock
     */
    fun syncMethodUnLock(className: String?) {
        syncMethodLocksStore.getLock(JmcObject.handleHashCode(className))!!.unlock()
    }

    /**
     * Registers a synchronization lock for the given instance.
     *
     *
     * This method registers a lock based on the instance's hash code.
     *
     * @param instance the instance to register a lock for
     */
    fun registerSyncLock(instance: Any?) {
        syncMethodLocksStore.registerLock(JmcObject.handleHashCode(instance))
    }

    /**
     * Registers a synchronization lock for the given class name.
     *
     *
     * This method registers a lock based on the class name's hash code.
     *
     * @param className the class name to register a lock for
     */
    fun registerSyncLock(className: String?) {
        syncMethodLocksStore.registerLock(JmcObject.handleHashCode(className))
    }

    /**
     * Locks the block for the given instance.
     *
     *
     * This method acquires a lock on the instance's hash code for synchronized blocks.
     *
     * @param instance the instance to lock
     */
    fun syncBlockLock(instance: Any?) {
        syncMethodLocksStore.getWithRegister(JmcObject.handleHashCode(instance))!!.lock()
    }

    /**
     * Unlocks the block for the given instance.
     *
     *
     * This method releases a lock on the instance's hash code for synchronized blocks.
     *
     * @param instance the instance to unlock
     */
    fun syncBlockUnLock(instance: Any?) {
        syncMethodLocksStore.getWithRegister(JmcObject.handleHashCode(instance))!!.unlock()
    }

    /**
     * Retrieves the synchronization lock for the given instance.
     *
     *
     * This method returns the lock associated with the instance's hash code, null if none
     * exists.
     *
     * @param instance the instance to get the lock for
     * @return the JmcReentrantLock associated with the instance
     */
    fun getSyncLock(instance: Any?): JmcReentrantLock? {
        return syncMethodLocksStore.getLock(JmcObject.handleHashCode(instance))
    }

    /**
     * Clears all synchronization locks.
     *
     *
     * This method clears the internal store of synchronization locks.
     */
    fun clearSyncLocks() {
        syncMethodLocksStore.clear()
    }

    fun registerStaticInitializedClass(clazz: Class<*>) {
        if (!staticInitializedClasses.contains(clazz.name)) {
            LOGGER.debug("Static classes registered are : {}", clazz.name)
            staticInitializedClasses.add(clazz.name)
            staticInitializedClassesList.add(clazz)
        }
    }

    private fun renameClassURL(url: URL): URL {
        var urlString = url.toString()
        urlString = urlString.replace("build/classes/java/main/", "build/generated/instrumented/")
        urlString = urlString.replace("build/classes/java/test/", "build/generated/instrumented/")
        try {
            return URL(urlString)
        } catch (e: Exception) {
            LOGGER.error("Error renaming class URL: {}", urlString, e)
            return url // Fallback to original URL in case of error
        }
    }

    // Relic from a different method to deal with static initialized classes
    // Would reload the classes and trigger static initializers.
    //    private static void reloadStaticInitializedClasses() {
    //        if (staticInitializedClasses.isEmpty()) {
    //            LOGGER.info("No static initialized classes to reload.");
    //            return;
    //        }
    //        URL[] urls = new URL[staticInitializedClassesList.size()];
    //        for (Class<?> clazz : staticInitializedClassesList) {
    //            URL url = clazz.getResource(clazz.getSimpleName() + ".class");
    //            urls[staticInitializedClassesList.indexOf(clazz)] = renameClassURL(url);
    //        }
    //
    //        try (ReloadingClassLoader classLoader = new ReloadingClassLoader(urls)) {
    //            // This will load the classes and trigger static initializers
    //            for (Class<?> clazz : staticInitializedClassesList) {
    //                try {
    //                    classLoader.reloadClass(clazz.getCanonicalName());
    //                } catch (ClassNotFoundException e) {
    //                    LOGGER.error("Could not reload class: {}", clazz.getCanonicalName(), e);
    //                }
    //            }
    //        } catch (Exception e) {
    //            LOGGER.error("Error initializing the custom class loader", e);
    //        }
    //    }
    private fun invokeInstrumentedStaticMethod() {
        if (staticInitializedClasses.isEmpty()) {
            LOGGER.debug("No static initialized classes to invoke.")
            return
        }
        val snapshot: List<Class<*>> = ArrayList(staticInitializedClassesList)

        // Determine which method to call based on iteration
        val methodName = "\$staticInitExplicit"

        for (clazz in snapshot) {
            try {
                val m = clazz.getDeclaredMethod(methodName)
                m.isAccessible = true
                m.invoke(null)
                LOGGER.debug("Invoked {} in class: {}", methodName, clazz.name)
            } catch (ite: InvocationTargetException) {
                ite.cause!!.printStackTrace()
                LOGGER.error("Error invoking {} in {}", methodName, clazz.name, ite.cause)
            } catch (e: IllegalAccessException) {
                LOGGER.error("Error invoking {} in {}", methodName, clazz.name, e.cause)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }
        }
    }


    /**
     * Invokes static initializer of the instrumented classes.
     *
     *
     * The instrumentation introduces a special method `$staticInit` for each class that has a
     * non-empty static initializer. Here we invoke that method.
     */
    fun invokeStaticInitializedClasses(iteration: Int) {
        // reloadStaticInitializedClasses();
        invokeInstrumentedStaticMethod()
    }

    // Add these methods to JmcRuntimeUtils class:
    /**
     * Creates a start static init event without yielding.
     * This marks the beginning of static initialization for a class.
     */
    fun startStaticInitEventWithoutYield() {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT)
            .taskId(JmcRuntime.currentTask())
        JmcRuntime.updateEvent(builder.build())
    }

    /**
     * Creates an end static init event without yielding.
     * This marks the end of static initialization for a class.
     */
    fun endStaticInitEventWithoutYield() {
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT)
            .taskId(JmcRuntime.currentTask())
        JmcRuntime.updateEvent(builder.build())
    }

    /**
     * Registers a static ExecutorService field for tracking.
     * Uses reflection to avoid triggering field read instrumentation.
     *
     * @param className the fully qualified class name
     * @param fieldName the name of the static ExecutorService field
     */
    fun registerStaticExecutorField(className: String?, fieldName: String) {
        try {
            val clazz = Class.forName(className)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val executorService = field[null]

            if (executorService is ExecutorService) {
                registerExecutor(executorService)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to register static executor field: {}.{}", className, fieldName, e)
        }
    }

    /**
     * Registers an ExecutorService for tracking and automatic shutdown.
     *
     * @param executor the ExecutorService to register
     */
    fun registerExecutor(executor: ExecutorService?) {
        // This will be called by TrackExecutors
        val builder = JmcRuntimeEvent.Builder()
        builder.type(JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT)
            .taskId(JmcRuntime.currentTask())
            .param("executor", executor)
            .param("action", "register")
        JmcRuntime.updateEvent(builder.build())
    }

    private class JmcSyncLocksStore {
        private val lockMap: MutableMap<Int, JmcReentrantLock> =
            HashMap()

        fun clear() {
            lockMap.clear()
        }

        /**
         * Returns a JmcReentrantLock for the given lockObject. If it does not exist, creates a new
         * one and registers it.
         *
         *
         * Note: the lock created does not call the initial write.
         *
         * @param lockObject the object to lock on
         * @return the JmcReentrantLock for the given lockObject
         */
        fun getWithRegister(lockObject: Any?): JmcReentrantLock? {
            if (!lockMap.containsKey(JmcObject.handleHashCode(lockObject))) {
                lockMap[JmcObject.handleHashCode(lockObject)] = JmcReentrantLock(lockObject)
            }
            return lockMap[JmcObject.handleHashCode(lockObject)]
        }

        /**
         * Returns the JmcReentrantLock for the given hashCode.
         *
         * @param hashCode the hash code of the lock object
         * @return the JmcReentrantLock for the given hashCode, or null if not found
         */
        fun getLock(hashCode: Int): JmcReentrantLock? {
            return lockMap[hashCode]
        }

        /**
         * Registers a new JmcReentrantLock for the given hashCode.
         *
         * @param hashcode the hash code of the lock object
         */
        fun registerLock(hashcode: Int) {
            lockMap[hashcode] = JmcReentrantLock()
        }
    }

    private class ReloadingClassLoader(urls: Array<URL?>) : URLClassLoader(urls, null) {
        @Throws(ClassNotFoundException::class)
        fun reloadClass(className: String) {
            // TODO: this is not working. Need to figure out why?
            // This method is used to reload a class by its name
            val clazz = loadClass(className, true)
            if (clazz != null) {
                LOGGER.info("Reloaded class: {}", className)
            } else {
                throw ClassNotFoundException("Class not found: $className")
            }
        }
    }
}
