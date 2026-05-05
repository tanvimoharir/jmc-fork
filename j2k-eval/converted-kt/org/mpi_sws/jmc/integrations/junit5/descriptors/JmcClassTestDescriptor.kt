package org.mpi_sws.jmc.integrations.junit5.descriptors

import org.junit.platform.commons.util.ReflectionUtils
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.mpi_sws.jmc.annotations.JmcCheck
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration
import java.lang.reflect.Method
import java.util.function.Consumer

/** A JUnit 5 test descriptor for a JMC class test.  */
class JmcClassTestDescriptor(private val testClass: Class<*>, parent: TestDescriptor, selfDiscovery: Boolean) :
    AbstractTestDescriptor(
        parent.uniqueId.append("class", testClass.name),
        testClass.simpleName,
        ClassSource.from(testClass)
    ) {
    var configAnnotation: JmcCheckConfiguration? = null
        private set

    init {
        setParent(parent)

        // Resolving class level configuration
        val annotation = testClass.getAnnotation(JmcCheckConfiguration::class.java)
        val jmcCheckAnnotation = testClass.getAnnotation(JmcCheck::class.java)
        if (annotation != null || jmcCheckAnnotation != null) {
            this.configAnnotation = annotation
        }
        if (selfDiscovery) {
            discoverChildren()
        }
    }

    private fun discoverChildren() {
        val classAnnotation =
            testClass.getAnnotation(JmcCheckConfiguration::class.java)
        val classHasAnnotation = classAnnotation != null

        ReflectionUtils.findMethods(
            testClass,
            { method: Method ->
                method.isAnnotationPresent(
                    JmcCheckConfiguration::class.java
                )
                        || method.isAnnotationPresent(JmcCheck::class.java)
                        || classHasAnnotation
            },
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN
        )
            .forEach(
                Consumer { method: Method ->
                    addChild(
                        JmcMethodTestDescriptor(
                            method,
                            this
                        )
                    )
                })
    }

    override fun getType(): TestDescriptor.Type {
        return TestDescriptor.Type.CONTAINER_AND_TEST
    }
}
