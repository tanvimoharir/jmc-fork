package org.mpi_sws.jmc.agent.visitors

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.objectweb.asm.MethodVisitor
import java.util.*

class JmcConcurrentDetector(
    api: Int,
    mv: MethodVisitor?,
    private val className: String?,
    private val methodName: String
) :
    MethodVisitor(api, mv) {
    private val detectedFeatures: MutableSet<String> = HashSet()

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (owner.startsWith("java/util/concurrent")) {
            val feature: String = owner.replace("/", ".") + "." + name
            detectedFeatures.add(feature)
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    fun getDetectedFeatures(): Set<String> {
        LOGGER.info("Detected features: $detectedFeatures")
        return detectedFeatures
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcConcurrentDetector::class.java
        )
    }
}
