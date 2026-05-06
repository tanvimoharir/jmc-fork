package org.mpi_sws.jmc.agent.visitors

import org.mpi_sws.jmc.agent.visitors.VisitorHelper.JmcAnnotationRecordVisitor
import org.mpi_sws.jmc.agent.visitors.VisitorHelper.NestedAnnotationValue
import org.objectweb.asm.*
import java.util.*

/**
 * JmcSyncMethodVisitor is a ClassVisitor that instruments synchronized methods and blocks in a
 * class. It replaces synchronized methods with non-synchronized versions and adds locking logic
 * around method calls to ensure thread safety.
 */
class JmcSyncMethodVisitor(classVisitor: ClassVisitor?, private val jmcSyncScanData: JmcSyncScanData) :
    ClassVisitor(Opcodes.ASM9, classVisitor) {
    private var className: String? = null

    private val syncMethods: MutableList<VisitorHelper.MethodInfo> = ArrayList()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        this.className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int, name: String, desc: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        if (jmcSyncScanData.hasSyncMethods() && name == "<init>") {
            val mv = super.visitMethod(access, name, desc, signature, exceptions)
            return JmcSyncMethodConstMethodVisitor(mv, true, "")
        }

        if (jmcSyncScanData.hasSyncStaticMethods() && name == "<clinit>") {
            val mv = super.visitMethod(access, name, desc, signature, exceptions)
            return JmcSyncMethodConstMethodVisitor(mv, false, name)
        }

        var mv: MethodVisitor
        if ((access and Opcodes.ACC_SYNCHRONIZED) != 0) {
            val methodInfo =
                VisitorHelper.MethodInfo(access, name, desc, signature, exceptions)
            syncMethods.add(methodInfo)
            // We record the annotations of the method when visiting it
            // Later when we recreate the method without synchronized, we add the annotations back
            // See visitEnd
            mv =
                JmcRecordMethodVisitor(
                    super.visitMethod(
                        access and Opcodes.ACC_SYNCHRONIZED.inv(),
                        methodInfo.unsyncName,
                        desc,
                        signature,
                        exceptions
                    ),
                    methodInfo
                )
        } else {
            mv = super.visitMethod(access, name, desc, signature, exceptions)
        }

        if (jmcSyncScanData.hasSyncBlocks()) {
            // If there are sync blocks, we still need to instrument monitorenter/monitorexit
            mv = JmcSyncBlockMethodVisitor(mv)
        }
        return mv
    }

    override fun visitEnd() {
        for (methodInfo in syncMethods) {
            addSyncMethod(methodInfo)
        }
        super.visitEnd()
    }

    // A recursive method to replay the values of the annotation for the given annotation visitor
    private fun writeAnnotationValue(
        annotationVisitor: AnnotationVisitor, name: String, value: VisitorHelper.AnnotationValue
    ) {
        when (value.type()) {
            VisitorHelper.AnnotationValue.Type.Primitive -> {
                annotationVisitor.visit(name, (value as VisitorHelper.PrimitiveValue).value)
                break
            }

            VisitorHelper.AnnotationValue.Type.Enum -> {
                val ev = value as VisitorHelper.EnumValue
                annotationVisitor.visitEnum(name, ev.descriptor, ev.value)
                break
            }

            VisitorHelper.AnnotationValue.Type.Array -> {
                val arrayVisitor = annotationVisitor.visitArray(name)
                for (v in (value as VisitorHelper.ArrayValue).values) {
                    writeAnnotationValue(arrayVisitor, name, v)
                }
                break
            }

            VisitorHelper.AnnotationValue.Type.Nested -> {
                val nested =
                    value as NestedAnnotationValue
                val nestedVisitor =
                    annotationVisitor.visitAnnotation(name, nested.nested.descriptor)
                for ((key, value1) in nested.nested.values) {
                    writeAnnotationValue(nestedVisitor, key, value1)
                }
                nestedVisitor.visitEnd()
                break
            }
        }
    }

    private fun addSyncMethod(methodInfo: VisitorHelper.MethodInfo) {
        val newMv =
            cv.visitMethod(
                methodInfo.nonSyncAccess,
                methodInfo.name,
                methodInfo.descriptor,
                methodInfo.signature,
                methodInfo.exceptions
            )

        val parameterNames = methodInfo.parameterNames
        val parameterAccesses = methodInfo.parameterAccesses
        for (i in parameterNames.indices) {
            newMv.visitParameter(parameterNames[i], parameterAccesses[i]!!)
        }

        for (ann in methodInfo.annotations) {
            val newMvAv =
                newMv.visitAnnotation(ann.descriptor, ann.visibility)
            for ((key, value) in ann.values) {
                writeAnnotationValue(newMvAv, key, value)
            }
            newMvAv.visitEnd()
        }

        newMv.visitCode()

        val l0 = Label()
        val l1 = Label()
        val l2 = Label()
        val l3 = Label()
        val l4 = Label()
        val l5 = Label()
        val l6 = Label()

        // try {
        newMv.visitTryCatchBlock(l0, l1, l2, null)

        // lock
        newMv.visitLabel(l0)

        if (methodInfo.isStatic) {
            newMv.visitLdcInsn(className)
            newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "registerSyncLock",
                "(Ljava/lang/String;)V",
                false
            )
        }
        if (methodInfo.isStatic) {
            newMv.visitLdcInsn(className)
        } else {
            newMv.visitIntInsn(Opcodes.ALOAD, 0)
        }
        newMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "syncMethodLock",
            if (methodInfo.isStatic) "(Ljava/lang/String;)V" else "(Ljava/lang/Object;)V",
            false
        )

        // Load all the parameters
        val argTypes = Type.getArgumentTypes(methodInfo.descriptor)
        val returnType = Type.getReturnType(methodInfo.descriptor)

        var slot = 0
        // load parameters
        if (!methodInfo.isStatic) {
            // this if not static
            newMv.visitIntInsn(Opcodes.ALOAD, slot++)
        }
        for (t in argTypes) {
            newMv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot)
            slot += t.size // long/double take 2
        }

        // Invoke the actual method
        if (methodInfo.isStatic) {
            newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                className,
                methodInfo.unsyncName,
                methodInfo.descriptor,
                false
            )
        } else {
            newMv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                className,
                methodInfo.unsyncName,
                methodInfo.descriptor,
                false
            )
        }
        newMv.visitLabel(l1)

        // No error unlock
        if (methodInfo.isStatic) {
            newMv.visitLdcInsn(className)
        } else {
            newMv.visitIntInsn(Opcodes.ALOAD, 0)
        }
        newMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "syncMethodUnLock",
            if (methodInfo.isStatic) "(Ljava/lang/String;)V" else "(Ljava/lang/Object;)V",
            false
        )
        newMv.visitLabel(l3)
        newMv.visitJumpInsn(Opcodes.GOTO, l4)

        // Error occurred. Unlock and throw exception.
        newMv.visitLabel(l2)
        // Visit frame for throwable and store the exception
        newMv.visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Throwable"))
        newMv.visitIntInsn(Opcodes.ASTORE, argTypes.size)
        // Unlock
        if (methodInfo.isStatic) {
            newMv.visitLdcInsn(className)
        } else {
            newMv.visitIntInsn(Opcodes.ALOAD, 0)
        }
        newMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "syncMethodUnLock",
            if (methodInfo.isStatic) "(Ljava/lang/String;)V" else "(Ljava/lang/Object;)V",
            false
        )
        newMv.visitLabel(l5)
        newMv.visitIntInsn(Opcodes.ALOAD, argTypes.size)
        newMv.visitInsn(Opcodes.ATHROW)

        // Done. Return
        newMv.visitLabel(l4)
        newMv.visitFrame(Opcodes.F_SAME, 0, null, 1, arrayOf<Any>("java/lang/Throwable"))
        VisitorHelper.addReturnInst(newMv, methodInfo.descriptor)
        newMv.visitLabel(l6)

        // Visit this local variable
        if (methodInfo.isStatic) {
            newMv.visitLocalVariable("this", "L$className;", null, l0, l6, 0)
        }
        newMv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l2, l4, slot)
        newMv.visitMaxs(-1, -1) // Auto-compute stack size and locals
        newMv.visitEnd()
    }

    private class JmcSyncMethodConstMethodVisitor(
        mv: MethodVisitor?,
        private val useInstance: Boolean,
        private val className: String
    ) :
        MethodVisitor(Opcodes.ASM5, mv) {
        override fun visitInsn(opcode: Int) {
            if (opcode == Opcodes.RETURN) {
                if (useInstance) {
                    mv.visitIntInsn(Opcodes.ALOAD, 0)
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                        "registerSyncLock",
                        "(Ljava/lang/Object;)V",
                        false
                    )
                } else {
                    mv.visitLdcInsn(className)
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                        "registerSyncLock",
                        "(Ljava/lang/String;)V",
                        false
                    )
                }
            }
            super.visitInsn(opcode)
        }
    }

    private class JmcSyncBlockMethodVisitor(mv: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitInsn(opcode: Int) {
            if (opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT) {
                // No additional handling needed for sync blocks
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    if (opcode == Opcodes.MONITORENTER) "syncBlockLock" else "syncBlockUnLock",
                    "(Ljava/lang/Object;)V",
                    false
                )
            } else {
                super.visitInsn(opcode)
            }
        }
    }

    private class JmcRecordMethodVisitor(mv: MethodVisitor?, private val methodInfo: VisitorHelper.MethodInfo) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitParameter(name: String, access: Int) {
            methodInfo.addParameter(name, access)
            super.visitParameter(name, access)
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            val annotationInfo =
                VisitorHelper.AnnotationInfo(descriptor, visible)
            methodInfo.addAnnotation(annotationInfo)
            return JmcAnnotationRecordVisitor(annotationInfo)
        }
    }
}
