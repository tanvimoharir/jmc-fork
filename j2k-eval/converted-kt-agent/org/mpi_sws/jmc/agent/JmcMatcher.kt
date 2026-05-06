package org.mpi_sws.jmc.agent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Matcher for JMC agent.
 *
 *
 * Currently filters out classes loaded by built-in class loader.
 */
class JmcMatcher
/**
 * Constructs a new JmcMatcher with the specified matching and excluded packages.
 *
 * @param matchingPackages the list of packages to match
 * @param excludedPackages the list of packages to exclude
 */(private val matchingPackages: List<String?>?, private val excludedPackages: List<String>?) {
    /**
     * Matches the class name.
     *
     * @param className   the class name
     * @param classLoader the class loader
     * @return true if the class name matches
     */
    fun matches(className: String, classLoader: ClassLoader?): Boolean {
        val typeName: String = className.replace("/", ".")
        if (typeName.startsWith("java.")
            || typeName.startsWith("javax.")
            || typeName.startsWith("sun.")
            || typeName.startsWith("com.sun.")
            || typeName.startsWith("jdk.")
            || typeName.startsWith("kotlin.")
            || typeName.startsWith("kotlinx.")
            || typeName.startsWith("org.gradle.")
            || typeName.startsWith("org.slf4j.")
            || typeName.startsWith("worker.org.gradle.")
            || typeName.startsWith("org.junit.")
        ) {
            return false
        }
        // Exclude instrumentation classes.
        if (typeName.startsWith("org.mpi_sws.jmc.agent.")) {
            return false
        }
        // Exclude instrumentation classes.
        if (!excludedPackages!!.isEmpty()) {
            for (exclude in excludedPackages) {
                if (!exclude.isEmpty() && typeName.startsWith(exclude)) {
                    //LOGGER.debug(
                    //      "Excluding class: {} due to excluded package: {}", typeName, exclude);
                    return false
                }
            }
        }
        return if (!matchingPackages!!.isEmpty()) {
            matchingPackages.stream().anyMatch { prefix: String? -> typeName.startsWith(prefix) }
        } else {
            true
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(JmcMatcher::class.java)
    }
}
