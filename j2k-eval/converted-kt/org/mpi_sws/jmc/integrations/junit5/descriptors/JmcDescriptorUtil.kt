package org.mpi_sws.jmc.integrations.junit5.descriptors

import org.mpi_sws.jmc.annotations.strategies.JmcMeasureGraphCoverage
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration
import org.mpi_sws.jmc.checker.exceptions.JmcInvalidConfigurationException
import org.mpi_sws.jmc.strategies.SchedulingStrategyConfiguration
import org.mpi_sws.jmc.strategies.SchedulingStrategyConfiguration.SchedulingStrategyConstructor
import org.mpi_sws.jmc.strategies.trust.*
import org.mpi_sws.jmc.strategies.trust.MeasureGraphCoverageStrategyConfig.MeasureGraphCoverageStrategyConfigBuilder
import java.lang.reflect.Method
import java.time.Duration

/**
 * Utility class for handling JMC descriptor configurations.
 *
 *
 * This class provides methods to check and update JMC checker configurations based on
 * annotations present on classes or methods.
 */
object JmcDescriptorUtil {
    /**
     * Checks the provided class and method for JMC trust strategy annotations and updates the JMC
     * checker configuration builder accordingly.
     *
     * @param builder The JMC checker configuration builder to update.
     * @param clazz   The class to check for annotations.
     * @param method  The method to check for annotations.
     * @return An updated JMC checker configuration builder.
     */
    @Throws(JmcInvalidConfigurationException::class)
    fun checkStrategyConfig(
        builder: JmcCheckerConfiguration.Builder, clazz: Class<*>?, method: Method?
    ): JmcCheckerConfiguration.Builder {
        return if (method != null && hasStrategyAnnotation(method)) {
            updateBuilderFromAnnotation(builder, method)
        } else if (clazz != null && hasStrategyAnnotation(clazz)) {
            updateBuilderFromAnnotation(builder, clazz)
        } else {
            builder
        }
    }

    private fun hasStrategyAnnotation(method: Method): Boolean {
        return method.isAnnotationPresent(JmcTrustStrategy::class.java)
    }

    private fun hasStrategyAnnotation(clazz: Class<*>): Boolean {
        return clazz.isAnnotationPresent(JmcTrustStrategy::class.java)
    }

    @Throws(JmcInvalidConfigurationException::class)
    private fun updateBuilderFromAnnotation(
        builder: JmcCheckerConfiguration.Builder, method: Method
    ): JmcCheckerConfiguration.Builder {
        val annotation = method.getAnnotation(JmcTrustStrategy::class.java)
        val constructor =
            getStrategyConstructor(annotation)

        if (method.getAnnotation<JmcMeasureGraphCoverage?>(JmcMeasureGraphCoverage::class.java) != null) {
            val coverageAnnotation =
                method.getAnnotation(JmcMeasureGraphCoverage::class.java)
            val measureConstructor =
                getCoverageStrategyConstructor(coverageAnnotation, constructor)
            return builder.strategyConstructor(measureConstructor)
        } else {
            return builder.strategyConstructor(constructor)
        }
    }

    @Throws(JmcInvalidConfigurationException::class)
    private fun updateBuilderFromAnnotation(
        builder: JmcCheckerConfiguration.Builder, clazz: Class<*>
    ): JmcCheckerConfiguration.Builder {
        val annotation = clazz.getAnnotation(JmcTrustStrategy::class.java)
        val constructor =
            getStrategyConstructor(annotation)

        if (clazz.getAnnotation<JmcMeasureGraphCoverage?>(JmcMeasureGraphCoverage::class.java) != null) {
            val coverageAnnotation =
                clazz.getAnnotation(JmcMeasureGraphCoverage::class.java)
            val measureConstructor =
                getCoverageStrategyConstructor(coverageAnnotation, constructor)
            return builder.strategyConstructor(measureConstructor)
        } else {
            return builder.strategyConstructor(constructor)
        }
    }

    @Throws(JmcInvalidConfigurationException::class)
    private fun getCoverageStrategyConstructor(
        coverageAnnotation: JmcMeasureGraphCoverage,
        constructor: SchedulingStrategyConstructor
    ): SchedulingStrategyConstructor {
        if (coverageAnnotation.recordFrequency != 0L && coverageAnnotation.recordPerIteration) {
            throw JmcInvalidConfigurationException(
                "Cannot set both recordFrequency and recordPerIteration to true in JmcMeasureGraphCoverage annotation."
            )
        }
        return SchedulingStrategyConstructor { config: SchedulingStrategyConfiguration? ->
            val frequency =
                Duration.of(
                    coverageAnnotation.recordFrequency, coverageAnnotation.recordUnit
                )
            val builder: MeasureGraphCoverageStrategyConfigBuilder =
                MeasureGraphCoverageStrategyConfig.Companion.builder()
                    .debug(coverageAnnotation.debug)
                    .recordGraphs(coverageAnnotation.recordGraphs)
                    .recordPath(coverageAnnotation.recordPath)
            if (coverageAnnotation.recordFrequency != 0L) {
                builder.withFrequency(frequency)
            } else if (coverageAnnotation.recordPerIteration) {
                builder.recordPerIteration()
            }
            MeasureGraphCoverageStrategy(constructor.create(config), builder.build())
        }
    }

    private fun getStrategyConstructor(annotation: JmcTrustStrategy): SchedulingStrategyConstructor {
        return SchedulingStrategyConstructor { config: SchedulingStrategyConfiguration? ->
            var seed = config.getSeed()
            if (annotation.seed != 0L) {
                seed = annotation.seed
            }
            TrustStrategy(
                seed,
                annotation.schedulingPolicy,
                annotation.debug,
                annotation.reportPath,
                annotation.loggerTree,
                annotation.solver
            )
        }
    }
}
