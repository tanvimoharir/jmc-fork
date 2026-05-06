package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.*
import java.util.*

/**
 * Represents a JMC reentrant lock visitor. Replaces calls to ReentrantLock with JmcReentrantLock.
 */
class JmcReentrantLockVisitor(cv: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, cv) {
    private var isExtendingReentrantLock = false

    /**
     * @param version
     * @param access
     * @param name
     * @param signature
     * @param superName
     * @param interfaces
     */
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        if (superName == REENTRANT_LOCK_PATH) {
            isExtendingReentrantLock = true
        }
        super.visit(version, access, name, signature, replaceType(superName), interfaces)
    }

    override fun visitField(
        access: Int, name: String, descriptor: String, signature: String, value: Any
    ): FieldVisitor {
        return super.visitField(access, name, replaceDescriptor(descriptor), signature, value)
    }

    override fun visitMethod(
        access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        val mv = if (isExtendingReentrantLock && name == "<init>") {
            // Special handling for constructors of classes extending ReentrantLock
            ReentrantLockInitMethodVisitor(
                super.visitMethod(
                    access, name, replaceDescriptor(descriptor), signature, exceptions
                )
            )
        } else {
            super.visitMethod(
                access, name, replaceDescriptor(descriptor), signature, exceptions
            )
        }
        return ReentrantLockReplacementMethodVisitor(mv)
    }

    private class ReentrantLockReplacementMethodVisitor(mv: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitTypeInsn(opcode: Int, type: String) {
            // Replace NEW ReentrantLock with JmcReentrantLock
            if (VisitorHelper.isInstantiation(opcode)) {
                super.visitTypeInsn(opcode, replaceType(type))
            } else {
                super.visitTypeInsn(opcode, replaceType(type))
            }
        }

        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            // Replace ReentrantLock constructor calls
            super.visitMethodInsn(
                opcode, replaceType(owner), name, replaceDescriptor(descriptor), isInterface
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
            val isReentrantLockType = descriptor.contains("java/util/concurrent/locks/ReentrantLock")
                    || (bsm != null && bsm.owner.contains("java/util/concurrent/locks/ReentrantLock"))
            // Check if descriptor or bootstrap method involves ReentrantLock
            if (isReentrantLockType) {
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

    private class ReentrantLockInitMethodVisitor(mv: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            if (opcode == Opcodes.INVOKESTATIC && owner == JMC_REENTRANT_LOCK_PATH
                && name == "<init>"
            ) {
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    replaceType(owner),
                    "createJmcReentrantLock",
                    descriptor,
                    isInterface
                )
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }

    companion object {
        private const val REENTRANT_LOCK_PATH = "java/util/concurrent/locks/ReentrantLock"
        private const val JMC_REENTRANT_LOCK_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock"
        private const val REENTRANT_LOCK_DESC = "L" + REENTRANT_LOCK_PATH + ";"
        private const val JMC_REENTRANT_LOCK_DESC = "L" + JMC_REENTRANT_LOCK_PATH + ";"

        private fun replaceDescriptor(desc: String): String {
            if (desc.contains(REENTRANT_LOCK_DESC)) {
                return desc.replace(REENTRANT_LOCK_DESC, JMC_REENTRANT_LOCK_DESC)
            }
            return desc
        }

        private fun replaceType(type: String): String {
            if (type == REENTRANT_LOCK_PATH) {
                return JMC_REENTRANT_LOCK_PATH
            }
            return type
        }
    }
}
