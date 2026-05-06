package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Represents a JMC read-write visitor. Adds instrumentation to change field accesses to
 * JmcReadWrite calls.
 */
class JmcReadWriteVisitor {
    /**
     * Class visitor for JMC read-write visitor.
     */
    class ReadWriteClassVisitor
    /**
     * Constructor.
     *
     * @param cv The underlying ClassVisitor
     */
        (cv: ClassVisitor?) : ClassVisitor(Opcodes.ASM9, cv) {
        private var isInterface = false
        private val skipInstrumentation = false

        /** Set of final field names in this class (format: "owner/name")  */
        private val finalFields: MutableSet<String> = HashSet()

        private var className: String? = null

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String,
            superName: String,
            interfaces: Array<String>
        ) {
            className = name

            if ((access and Opcodes.ACC_INTERFACE) != 0) {
                isInterface = true
            }

            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            value: Any
        ): FieldVisitor {
            // Track final fields
            if ((access and Opcodes.ACC_FINAL) != 0) {
                finalFields.add("$className/$name")
            }
            return super.visitField(access, name, descriptor, signature, value)
        }


        override fun visitMethod(
            access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
        ): MethodVisitor {
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (skipInstrumentation) {
                return mv
            }
            if (isInterface && name == "<clinit>") {
                // If this is an interface static initializer, we do not instrument it
                return mv
            }

            return ReadWriteMethodVisitor(mv, access, descriptor, "<init>" == name, className, finalFields)
        }
    }

    /**
     * Method visitor for JMC read-write visitor.
     */
    class ReadWriteMethodVisitor
    /**
     * Constructor.
     *
     * @param mv         The underlying MethodVisitor
     * @param access     The method's access flags
     * @param descriptor The method descriptor (e.g., "(I)V")
     * @param constructor Whether this is a constructor
     * @param className The name of the class being visited
     * @param finalFields Set of final field keys (format: "owner/name")
     */(
        mv: MethodVisitor?, access: Int, descriptor: String, private val constructor: Boolean,
        private val className: String?, private val finalFields: Set<String>
    ) : LocalVarTrackingMethodVisitor(Opcodes.ASM9, mv, access, descriptor) {
        private var instrumented = false

        private var constructorInitialized = false


        private fun insertUpdateEventCall(
            owner: String, isStatic: Boolean, isWrite: Boolean, name: String, descriptor: String
        ) {
            if (owner == "java/lang/System") {
                // Ignore System calls
                return
            }
            if (name == "\$assertionsDisabled") {
                // Ignore assertionsDisabled field
                return
            }
            if (constructorNotInitialized()) {
                return
            }
            // Skip final fields - they don't need synchronization
            val fieldKey = "$owner/$name"
            if (finalFields.contains(fieldKey)) {
                return
            }
            instrumented = true
            if (!isWrite) {
                VisitorHelper.insertRead(mv, isStatic, owner, name, descriptor)
            } else {
                VisitorHelper.insertWrite(mv, isStatic, owner, name, descriptor)
            }
        }

        private fun constructorNotInitialized(): Boolean {
            // The method we are visiting is either
            // 1. not a constructor
            // 2. or a constructor that has been initialized
            return constructor && !constructorInitialized
        }

        /**
         * Instrument field accesses. GETFIELD and GETSTATIC are considered "Read" accesses,
         * PUTFIELD and PUTSTATIC are considered "Write" accesses.
         *
         *
         * For put instructions the top of the stack is duplicated based on the type of the
         * field.
         */
        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            var shouldInstrument = false
            var isWrite = false
            var isStatic = false

            if (opcode == Opcodes.GETFIELD) {
                shouldInstrument = true
            } else if (opcode == Opcodes.GETSTATIC) {
                shouldInstrument = true
                isStatic = true
            } else if (opcode == Opcodes.PUTFIELD) {
                shouldInstrument = true
                isWrite = true
            } else if (opcode == Opcodes.PUTSTATIC) {
                shouldInstrument = true
                isWrite = true
                isStatic = true
            }

            if (shouldInstrument && isStatic && !isWrite) {
                // For static field READS (GETSTATIC): execute field access first, then instrument
                super.visitFieldInsn(opcode, owner, name, descriptor)
                insertStaticReadAfterCall(owner, name, descriptor)
                if (instrumented) {
                    VisitorHelper.insertYield(mv)
                    instrumented = false
                }
            } else if (shouldInstrument && isStatic && isWrite) {
                // For static field WRITES (PUTSTATIC): duplicate value, execute write, then instrument
                if (shouldInstrumentField(owner, name)) {
                    VisitorHelper.insertStaticWriteBefore(mv, descriptor)
                    instrumented = true
                    super.visitFieldInsn(opcode, owner, name, descriptor)
                    VisitorHelper.insertStaticWriteAfter(mv, owner, name, descriptor)
                    VisitorHelper.insertYield(mv)
                    instrumented = false
                } else {
                    // Field should not be instrumented, just execute the instruction
                    super.visitFieldInsn(opcode, owner, name, descriptor)
                }
            } else if (shouldInstrument) {
                // For instance fields: instrument first, then execute
                insertUpdateEventCall(owner, false, isWrite, name, descriptor)
                super.visitFieldInsn(opcode, owner, name, descriptor)
                if (instrumented) {
                    VisitorHelper.insertYield(mv)
                    instrumented = false
                }
            } else {
                // No instrumentation needed
                super.visitFieldInsn(opcode, owner, name, descriptor)
            }
        }

        /**
         * Checks if a field should be instrumented based on various filters.
         */
        private fun shouldInstrumentField(owner: String, name: String): Boolean {
            if (owner == "java/lang/System") {
                return false
            }
            if (name == "\$assertionsDisabled") {
                return false
            }
            if (constructorNotInitialized()) {
                return false
            }
            val fieldKey = "$owner/$name"
            if (finalFields.contains(fieldKey)) {
                return false
            }
            return true
        }

        /**
         * Inserts instrumentation for static field reads after the GETSTATIC instruction.
         */
        private fun insertStaticReadAfterCall(owner: String, name: String, descriptor: String) {
            if (!shouldInstrumentField(owner, name)) {
                return
            }
            instrumented = true
            VisitorHelper.insertStaticReadAfter(mv, owner, name, descriptor)
        }


        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            if (opcode == Opcodes.INVOKESPECIAL) {
                // We do not instrument method calls in this visit method
                if (name == "<init>") {
                    // If this is a constructor, we need to track if it has been initialized
                    constructorInitialized = true
                }
            }
            // We do not instrument method calls in this visit method
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            if (instrumented) {
                super.visitMaxs(maxStack + 3, maxLocals)
            } else {
                super.visitMaxs(maxStack, maxLocals)
            }
        }
    }
}
