package org.mpi_sws.jmc.agent

import java.util.*

/** The AgentArgs class is used to parse the agent arguments.  */
class AgentArgs(agentArgs: String?) {
    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    var isDebug: Boolean = false
        private set

    /**
     * Gets the path where debug information will be saved.
     *
     * @return the debug save path
     */
    var debugSavePath: String = "build/generated/instrumented"
        private set

    /**
     * Gets the list of packages to instrument.
     *
     * @return the list of instrumenting packages
     */
    var instrumentingPackages: List<String> = ArrayList()
        private set

    /**
     * Gets the list of packages to exclude from instrumentation.
     *
     * @return the list of excluded packages
     */
    var excludedPackages: List<String> = ArrayList()
        private set

    /**
     * Gets the path to the JMC runtime jar.
     *
     * @return the path to the JMC runtime jar
     */
    var jmcRuntimeJarPath: String = "build/deps/jmc-0.1.1.jar"
        private set

    /**
     * The AgentArgs constructor is used to parse the agent arguments.
     *
     * @param agentArgs the agent arguments
     */
    init {
        if (agentArgs != null) {
            val args: Array<String> = agentArgs.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (arg in args) {
                val parts: Array<String> = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size == 2) {
                    if (parts[0] == DEBUG_FLAG) {
                        isDebug = parts[1].toBoolean()
                    } else if (parts[0] == DEBUG_PATH_FLAG) {
                        debugSavePath = parts[1]
                    } else if (parts[0] == INSTRUMENTING_PKG_FLAG) {
                        instrumentingPackages =
                            java.util.List.of<String>(*parts[1].split(";".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray())
                    } else if (parts[0] == EXCLUDED_PKG_FLAG) {
                        excludedPackages =
                            java.util.List.of<String>(*parts[1].split(";".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray())
                    } else if (parts[0] == JMC_RUNTIME_JAR_PATH_FLAG) {
                        jmcRuntimeJarPath = parts[1]
                    }
                } else {
                    if (arg == DEBUG_FLAG) {
                        isDebug = true
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return ("AgentArgs{"
                + "debug="
                + isDebug
                + ", debugSavePath='"
                + debugSavePath
                + '\''
                + ", instrumentingPackages="
                + instrumentingPackages
                + ", excludedPackages="
                + excludedPackages
                + ", jmcRuntimeJarPath='"
                + jmcRuntimeJarPath
                + '\''
                + '}')
    }

    companion object {
        private const val DEBUG_FLAG = "debug"
        private const val DEBUG_PATH_FLAG = "debugSavePath"
        private const val INSTRUMENTING_PKG_FLAG = "instrumentingPackages"
        private const val EXCLUDED_PKG_FLAG = "excludedPackages"
        private const val JMC_RUNTIME_JAR_PATH_FLAG = "jmcRuntimeJarPath"
    }
}
