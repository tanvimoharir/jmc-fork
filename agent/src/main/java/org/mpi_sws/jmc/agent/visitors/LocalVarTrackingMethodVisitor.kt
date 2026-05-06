package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * A MethodVisitor that tracks local variable indices and updates the maxLocals value in the
 * visitMaxs method.
 */
open class LocalVarTrackingMethodVisitor(api: Int, mv: MethodVisitor?, access: Int, methodDesc: String) :
    MethodVisitor(api, mv) {
    // Next available local variable index.
    private var nextLocal: Int

    /**
     * Constructor.
     *
     * @param api ASM API version (e.g., Opcodes.ASM9)
     * @param mv The underlying MethodVisitor
     * @param access The method's access flags
     * @param methodDesc The method descriptor (e.g., "(I)V")
     */
    init {
        // For non-static methods, index 0 is reserved for 'this'
        // Indicates whether the method is static.
        val isStatic = (access and Opcodes.ACC_STATIC) != 0
        nextLocal = if (isStatic) 0 else 1

        // Compute the initial nextLocal based on the method's arguments.
        val argTypes = Type.getArgumentTypes(methodDesc)
        for (argType in argTypes) {
            nextLocal += argType.size
        }
    }

    /**
     * Allocates a new local variable of the given type.
     *
     * @param type the ASM Type of the new local variable.
     * @return the index of the newly allocated local variable.
     */
    fun newLocal(type: Type): Int {
        val index = nextLocal
        nextLocal += type.size // Reserve 1 slot for most types or 2 for long/double.
        return index
    }

    fun newLocal(): Int {
        val index = nextLocal
        nextLocal++
        return index
    }

    /** Override visitLocalVariable to capture local variable declarations.  */
    override fun visitLocalVariable(
        name: String, descriptor: String, signature: String, start: Label, end: Label, index: Int
    ) {
        val type = Type.getType(descriptor)
        nextLocal = max(nextLocal, index + type.size)
        super.visitLocalVariable(name, descriptor, signature, start, end, index)
    }

    /** Override visitMaxs to update the maximum number of locals if necessary.  */
    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // Ensure that maxLocals is at least as high as the computed nextLocal.
        super.visitMaxs(maxStack, max(maxLocals, nextLocal))
    }
}
