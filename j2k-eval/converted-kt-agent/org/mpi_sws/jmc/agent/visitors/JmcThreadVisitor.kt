package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.*
import java.util.*

/**
 * Represents a JMC thread visitor. Adds instrumentation to change Thread calls to JmcThread calls
 */
class JmcThreadVisitor {
    class ThreadClassVisitor(cv: ClassVisitor?) :
        ClassVisitor(Opcodes.ASM9, cv) {
        // Flag to indicate that the class being visited extends Thread.
        private var isExtendingThread = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String,
            superName: String,
            interfaces: Array<String>
        ) {
            // Check if the class extends java/lang/Thread
            var superName = superName
            if (THREAD_PATH == superName) {
                isExtendingThread = true
                // Replace the superclass with JmcThread (ensure the internal name is correct)
                superName = JMC_THREAD_PATH
            }
            // Continue visiting with the possibly modified superclass.
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(
            access: Int, name: String, descriptor: String, signature: String, value: Any
        ): FieldVisitor {
            // Replace Thread field types with JmcThread
            return super.visitField(access, name, replaceDescriptor(descriptor), signature, value)
        }

        override fun visitMethod(
            access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
        ): MethodVisitor {
            val mv: MethodVisitor
            // Only instrument if the class extends Thread and this is a constructor
            if (isExtendingThread && "<init>" == name) {
                mv =
                    ThreadInitMethodVisitor(
                        super.visitMethod(
                            access,
                            name,
                            replaceDescriptor(descriptor),
                            signature,
                            exceptions
                        )
                    )
            } else if (isExtendingThread && "run" == name && "()V" == descriptor) {
                // Rename it to "run1" by passing the new name into the visitMethod call.
                mv = super.visitMethod(access, "run1", descriptor, signature, exceptions)
                val av = mv.visitAnnotation("Override", true)
                av.visitEnd()
            } else {
                mv =
                    super.visitMethod(
                        access, name, replaceDescriptor(descriptor), signature, exceptions
                    )
            }
            return ThreadInstanceMethodVisitor(mv)
        }

        private class ThreadInstanceMethodVisitor(mv: MethodVisitor?) :
            MethodVisitor(Opcodes.ASM9, mv) {
            override fun visitTypeInsn(opcode: Int, type: String) {
                // Replace Thread with JmcThread in instance creation
                if (VisitorHelper.isInstantiation(opcode) && THREAD_PATH == type) {
                    super.visitTypeInsn(opcode, JMC_THREAD_PATH)
                } else {
                    super.visitTypeInsn(opcode, replaceType(type))
                }
            }

            override fun visitMethodInsn(
                opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
            ) {
                super.visitMethodInsn(
                    opcode,
                    replaceType(owner),
                    name,
                    replaceDescriptor(descriptor),
                    isInterface
                )
            }

            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor))
            }

            override fun visitLocalVariable(
                name: String,
                descriptor: String,
                signature: String,
                start: Label,
                end: Label,
                index: Int
            ) {
                super.visitLocalVariable(
                    name, replaceDescriptor(descriptor), signature, start, end, index
                )
            }

            override fun visitInvokeDynamicInsn(
                name: String, descriptor: String, bsm: Handle?, vararg bsmArgs: Any
            ) {
                val isThreadType = descriptor.contains(THREAD_PATH)
                        || (bsm != null && bsm.owner.contains(THREAD_PATH))

                // Check if descriptor or bootstrap method involves Atomic types
                if (isThreadType) {
                    var newBsm = bsm
                    val newDescriptor = replaceDescriptor(descriptor)
                    if (bsm != null) {
                        val owner = bsm.owner
                        val newOwner = replaceType(owner)
                        val bsmDesc = bsm.desc
                        val newbsmDesc = replaceDescriptor(bsmDesc)
                        newBsm = Handle(bsm.tag, newOwner, bsm.name, newbsmDesc, bsm.isInterface)
                    }
                    val tempBsmArgs = Arrays.stream(bsmArgs).toArray()
                    val newBsmArgs = arrayOfNulls<Any>(tempBsmArgs.size)
                    for (i in tempBsmArgs.indices) {
                        if (tempBsmArgs[i] is Type) {
                            val className: String = t.getInternalName()
                            newBsmArgs[i] = Type.getType(replaceType(className))
                        }
                        if (tempBsmArgs[i] is Handle) {
                            val desc = replaceDescriptor(h.getDesc())
                            newBsmArgs[i] = Handle(
                                h.getTag(),
                                replaceType(h.getOwner()),
                                h.getName(),
                                desc,
                                h.isInterface()
                            )
                        }
                    }
                    super.visitInvokeDynamicInsn(name, newDescriptor, newBsm, *newBsmArgs)
                } else {
                    super.visitInvokeDynamicInsn(name, descriptor, bsm, *bsmArgs)
                }
            }
        }

        // Nested MethodVisitor to modify constructor calls
        private class ThreadInitMethodVisitor(mv: MethodVisitor?) :
            MethodVisitor(Opcodes.ASM9, mv) {
            override fun visitMethodInsn(
                opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
            ) {
                // Check if this is a call to Thread's constructor
                if (opcode == Opcodes.INVOKESPECIAL && "java/lang/Thread" == owner
                    && "<init>" == name
                ) {
                    // Replace with call to JmcThread's constructor
                    super.visitMethodInsn(
                        Opcodes.INVOKESPECIAL,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcThread",
                        name,
                        descriptor,
                        isInterface
                    )
                } else {
                    // Pass through unchanged
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }
            }
        }

        companion object {
            private const val THREAD_PATH = "java/lang/Thread"
            private const val JMC_THREAD_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcThread"
            private const val THREAD_DESC = "L" + THREAD_PATH + ";"
            private const val JMC_THREAD_DESC = "L" + JMC_THREAD_PATH + ";"

            private fun replaceDescriptor(desc: String): String {
                if (desc.contains(THREAD_DESC)) {
                    return desc.replace(THREAD_DESC, JMC_THREAD_DESC)
                }
                return desc
            }

            private fun replaceType(type: String): String {
                if (type == THREAD_PATH) {
                    return JMC_THREAD_PATH
                }
                return type
            }
        }
    }

    /**
     * ClassVisitor that replaces calls to "run" and "join" on objects that extend Thread with calls
     * to "run1" and "join1" respectively.
     */
    class ThreadCallReplacerClassVisitor
    /**
     * Constructor.
     *
     * @param cv The underlying ClassVisitor
     */
        (cv: ClassVisitor?) : ClassVisitor(Opcodes.ASM9, cv) {
        override fun visitMethod(
            access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
        ): MethodVisitor {
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return ThreadCallReplacerMethodVisitor(mv)
        }
    }

    /**
     * MethodVisitor that replaces calls to "run" and "join" on objects that extend Thread with
     * calls to "run1" and "join1" respectively.
     */
    class ThreadCallReplacerMethodVisitor
    /**
     * Constructor.
     *
     * @param mv The underlying MethodVisitor
     */
        (mv: MethodVisitor?) : MethodVisitor(Opcodes.ASM9, mv) {
        /**
         * Visit method invocation instructions. If the instruction is a call "join" on an object
         * whose class extends Thread, replace it with a call to "join1".
         */
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            if (name == "join" && opcode == Opcodes.INVOKEVIRTUAL && JOIN_DESCRIPTORS.contains(descriptor)) {
                // Duplicate top of the stack (the object on which join() is called)
                mv.visitInsn(Opcodes.DUP)

                // Call JmcRuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "shouldInstrumentThreadCall",
                    "(Ljava/lang/Object;)Z",
                    false
                )

                // Create the if-else block
                val originalCall = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall)

                // Call JmcRuntimeUtils.join()
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "join",
                    matchDescriptor(descriptor),
                    false
                )

                // Skip the original call
                val end = Label()
                mv.visitJumpInsn(Opcodes.GOTO, end)

                // Original join() method call
                mv.visitLabel(originalCall)
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

                // End label
                mv.visitLabel(end)
            } else if (name == "yield" && opcode == Opcodes.INVOKEVIRTUAL) {
                // Duplicate top of the stack (the object on which join() is called)
                mv.visitInsn(Opcodes.DUP)

                // Call JmcRuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "shouldInstrumentThreadCall",
                    "(Ljava/lang/Object;)Z",
                    false
                )

                // Create the if-else block
                val originalCall = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall)

                // Call JmcRuntime.yield()
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntime",
                    "yield",
                    "()V",
                    false
                )

                // Skip the original call
                val end = Label()
                mv.visitJumpInsn(Opcodes.GOTO, end)

                // Original yield() method call
                mv.visitLabel(originalCall)
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

                // End label
                mv.visitLabel(end)
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }

        private fun matchDescriptor(descriptor: String): String {
            if (descriptor == "()V") {
                return "(Ljava/lang/Thread;)V"
            } else if (descriptor == "(J)V") {
                return "(Ljava/lang/Thread;J)V"
            }
            return "(Ljava/lang/Thread;JI)V"
        }

        companion object {
            private val JOIN_DESCRIPTORS: Set<String> = setOf<String>("()V", "(J)V", "(JI)V", "(Ljava/time/Duration;)Z")
        }
    }
}
