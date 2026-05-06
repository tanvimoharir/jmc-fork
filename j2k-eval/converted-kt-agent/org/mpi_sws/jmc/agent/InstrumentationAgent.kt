package org.mpi_sws.jmc.agent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

/**
 * The InstrumentationAgent class is the entry point for the instrumentation agent. It is used to
 * set up the agent and install the instrumentation on the target application.
 */
object InstrumentationAgent {
    private val LOGGER: Logger = LogManager.getLogger(
        InstrumentationAgent::class.java
    )

    private fun loadDependencyJars(inst: Instrumentation, jmcRuntimeJarPath: String) {
        try {
            val `in` = Files.newInputStream(File(jmcRuntimeJarPath).toPath())
            val tempFile = File.createTempFile("jmc-runtime", ".jar")
            Files.copy(`in`, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            inst.appendToSystemClassLoaderSearch(JarFile(tempFile))
        } catch (e: Exception) {
            throw RuntimeException("Failed to load JMC runtime jar", e)
        }
    }

    /**
     * The premain method is called before the application's main method is called. It is used to
     * set up the instrumentation agent.
     *
     * @param agentArgs the agent arguments
     * @param inst the instrumentation object
     */
    fun premain(agentArgs: String?, inst: Instrumentation) {
        val args = AgentArgs(agentArgs)
        LOGGER.debug("Starting JMC agent")
        LOGGER.info("Arguments: {}", agentArgs)
        loadDependencyJars(inst, args.jmcRuntimeJarPath)

        try {
            val instrumentor = PremainInstrumentor(args)
            inst.addTransformer(instrumentor, true)
        } catch (e: Exception) {
            LOGGER.error("Failed to initialize JMC agent", e)
            System.err.println("Failed to initialize JMC agent: " + e.message)
            throw RuntimeException("Failed to initialize JMC agent", e)
        }
    }
}
