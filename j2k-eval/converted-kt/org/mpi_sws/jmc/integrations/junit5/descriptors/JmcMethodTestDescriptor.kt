package org.mpi_sws.jmc.integrations.junit5.descriptors

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import org.mpi_sws.jmc.annotations.*
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.integrations.junit5.engine.JmcTestExecutor
import org.mpi_sws.jmc.util.ExceptionUtil
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.time.Duration

/**
 * A JUnit 5 test descriptor for a JMC method test.
 *
 *
 * This descriptor represents a single test method annotated with JMC annotations, allowing for
 * the execution of JMC checks as part of the test lifecycle.
 */
class JmcMethodTestDescriptor(private val testMethod: Method, parent: JmcClassTestDescriptor) : AbstractTestDescriptor(
    parent.uniqueId.append("method", testMethod.name),
    testMethod.name,
    MethodSource.from(testMethod)
), JmcExecutableTestDescriptor {
    private val isReplayTest =
        testMethod.getAnnotation(JmcReplay::class.java) != null
    private val parentConfigAnnotation: JmcCheckConfiguration? = parent.configAnnotation

    override fun getType(): TestDescriptor.Type {
        return TestDescriptor.Type.TEST
    }

    private fun buildFromAnnotation(
        builder: JmcCheckerConfiguration.Builder, annotation: JmcCheckConfiguration
    ): JmcCheckerConfiguration.Builder {
        var seed = annotation.seed
        val budget = annotation.budget
        val timeout = annotation.timeout
        if (annotation.seed == 0L) {
            seed = System.nanoTime()
        }
        return builder.numIterations(annotation.numIterations)
            .debug(annotation.debug)
            .seed(seed)
            .budget(budget)
            .timeout(timeout)
            .reportPath(annotation.reportPath)
            .strategyType(annotation.strategy)
            .solver(annotation.solver)
            .schedulingPolicy(annotation.schedulingPolicy)
    }

    /**
     * Executes the JMC test method.
     *
     *
     * This method creates an instance of the test class, configures the JMC checker based on
     * annotations, and executes the test method using the JMC Model Checker.
     *
     *
     * Execution can be either running the model checker or replaying a previous execution and
     * depends on the annotation provided for the test method. If the method is annotated with
     * [JmcReplay], it will replay the test method instead of executing it.
     *
     * @throws JmcCheckerException If an error occurs during execution or configuration.
     */
    @Throws(JmcCheckerException::class)
    override fun execute() {
        LOGGER.debug("JmcMethodTestDescriptor execute() called")
        val methodInstance: Any
        try {
            methodInstance = testMethod.declaringClass.getDeclaredConstructor().newInstance()
        } catch (e: NoSuchMethodException) {
            LOGGER.error(
                "Error creating instance of test class: {}",
                testMethod.declaringClass.name,
                e
            )
            throw JmcCheckerException("Error creating instance of test class", e)
        } catch (e: InstantiationException) {
            LOGGER.error(
                "Error creating instance of test class: {}",
                testMethod.declaringClass.name,
                e
            )
            throw JmcCheckerException("Error creating instance of test class", e)
        } catch (e: IllegalAccessException) {
            LOGGER.error(
                "Error creating instance of test class: {}",
                testMethod.declaringClass.name,
                e
            )
            throw JmcCheckerException("Error creating instance of test class", e)
        } catch (e: InvocationTargetException) {
            LOGGER.error(
                "Error creating instance of test class: {}",
                testMethod.declaringClass.name,
                e
            )
            throw JmcCheckerException("Error creating instance of test class", e)
        }
        testMethod.isAccessible = true

        var configBuilder: JmcCheckerConfiguration.Builder? = JmcCheckerConfiguration.Builder()
        if (testMethod.getAnnotation<JmcCheckConfiguration?>(JmcCheckConfiguration::class.java) != null) {
            // Method has JmcCheckConfiguration annotation use that
            val annotation =
                testMethod.getAnnotation(JmcCheckConfiguration::class.java)
            LOGGER.debug("JmcCheckConfiguration annotation found")
            configBuilder = buildFromAnnotation(configBuilder!!, annotation)
        } else if (parentConfigAnnotation != null) {
            // Class has JmcCheckConfiguration annotation use that
            val annotation =
                testMethod.declaringClass.getAnnotation(JmcCheckConfiguration::class.java)
            LOGGER.debug("JmcCheckConfiguration annotation found in class")
            configBuilder = buildFromAnnotation(configBuilder!!, annotation)
        } else {
            LOGGER.debug("No JmcCheckConfiguration annotation found")
            // Use default values
        }
        if (testMethod.getAnnotation<JmcTimeout?>(JmcTimeout::class.java) != null) {
            val annotationTimeout = testMethod.getAnnotation(JmcTimeout::class.java)
            configBuilder =
                configBuilder!!.timeout(
                    Duration.of(annotationTimeout.value, annotationTimeout.unit)
                )
        }

        configBuilder =
            JmcDescriptorUtil.checkStrategyConfig(
                configBuilder!!, testMethod.declaringClass, testMethod
            )

        val expectFailure = testMethod.getAnnotation(
            JmcExpectAssertionFailure::class.java
        ) != null
        var failed = false
        try {
            val config = configBuilder.build()
            if (isReplayTest) {
                JmcTestExecutor.executeReplay(testMethod, methodInstance, config)
            } else {
                val report =
                    JmcTestExecutor.execute(testMethod, methodInstance, config)
                if (testMethod.getAnnotation(JmcExpectExecutions::class.java) != null) {
                    val expectExecutions =
                        testMethod.getAnnotation(JmcExpectExecutions::class.java)
                    val completeIteration = report.totalIterations - report.blockedIterations
                    if (completeIteration != expectExecutions.value) {
                        throw JmcCheckerException(
                            ("Expected "
                                    + expectExecutions.value
                                    + " executions, but got "
                                    + completeIteration)
                        )
                    }
                }
            }
        } catch (e: JmcCheckerException) {
            if (expectFailure && ExceptionUtil.isAssertionError(e.cause)) {
                failed = true
            } else {
                LOGGER.error("Error executing test method: {}", testMethod.name, e)
                throw e
            }
        }
        if (expectFailure && !failed) {
            throw JmcCheckerException(
                ("Test method "
                        + testMethod.name
                        + " expected to fail but passed successfully.")
            )
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcMethodTestDescriptor::class.java
        )
    }
}
