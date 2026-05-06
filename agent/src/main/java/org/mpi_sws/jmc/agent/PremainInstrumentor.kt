package org.mpi_sws.jmc.agent

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.agent.visitors.JmcIgnoreVisitor
import org.mpi_sws.jmc.agent.visitors.JmcVisitor
import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.IllegalClassFormatException
import java.nio.file.Files
import java.security.ProtectionDomain

/**
 * The PremainInstrumentor class is responsible for transforming classes during the premain phase of
 * the Java agent lifecycle. It applies various instrumentation visitors to classes that match the
 * specified criteria.
 */
class PremainInstrumentor(private val agentArgs: AgentArgs) : ClassFileTransformer {
    private val matcher = JmcMatcher(
        agentArgs.instrumentingPackages, agentArgs.excludedPackages
    )

    /**
     * Transforms the class file buffer of a class being loaded or redefined.
     *
     *
     * Specifically, if the class matches the arguments provided to the agent, it applies the
     * following visitors in order:
     *
     *
     *  * JmcIgnoreVisitor: Checks if the class has the JmcIgnoreInstrumentation annotation.
     *  * JmcSyncScanVisitor: Scans the class for synchronized methods and collects data.
     *  * JmcSyncMethodVisitor: Instruments synchronized methods based on the collected data.
     *  * JmcFutureVisitor: Instruments classes related to futures and executors.
     *  * JmcAtomicVisitor: Instruments atomic classes.
     *  * JmcReentrantLockVisitor: Instruments reentrant locks.
     *  * JmcThreadVisitor: Instruments thread-related classes.
     *  * JmcReadWriteVisitor: Instruments read-write calls throughout.
     *
     *
     * @param loader the defining loader of the class to be transformed, may be `null` if the
     * bootstrap loader
     * @param className the name of the class in the internal form of fully qualified class and
     * interface names as defined in *The Java Virtual Machine Specification*. For example,
     * `"java/util/List"`.
     * @param classBeingRedefined if this is triggered by a redefine or retransform, the class being
     * redefined or retransformed; if this is a class load, `null`
     * @param protectionDomain the protection domain of the class being defined or redefined
     * @param classFileBuffer the input byte buffer in class file format - must not be modified
     * @return the transformed class file buffer, or the original
     */
    @kotlin.Throws(IllegalClassFormatException::class, JmcUnsupportedFeatureException::class)
    override fun transform(
        loader: ClassLoader,
        className: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain,
        classFileBuffer: ByteArray
    ): ByteArray {
        val finalClassName: String = className.replace("/", ".")
        val copiedClassBuffer: ByteArray = classFileBuffer.copyOf(classFileBuffer.size)

        if (!matcher.matches(finalClassName, loader)) {
            return copiedClassBuffer
        }

        try {
            val tempCr = ClassReader(copiedClassBuffer)
            val tempCw =
                ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)

            val ignoreVisitor = JmcIgnoreVisitor(tempCw)
            tempCr.accept(
                ignoreVisitor,
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
            )

            if (ignoreVisitor.hasIgnoreAnnotation()) {
                return copiedClassBuffer // Skip instrumentation if the class has
                // JmcIgnoreInstrumentation annotation
            }

            LOGGER.info("Instrumenting class: {}", finalClassName)
            val transformed = JmcVisitor.transform(copiedClassBuffer)
            if (agentArgs.isDebug) {
                record(className, transformed!!)
            }
            return transformed!!
        } catch (e: Exception) {
            if (e is JmcUnsupportedFeatureException) {
                throw JmcUnsupportedFeatureException(e.message)
            } else {
                LOGGER.info("Error transforming class: {} {}", finalClassName, e)
                throw IllegalClassFormatException(
                    "Error instrumenting class: $finalClassName Error: $e"
                )
            }
        }
    }

    fun record(className: String, classFileBuffer: ByteArray) {
        val outputDir = agentArgs.debugSavePath
        val outFile = File("$outputDir/$className.class")
        try {
            LOGGER.debug("Recording instrumented class: {}", className)
            outFile.parentFile.mkdirs()
            Files.write(outFile.toPath(), classFileBuffer)
        } catch (e: Exception) {
            LOGGER.error("Error writing to file: {} {}", outFile.absolutePath, e)
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            PremainInstrumentor::class.java
        )
    }
}
