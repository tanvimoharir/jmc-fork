package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.*
import java.util.*

/**
 * This class is an ASM ClassVisitor that replaces standard Java Atomic classes with JMC Atomic
 * classes. It modifies field descriptors, method descriptors, and type instructions to ensure that
 * the JMC versions are used instead of the standard Java versions.
 */
class JmcAtomicVisitor(cv: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, cv) {
    private var isExtendingAtomic = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        if (checkIfAtomic(superName)) {
            isExtendingAtomic = true
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
        val mv = if (isExtendingAtomic && "<init>" == name) {
            AtomicInitMethodVisitor(
                super.visitMethod(
                    access,
                    name,
                    replaceDescriptor(descriptor),
                    signature,
                    exceptions
                )
            )
        } else {
            // First let the parent handle the method visitor creation
            super.visitMethod(
                access, name, replaceDescriptor(descriptor), signature, exceptions
            )
        }
        // Return a new visitor that will handle Atomic types
        return AtomicReplacementMethodVisitor(mv)
    }

    private class AtomicReplacementMethodVisitor(mv: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitTypeInsn(opcode: Int, type: String) {
            // Replace NEW Atomic types with JmcAtomic types
            if (VisitorHelper.isInstantiation(opcode)) {
                super.visitTypeInsn(opcode, replaceType(type))
            } else {
                super.visitTypeInsn(opcode, replaceType(type))
            }
        }

        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            // Replace Atomic type constructor calls
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
            val isAtomicType = descriptor.contains("java/util/concurrent/atomic/Atomic")
                    || (bsm != null && bsm.owner.contains("java/util/concurrent/atomic/Atomic"))

            // Check if descriptor or bootstrap method involves Atomic types
            if (isAtomicType) {
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

    private class AtomicInitMethodVisitor(mv: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            if (opcode == Opcodes.INVOKESPECIAL && checkIfAtomic(owner)
                && name == "<init>"
            ) {
                super.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    replaceType(owner),
                    name,
                    descriptor,
                    isInterface
                )
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }

    companion object {
        private const val ATOMIC_INTEGER_PATH = "java/util/concurrent/atomic/AtomicInteger"
        private const val JMC_ATOMIC_INTEGER_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger"
        private const val ATOMIC_INTEGER_DESC = "L" + ATOMIC_INTEGER_PATH + ";"
        private const val JMC_ATOMIC_INTEGER_DESC = "L" + JMC_ATOMIC_INTEGER_PATH + ";"

        private const val ATOMIC_LONG_PATH = "java/util/concurrent/atomic/AtomicLong"
        private const val JMC_ATOMIC_LONG_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong"
        private const val ATOMIC_LONG_DESC = "L" + ATOMIC_LONG_PATH + ";"
        private const val JMC_ATOMIC_LONG_DESC = "L" + JMC_ATOMIC_LONG_PATH + ";"

        private const val ATOMIC_BOOLEAN_PATH = "java/util/concurrent/atomic/AtomicBoolean"
        private const val JMC_ATOMIC_BOOLEAN_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean"
        private const val ATOMIC_BOOLEAN_DESC = "L" + ATOMIC_BOOLEAN_PATH + ";"
        private const val JMC_ATOMIC_BOOLEAN_DESC = "L" + JMC_ATOMIC_BOOLEAN_PATH + ";"

        private const val ATOMIC_REFERENCE_PATH = "java/util/concurrent/atomic/AtomicReference"
        private const val JMC_ATOMIC_REFERENCE_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference"
        private const val ATOMIC_REFERENCE_DESC = "L" + ATOMIC_REFERENCE_PATH + ";"
        private const val JMC_ATOMIC_REFERENCE_DESC = "L" + JMC_ATOMIC_REFERENCE_PATH + ";"

        private const val ATOMIC_MARKABLE_REFERENCE_PATH = "java/util/concurrent/atomic/AtomicMarkableReference"
        private const val JMC_ATOMIC_MARKABLE_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicMarkableReference"
        private const val ATOMIC_MARKABLE_REFERENCE_DESC = "L" + ATOMIC_MARKABLE_REFERENCE_PATH + ";"
        private const val JMC_ATOMIC_MARKABLE_REFERENCE_DESC = "L" + JMC_ATOMIC_MARKABLE_REFERENCE_PATH + ";"

        private const val ATOMIC_INTEGER_ARRAY_PATH = "java/util/concurrent/atomic/AtomicIntegerArray"
        private const val JMC_ATOMIC_INTEGER_ARRAY_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicIntegerArray"
        private const val ATOMIC_INTEGER_ARRAY_DESC = "L" + ATOMIC_INTEGER_ARRAY_PATH + ";"
        private const val JMC_ATOMIC_INTEGER_ARRAY_DESC = "L" + JMC_ATOMIC_INTEGER_ARRAY_PATH + ";"

        private const val ATOMIC_LONG_ARRAY_PATH = "java/util/concurrent/atomic/AtomicLongArray"
        private const val JMC_ATOMIC_LONG_ARRAY_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLongArray"
        private const val ATOMIC_LONG_ARRAY_DESC = "L" + ATOMIC_LONG_ARRAY_PATH + ";"
        private const val JMC_ATOMIC_LONG_ARRAY_DESC = "L" + JMC_ATOMIC_LONG_ARRAY_PATH + ";"

        private const val ATOMIC_REFERENCE_ARRAY_PATH = "java/util/concurrent/atomic/AtomicReferenceArray"
        private const val JMC_ATOMIC_REFERENCE_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReferenceArray"
        private const val ATOMIC_REFERENCE_ARRAY_DESC = "L" + ATOMIC_REFERENCE_ARRAY_PATH + ";"
        private const val JMC_ATOMIC_REFERENCE_ARRAY_DESC = "L" + JMC_ATOMIC_REFERENCE_ARRAY_PATH + ";"

        private const val ATOMIC_STAMPED_REFERENCE_PATH = "java/util/concurrent/atomic/AtomicStampedReference"
        private const val JMC_ATOMIC_STAMPED_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference"
        private const val ATOMIC_STAMPED_REFERENCE_DESC = "L" + ATOMIC_STAMPED_REFERENCE_PATH + ";"
        private const val JMC_ATOMIC_STAMPED_REFERENCE_DESC = "L" + JMC_ATOMIC_STAMPED_REFERENCE_PATH + ";"

        private const val ATOMIC_INTEGER_FIELD_PATH = "java/util/concurrent/atomic/AtomicIntegerFieldUpdater"
        private const val JMC_ATOMIC_INTEGER_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicIntegerFieldUpdater"
        private const val ATOMIC_INTEGER_FIELD_DESC = "L" + ATOMIC_INTEGER_FIELD_PATH + ";"
        private const val JMC_ATOMIC_INTEGER_FIELD_DESC = "L" + JMC_ATOMIC_INTEGER_FIELD_PATH + ";"

        private const val ATOMIC_LONG_FIELD_PATH = "java/util/concurrent/atomic/AtomicLongFieldUpdater"
        private const val JMC_ATOMIC_LONG_FIELD_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLongFieldUpdater"
        private const val ATOMIC_LONG_FIELD_DESC = "L" + ATOMIC_LONG_FIELD_PATH + ";"
        private const val JMC_ATOMIC_LONG_FIELD_DESC = "L" + JMC_ATOMIC_LONG_FIELD_PATH + ";"

        private const val ATOMIC_REFERENCE_FIELD_PATH = "java/util/concurrent/atomic/AtomicReferenceFieldUpdater"
        private const val JMC_ATOMIC_REFERENCE_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReferenceFieldUpdater"
        private const val ATOMIC_REFERENCE_FIELD_DESC = "L" + ATOMIC_REFERENCE_FIELD_PATH + ";"
        private const val JMC_ATOMIC_REFERENCE_FIELD_DESC = "L" + JMC_ATOMIC_REFERENCE_FIELD_PATH + ";"

        private fun replaceDescriptor(desc: String): String {
            var newDesc = desc
            if (newDesc.contains(ATOMIC_INTEGER_DESC)) {
                newDesc = newDesc.replace(ATOMIC_INTEGER_DESC, JMC_ATOMIC_INTEGER_DESC)
            }
            if (newDesc.contains(ATOMIC_LONG_DESC)) {
                newDesc = newDesc.replace(ATOMIC_LONG_DESC, JMC_ATOMIC_LONG_DESC)
            }
            if (newDesc.contains(ATOMIC_BOOLEAN_DESC)) {
                newDesc = newDesc.replace(ATOMIC_BOOLEAN_DESC, JMC_ATOMIC_BOOLEAN_DESC)
            }
            if (newDesc.contains(ATOMIC_REFERENCE_DESC)) {
                newDesc = newDesc.replace(ATOMIC_REFERENCE_DESC, JMC_ATOMIC_REFERENCE_DESC)
            }
            if (newDesc.contains(ATOMIC_MARKABLE_REFERENCE_DESC)) {
                newDesc =
                    newDesc.replace(
                        ATOMIC_MARKABLE_REFERENCE_DESC, JMC_ATOMIC_MARKABLE_REFERENCE_DESC
                    )
            }
            if (newDesc.contains(ATOMIC_INTEGER_ARRAY_DESC)) {
                newDesc = newDesc.replace(ATOMIC_INTEGER_ARRAY_DESC, JMC_ATOMIC_INTEGER_ARRAY_DESC)
            }
            if (newDesc.contains(ATOMIC_LONG_ARRAY_DESC)) {
                newDesc = newDesc.replace(ATOMIC_LONG_ARRAY_DESC, JMC_ATOMIC_LONG_ARRAY_DESC)
            }
            if (newDesc.contains(ATOMIC_REFERENCE_ARRAY_DESC)) {
                newDesc = newDesc.replace(ATOMIC_REFERENCE_ARRAY_DESC, JMC_ATOMIC_REFERENCE_ARRAY_DESC)
            }
            if (newDesc.contains(ATOMIC_STAMPED_REFERENCE_DESC)) {
                newDesc =
                    newDesc.replace(
                        ATOMIC_STAMPED_REFERENCE_DESC, JMC_ATOMIC_STAMPED_REFERENCE_DESC
                    )
            }
            if (newDesc.contains(ATOMIC_INTEGER_FIELD_DESC)) {
                newDesc = newDesc.replace(ATOMIC_INTEGER_FIELD_DESC, JMC_ATOMIC_INTEGER_FIELD_DESC)
            }
            if (newDesc.contains(ATOMIC_LONG_FIELD_DESC)) {
                newDesc = newDesc.replace(ATOMIC_LONG_FIELD_DESC, JMC_ATOMIC_LONG_FIELD_DESC)
            }
            if (newDesc.contains(ATOMIC_REFERENCE_FIELD_DESC)) {
                newDesc = newDesc.replace(ATOMIC_REFERENCE_FIELD_DESC, JMC_ATOMIC_REFERENCE_FIELD_DESC)
            }
            return newDesc
        }

        private fun replaceType(type: String): String {
            if (type == ATOMIC_INTEGER_PATH) {
                return JMC_ATOMIC_INTEGER_PATH
            } else if (type == ATOMIC_LONG_PATH) {
                return JMC_ATOMIC_LONG_PATH
            } else if (type == ATOMIC_BOOLEAN_PATH) {
                return JMC_ATOMIC_BOOLEAN_PATH
            } else if (type == ATOMIC_REFERENCE_PATH) {
                return JMC_ATOMIC_REFERENCE_PATH
            } else if (type == ATOMIC_MARKABLE_REFERENCE_PATH) {
                return JMC_ATOMIC_MARKABLE_REFERENCE_PATH
            } else if (type == ATOMIC_INTEGER_ARRAY_PATH) {
                return JMC_ATOMIC_INTEGER_ARRAY_PATH
            } else if (type == ATOMIC_LONG_ARRAY_PATH) {
                return JMC_ATOMIC_LONG_ARRAY_PATH
            } else if (type == ATOMIC_REFERENCE_ARRAY_PATH) {
                return JMC_ATOMIC_REFERENCE_ARRAY_PATH
            } else if (type == ATOMIC_STAMPED_REFERENCE_PATH) {
                return JMC_ATOMIC_STAMPED_REFERENCE_PATH
            } else if (type == ATOMIC_INTEGER_FIELD_PATH) {
                return JMC_ATOMIC_INTEGER_FIELD_PATH
            } else if (type == ATOMIC_LONG_FIELD_PATH) {
                return JMC_ATOMIC_LONG_FIELD_PATH
            } else if (type == ATOMIC_REFERENCE_FIELD_PATH) {
                return JMC_ATOMIC_REFERENCE_FIELD_PATH
            }
            return type
        }

        private fun checkIfAtomic(classPath: String): Boolean {
            return classPath.startsWith("java/util/concurrent/atomic/Atomic")
        }
    }
}
