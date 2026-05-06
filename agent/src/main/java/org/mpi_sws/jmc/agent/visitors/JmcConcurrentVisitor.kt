package org.mpi_sws.jmc.agent.visitors

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.agent.visitors.JmcConcurrentVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.*

class JmcConcurrentVisitor : ClassVisitor {
    private var className: String? = null
    private val methodDetectors: MutableList<JmcConcurrentDetector> = ArrayList()
    private val unsupported: MutableSet<String> = HashSet()

    constructor(api: Int, cv: ClassVisitor?) : super(api, cv)

    constructor(classVisitor: ClassVisitor?) : super(Opcodes.ASM9, classVisitor)

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        this.className = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int, name: String, desc: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        val mv = super.visitMethod(access, name, desc, signature, exceptions)
        val detector =
            JmcConcurrentDetector(Opcodes.ASM9, mv, className, name)
        methodDetectors.add(detector)
        return detector
    }

    val allDetectedFeatures: Set<String>
        get() {
            val all: MutableSet<String> = HashSet()
            for (detector in methodDetectors) {
                all.addAll(detector.detectedFeatures)
            }
            return all
        }

    fun usesUnsupportedFeatures(supportedFeatures: Set<String?>): Boolean {
        unsupported.clear()
        for (feature in allDetectedFeatures) {
            if (feature.startsWith("java.util.concurrent")
                && !supportedFeatures.contains(feature)
            ) {
                unsupported.add(feature)
                return true
            }
        }
        return false
    }

    val unsupportedFeatures: Set<String>
        get() {
            LOGGER.info("Unsupported feature {}", unsupported)
            return unsupported
        }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            JmcConcurrentVisitor::class.java
        )
    }
}
