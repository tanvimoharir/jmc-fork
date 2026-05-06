package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visitor that detetcs classes with finalize() methods.
 * Classes with finalizers should be ignored from instrumentation to avoid
 * conflicts with finalizer thread during garbage collection.
 */
class JmcIgnoreFinalizerVisitor(classVisitor: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, classVisitor) {
    private var hasFinalizer = false
    private var className: String? = null

    fun hasFinalizer(): Boolean {
        return hasFinalizer
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String,
        exceptions: Array<String>
    ): MethodVisitor {
        // Check if this is the finalize() method with exact signature
        // - name is "finalize"
        // - descriptor is "()V" no params, void return
        // - access is protected (ACC_PROTECTED)
        if ("finalize" == name
            && "()V" == desc
            && ((access and Opcodes.ACC_PROTECTED) != 0)
        ) {
            hasFinalizer = true
        }
        return super.visitMethod(access, name, desc, signature, exceptions)
    }
}
