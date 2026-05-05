package org.mpi_sws.jmc.integrations.junit5.engine

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.platform.commons.support.AnnotationSupport
import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.discovery.MethodSelector
import org.junit.platform.engine.discovery.PackageSelector
import org.mpi_sws.jmc.annotations.JmcCheck
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcClassTestDescriptor
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcEngineDescriptor
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcExecutableTestDescriptor
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcMethodTestDescriptor
import java.lang.reflect.Method
import java.net.URI
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * A custom JUnit 5 test engine for running JMC tests.
 *
 *
 * This engine discovers and executes tests annotated with [JmcCheck] or [ ] in the classpath, packages, or specific classes.
 */
class JmcTestEngine : TestEngine {
    override fun getId(): String {
        return "jmc-test-engine"
    }

    /**
     * Discovers tests based on the provided discovery request and unique ID.
     *
     *
     * This method scans the classpath, packages, and specific classes for JMC tests annotated
     * with [JmcCheck] or [JmcCheckConfiguration]. It creates a test descriptor for the
     * JMC test engine and adds discovered tests as children of the engine descriptor.
     *
     * @param request The discovery request containing selectors for classpath roots, packages, and
     * classes.
     * @param uniqueId The unique ID for the test engine descriptor.
     * @return A [TestDescriptor] representing the discovered tests in the JMC test engine.
     */
    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        LOGGER.debug("Discovering tests")
        val engineDescriptor = JmcEngineDescriptor(uniqueId)

        request.getSelectorsByType(ClasspathRootSelector::class.java)
            .forEach(
                Consumer { selector: ClasspathRootSelector ->
                    appendTestsInClasspathRoot(
                        selector.classpathRoot, engineDescriptor
                    )
                })

        request.getSelectorsByType(PackageSelector::class.java)
            .forEach(
                Consumer { selector: PackageSelector ->
                    appendTestsInPackage(selector.packageName, engineDescriptor)
                })

        request.getSelectorsByType(ClassSelector::class.java)
            .forEach(
                Consumer { selector: ClassSelector ->
                    try {
                        appendTestsInClass(selector.javaClass, engineDescriptor)
                    } catch (e: JmcCheckerException) {
                        throw RuntimeException(e)
                    }
                })
        request.getSelectorsByType(MethodSelector::class.java)
            .forEach(
                Consumer { selector: MethodSelector ->
                    try {
                        val javaClass = selector.javaClass
                        val method = selector.javaMethod
                        if (IS_JMC_TEST_CONTAINER.test(javaClass)) {
                            engineDescriptor.addChild(
                                JmcClassTestDescriptor(
                                    javaClass, engineDescriptor, false
                                )
                            )
                        } else {
                            appendTestsInClass(javaClass, engineDescriptor)
                        }
                    } catch (e: JmcCheckerException) {
                        throw RuntimeException(e)
                    }
                })

        return engineDescriptor
    }

    private fun appendTestsInClasspathRoot(uri: URI, engineDescriptor: TestDescriptor) {
        ReflectionSupport.findAllClassesInClasspathRoot(
            uri, IS_JMC_TEST_CONTAINER
        ) { name: String? -> true } //
            .stream() //
            .map { aClass: Class<*> ->
                try {
                    return@map JmcClassTestDescriptor(aClass, engineDescriptor, true)
                } catch (e: JmcCheckerException) {
                    throw RuntimeException(e)
                }
            }  //
            .forEach { descriptor: JmcClassTestDescriptor? -> engineDescriptor.addChild(descriptor) }
    }

    private fun appendTestsInPackage(packageName: String, engineDescriptor: TestDescriptor) {
        LOGGER.debug("Discovering tests in package {}", packageName)
        ReflectionSupport.findAllClassesInPackage(
            packageName, IS_JMC_TEST_CONTAINER
        ) { name: String? -> true } //
            .stream() //
            .map { aClass: Class<*> ->
                try {
                    return@map JmcClassTestDescriptor(aClass, engineDescriptor, true)
                } catch (e: JmcCheckerException) {
                    throw RuntimeException(e)
                }
            }  //
            .forEach { descriptor: JmcClassTestDescriptor? -> engineDescriptor.addChild(descriptor) }
    }

    @Throws(JmcCheckerException::class)
    private fun appendTestsInClass(javaClass: Class<*>, engineDescriptor: TestDescriptor) {
        LOGGER.debug("Discovering tests in class {}", javaClass.name)
        if (IS_JMC_TEST_CONTAINER.test(javaClass)) {
            engineDescriptor.addChild(
                JmcClassTestDescriptor(javaClass, engineDescriptor, true)
            )
        } else {
            val methods =
                ReflectionSupport.findMethods(
                    javaClass,
                    { method: Method ->
                        method.getAnnotation(
                            JmcCheckConfiguration::class.java
                        ) != null
                                || method.getAnnotation(JmcCheck::class.java) != null
                    },
                    HierarchyTraversalMode.TOP_DOWN
                )

            if (methods.isEmpty()) {
                return
            }
            val testDescriptor =
                JmcClassTestDescriptor(javaClass, engineDescriptor, false)
            engineDescriptor.addChild(testDescriptor)

            methods.forEach(
                Consumer { method: Method ->
                    if (method.getAnnotation(
                            JmcCheckConfiguration::class.java
                        ) != null
                        || method.getAnnotation(JmcCheck::class.java) != null
                    ) {
                        testDescriptor.addChild(
                            JmcMethodTestDescriptor(method, testDescriptor)
                        )
                    }
                })
        }
    }

    /**
     * Executes the discovered tests in the JMC test engine.
     *
     *
     * This method starts the execution of the root test descriptor and recursively executes all
     * child descriptors, handling any exceptions that may occur during execution.
     *
     * @param request The execution request containing the root test descriptor and engine execution
     * listener.
     */
    override fun execute(request: ExecutionRequest) {
        val root = request.rootTestDescriptor
        request.engineExecutionListener.executionStarted(root)

        for (child in root.children) {
            executeDescriptor(request.engineExecutionListener, child)
        }

        request.engineExecutionListener
            .executionFinished(root, TestExecutionResult.successful())
    }

    private fun executeDescriptor(listener: EngineExecutionListener, descriptor: TestDescriptor) {
        if (descriptor is JmcExecutableTestDescriptor) {
            listener.executionStarted(descriptor)
            try {
                descriptor.execute()
                listener.executionFinished(descriptor, TestExecutionResult.successful())
            } catch (t: Throwable) {
                listener.executionFinished(descriptor, TestExecutionResult.failed(t))
            }
        } else {
            for (child in descriptor.children) {
                executeDescriptor(listener, child)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcTestEngine::class.java
        )

        private val IS_JMC_TEST_CONTAINER =
            Predicate { classCandidate: Class<*>? ->
                AnnotationSupport.isAnnotated(
                    classCandidate,
                    JmcCheckConfiguration::class.java
                )
                        || AnnotationSupport.isAnnotated(classCandidate, JmcCheck::class.java)
            }
    }
}
