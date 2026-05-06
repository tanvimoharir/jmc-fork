package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.*
import java.util.*

class JmcStaticMethodVisitor(classVisitor: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, classVisitor) {
    private var className: String? = null
    private var staticMethodInfo: StaticMethodInfo? = null

    private var isInterface = false
    private val interfaceFields: MutableList<FieldInfo> = ArrayList()
    private val staticExecutorFields: MutableList<ExecutorFieldInfo> = ArrayList()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        if ((access and Opcodes.ACC_INTERFACE) != 0) {
            isInterface = true
        }

        this.className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitField(
        access: Int, name: String, desc: String, signature: String, value: Any
    ): FieldVisitor {
        if (isInterface) {
            interfaceFields.add(FieldInfo(this.className, name, desc, value))
            return super.visitField(access, name, desc, signature, value)
        }

        // Track static ExecutorService fields for automatic registration
        if (isStaticExecutorServiceField(access, desc)) {
            staticExecutorFields.add(ExecutorFieldInfo(name, desc))
        }

        if (isStaticFinalField(access)) {
            return super.visitField(removeFinal(access), name, desc, signature, value)
        }
        return super.visitField(access, name, desc, signature, value)
    }

    override fun visitMethod(
        access: Int, name: String, desc: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        // Check if the method is static
        if (isInterface && name == "<clinit>") {
            return JmcStaticInitMethodVisitor(
                super.visitMethod(access, name, desc, signature, exceptions), className
            )
        }
        if (name == "<clinit>") {
            this.staticMethodInfo = StaticMethodInfo(access, name, desc, signature, exceptions)
            return super.visitMethod(
                Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE,
                "\$staticInitBody",
                desc,
                signature,
                exceptions
            )
        }
        // Otherwise, just return the default MethodVisitor
        return super.visitMethod(access, name, desc, signature, exceptions)
    }

    //    @Override
    override fun visitEnd() {
        // Handle interfaces with static fields
        if (isInterface && !interfaceFields.isEmpty()) {
            // Create the body helper for interfaces
            createInterfaceStaticInitBody()
            // Create the two public methods
            createStaticInitExplicit()
            createStaticInitImplicit()
            //createClinit();
            // Note: interfaces don't need <clinit> recreation, it's handled by JmcStaticInitMethodVisitor
        } else if (isInterface) {
            // Interface with no static fields, nothing to do
            super.visitEnd()
            return
        }

        // Handle regular classes
        if (this.staticMethodInfo != null) {
            createStaticInitExplicit()
            createStaticInitImplicit()
            createClinit()
        }
        super.visitEnd()
    }

    private fun createInterfaceStaticInitBody() {
        val mv = cv.visitMethod(
            Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE,
            "\$staticInitBody",
            "()V",
            null,
            null
        )
        mv.visitCode()

        // Insert write events for each interface field
        for (field in interfaceFields) {
            field.insertWriteEventCall(mv)
        }

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }


    private fun createStaticInitExplicit() {
        val mv = cv.visitMethod(
            Opcodes.ACC_STATIC or Opcodes.ACC_PUBLIC,
            "\$staticInitExplicit",
            "()V",
            null,
            null
        )
        mv.visitCode()

        // Just call the body helper
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            className,
            "\$staticInitBody",
            "()V",
            isInterface
        )

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    // Replace the createStaticInitImplicit method in JmcStaticMethodVisitor.java:
    private fun createStaticInitImplicit() {
        val mv = cv.visitMethod(
            Opcodes.ACC_STATIC or Opcodes.ACC_PUBLIC,
            "\$staticInitImplicit",
            "()V",
            null,
            null
        )
        mv.visitCode()

        // Call JmcRuntimeUtils.startStaticInitEventWithoutYield()
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "startStaticInitEventWithoutYield",
            "()V",
            false
        )

        // Call the body helper
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            className,
            "\$staticInitBody",
            "()V",
            isInterface
        )

        // Call JmcRuntimeUtils.endStaticInitEventWithoutYield()
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "endStaticInitEventWithoutYield",
            "()V",
            false
        )

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }


    private fun createClinit() {
        val mv = cv.visitMethod(
            staticMethodInfo!!.access,
            staticMethodInfo!!.name,
            staticMethodInfo!!.desc,
            staticMethodInfo!!.signature,
            staticMethodInfo!!.exceptions
        )
        mv.visitCode()

        mv.visitLdcInsn(Type.getObjectType(className))
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "registerStaticInitializedClass",
            "(Ljava/lang/Class;)V",
            false
        )

        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            className,
            "\$staticInitImplicit",
            "()V",
            false
        )

        // Register static ExecutorService fields AFTER initialization completes
        // Use reflection-based registration to avoid triggering field read instrumentation
        for ((name) in staticExecutorFields) {
            // Push class name
            mv.visitLdcInsn(className.replace('/', '.'))

            // Push field name
            mv.visitLdcInsn(name)

            // Call helper method
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "registerStaticExecutorField",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
            )
        }

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }


    private fun isStaticFinalField(access: Int): Boolean {
        return (access and Opcodes.ACC_STATIC) != 0 && (access and Opcodes.ACC_FINAL) != 0
    }

    private fun isStaticExecutorServiceField(access: Int, desc: String): Boolean {
        if ((access and Opcodes.ACC_STATIC) == 0) {
            return false
        }
        // Check if the field type is ExecutorService or ScheduledExecutorService
        return desc == "Ljava/util/concurrent/ExecutorService;" ||
                desc == "Ljava/util/concurrent/ScheduledExecutorService;"
    }

    private fun removeFinal(access: Int): Int {
        // Remove the final modifier from the access flags
        return access and Opcodes.ACC_FINAL.inv()
    }

    private class JmcStaticInitMethodVisitor(methodVisitor: MethodVisitor?, private val className: String?) :
        MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitCode() {
            super.visitCode()

            mv.visitLdcInsn(Type.getObjectType(className))
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "registerStaticInitializedClass",
                "(Ljava/lang/Class;)V",
                false
            )


            // Call $staticInitImplicit() to execute instrumented initialization
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                className,
                "\$staticInitImplicit",
                "()V",
                true
            ) // true because it's an interface method
        }
    }

    @kotlin.jvm.JvmRecord
    private data class StaticMethodInfo(
        val access: Int,
        val name: String,
        val desc: String,
        val signature: String,
        val exceptions: Array<String>
    ) {
        val staticReplacementName: String
            get() = "\$staticInit"

        val staticReplacementAccess: Int
            get() = Opcodes.ACC_STATIC or Opcodes.ACC_PUBLIC
    }

    @kotlin.jvm.JvmRecord
    private data class FieldInfo(val className: String?, val name: String, val desc: String, val value: Any?) {
        fun insertWriteEventCall(mv: MethodVisitor) {
            if (value == null) {
                mv.visitInsn(Opcodes.ACONST_NULL)
            } else {
                mv.visitLdcInsn(value)
                VisitorHelper.addObjectConverter(mv, Type.getType(desc))
            }
            mv.visitLdcInsn(className)
            mv.visitLdcInsn(name)
            mv.visitLdcInsn(desc)
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "writeEvent",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                false
            )
        }
    }

    @kotlin.jvm.JvmRecord
    private data class ExecutorFieldInfo(val name: String, val desc: String) {
        /**
         * Inserts a call to register a static ExecutorService field.
         * Generates bytecode equivalent to:
         * JmcRuntime.registerExecutor(ClassName.fieldName, true);
         */
        fun insertRegisterExecutorCall(mv: MethodVisitor, className: String?) {
            // Load the static field value onto the stack
            mv.visitFieldInsn(
                Opcodes.GETSTATIC,
                className,
                name,
                desc
            )

            // Push true (1) for isStatic parameter
            mv.visitInsn(Opcodes.ICONST_1)

            // Call JmcRuntime.registerExecutor(ExecutorService, boolean)
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntime",
                "registerExecutor",
                "(Ljava/util/concurrent/ExecutorService;Z)V",
                false
            )
        }
    }
}
