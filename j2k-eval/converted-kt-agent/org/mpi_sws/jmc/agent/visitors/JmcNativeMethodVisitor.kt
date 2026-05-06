package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visitor that handles native Object method transformations for JMC.
 * Handles: hashCode, toString, equals
 *
 * NEW APPROACH:
 * - Keeps original methods unchanged (no renaming)
 * - Adds jmcHashCode(), jmcEquals(), jmcToString() methods that call super.method()
 * - Sets isOverridden flag to track which methods are overridden
 */
class JmcNativeMethodVisitor(cv: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, cv) {
    private var className: String? = null
    private var superName: String? = null
    private var isInterface = false

    // Track which methods are overridden
    private var hasHashCode = false
    private var hasToString = false
    private var hasEquals = false
    private var hasFinalize = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        this.className = name
        this.superName = superName
        this.isInterface = (access and Opcodes.ACC_INTERFACE) != 0
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        // Track which methods are overridden (but don't rename them)

        if (HASHCODE_NAME == name && HASHCODE_DESCRIPTOR == descriptor) {
            hasHashCode = true
        }

        if (TOSTRING_NAME == name && TOSTRING_DESCRIPTOR == descriptor) {
            hasToString = true
        }

        if (EQUALS_NAME == name && EQUALS_DESCRIPTOR == descriptor) {
            hasEquals = true
        }

        //For finalize we onlu rename the method so that the overridden method cannot be invoked
        if (FINALIZE_NAME == name && FINALIZE_DESCRIPTOR == descriptor) {
            hasFinalize = true
            val jmcFinalizeName = JMC_PREFIX + FINALIZE_NAME[0].uppercaseChar() + FINALIZE_NAME.substring(1)
            return super.visitMethod(access, jmcFinalizeName, descriptor, signature, exceptions)
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    override fun visitEnd() {
        if (!isInterface) {
            // Always create jmcHashCode(), jmcEquals(), jmcToString() methods
            // These call super.method() regardless of whether the class overrides them
            createJmcMethod(HASHCODE_NAME, HASHCODE_DESCRIPTOR, Opcodes.IRETURN)
            createJmcMethod(TOSTRING_NAME, TOSTRING_DESCRIPTOR, Opcodes.ARETURN)
            createJmcMethod(EQUALS_NAME, EQUALS_DESCRIPTOR, Opcodes.IRETURN)
        }
        super.visitEnd()
    }

    /**
     * Creates a jmcMethodName() method that delegates to super.methodName()
     * For example: jmcHashCode() calls super.hashCode()
     */
    private fun createJmcMethod(methodName: String, descriptor: String, returnOpcode: Int) {
        // Create method name: hashCode -> jmcHashCode
        val jmcMethodName = JMC_PREFIX + methodName[0].uppercaseChar() + methodName.substring(1)

        val mv = super.visitMethod(
            Opcodes.ACC_PUBLIC,
            jmcMethodName,
            descriptor,
            null,
            null
        )

        if (mv != null) {
            mv.visitCode()

            if (TOSTRING_NAME == methodName) {
                //Load this onto the stack
                mv.visitVarInsn(Opcodes.ALOAD, 0)

                //Call JmcObject.toString(this)
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/api/JmcObject",
                    "toString",
                    "(Ljava/lang/Object;)Ljava/lang/String;",
                    false
                )

                //return the result
                mv.visitInsn(returnOpcode)
                mv.visitMaxs(1, 1)
            } else {
                // Load 'this' onto the stack

                mv.visitVarInsn(Opcodes.ALOAD, 0)

                // Load parameters if any (for equals)
                if (EQUALS_NAME == methodName) {
                    mv.visitVarInsn(Opcodes.ALOAD, 1)
                }

                // Call super.methodName()
                mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    superName,
                    methodName,
                    descriptor,
                    false
                )

                // Return the result
                mv.visitInsn(returnOpcode)

                // Calculate max stack and locals
                val maxStack = if (EQUALS_NAME == methodName) 2 else 1
                val maxLocals = if (EQUALS_NAME == methodName) 2 else 1
                mv.visitMaxs(maxStack, maxLocals)
            }
            mv.visitEnd()
        }
    }

    companion object {
        private const val JMC_PREFIX = "jmc"

        // Method signatures
        private const val HASHCODE_NAME = "hashCode"
        private const val HASHCODE_DESCRIPTOR = "()I"

        private const val TOSTRING_NAME = "toString"
        private const val TOSTRING_DESCRIPTOR = "()Ljava/lang/String;"

        private const val EQUALS_NAME = "equals"
        private const val EQUALS_DESCRIPTOR = "(Ljava/lang/Object;)Z"

        private const val FINALIZE_NAME = "finalize"
        private const val FINALIZE_DESCRIPTOR = "()V"
    }
}
