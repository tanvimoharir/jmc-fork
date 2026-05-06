package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*

/**
 * Helper class for inserting instrumentation to generate RuntimeEvents for field read and write
 * operations.
 */
object VisitorHelper {
    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field read operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    fun insertRead(
        mv: MethodVisitor, isStatic: Boolean, owner: String?, name: String?, descriptor: String?
    ) {
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL)
        } else {
            mv.visitInsn(Opcodes.DUP) // Duplicate the 'this' reference on the stack
        }
        mv.visitLdcInsn(owner)
        mv.visitLdcInsn(name)
        mv.visitLdcInsn(descriptor)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "readEventWithoutYield",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false
        )
    }

    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field write operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    fun insertWrite(
        mv: MethodVisitor, isStatic: Boolean, owner: String?, name: String?, descriptor: String
    ) {
        val fieldType = Type.getType(descriptor)
        val isLongOrDouble = fieldType.size == 2
        if (isLongOrDouble && !isStatic) {
            // We need to duplicate the 'this' reference and the value
            mv.visitInsn(Opcodes.DUP2_X1) // Duplicate the value and the 'this' reference
        } else if (!isLongOrDouble && !isStatic) {
            // We need to duplicate the 'this' reference and value, but it is short
            mv.visitInsn(Opcodes.DUP2)
        } else if (isLongOrDouble) {
            // For static fields, we just duplicate the value, but it is long or double
            mv.visitInsn(Opcodes.DUP2) // Duplicate the value
        } else {
            // For static fields, we just duplicate the value, but it is short
            mv.visitInsn(Opcodes.DUP) // Duplicate the value
        }
        // Convert the value to an Object if necessary
        addObjectConverter(mv, fieldType)
        if (!isStatic && isLongOrDouble) {
            mv.visitInsn(Opcodes.SWAP)
            mv.visitInsn(Opcodes.DUP_X1)
            mv.visitInsn(Opcodes.SWAP)
        } else if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitInsn(Opcodes.SWAP)
        }

        mv.visitLdcInsn(owner)
        mv.visitLdcInsn(name)
        mv.visitLdcInsn(descriptor)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "writeEventWithoutYield",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false
        )
        if (isLongOrDouble && !isStatic) {
            mv.visitInsn(Opcodes.DUP_X2)
            mv.visitInsn(Opcodes.POP)
        }
    }

    /**
     * Inserts a yield call to the JmcRuntime.
     *
     * @param mv The MethodVisitor to which the yield call will be added.
     */
    fun insertYield(mv: MethodVisitor) {
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntime",
            "yield",
            "()Ljava/lang/Object;",
            false
        )
        mv.visitInsn(Opcodes.POP)
    }


    /**
     * Inserts instrumentation for a static field read AFTER the GETSTATIC instruction.
     * At this point, the field value is on top of the stack and must remain there.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    fun insertStaticReadAfter(
        mv: MethodVisitor, owner: String?, name: String?, descriptor: String?
    ) {
        // Stack before: [value from GETSTATIC]
        // Stack after: [value from GETSTATIC] (unchanged)

        // The readEventWithoutYield call doesn't need the field value,
        // just the metadata, so we don't touch the stack value

        mv.visitInsn(Opcodes.ACONST_NULL) // null object reference for static field
        mv.visitLdcInsn(owner)
        mv.visitLdcInsn(name)
        mv.visitLdcInsn(descriptor)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "readEventWithoutYield",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false
        )
        // Stack: [value] - original value remains untouched
    }

    /**
     * Inserts instrumentation BEFORE a static field write to prepare for post-write event.
     * This duplicates the value so it can be used after PUTSTATIC consumes it.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param descriptor The descriptor of the field.
     */
    fun insertStaticWriteBefore(
        mv: MethodVisitor, descriptor: String
    ) {
        // Stack before: [value to write]
        // Stack after: [value to write, value copy]

        val fieldType = Type.getType(descriptor)
        val isLongOrDouble = fieldType.size == 2

        if (isLongOrDouble) {
            mv.visitInsn(Opcodes.DUP2) // Duplicate long/double value
        } else {
            mv.visitInsn(Opcodes.DUP) // Duplicate regular value
        }
        // Stack: [value, value] - one will be consumed by PUTSTATIC, one for event
    }

    /**
     * Inserts instrumentation for a static field write AFTER the PUTSTATIC instruction.
     * Assumes the value was duplicated before PUTSTATIC via insertStaticWriteBefore.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    fun insertStaticWriteAfter(
        mv: MethodVisitor, owner: String?, name: String?, descriptor: String
    ) {
        // Stack before: [value copy] (the duplicate from insertStaticWriteBefore)
        // Stack after: [] (clean)

        val fieldType = Type.getType(descriptor)

        // Convert the value to an Object if necessary
        addObjectConverter(mv, fieldType)

        // Now we have: [Object value]
        mv.visitInsn(Opcodes.ACONST_NULL) // null object reference for static field
        mv.visitInsn(Opcodes.SWAP) // Stack: [null, Object value]

        mv.visitLdcInsn(owner)
        mv.visitLdcInsn(name)
        mv.visitLdcInsn(descriptor)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
            "writeEventWithoutYield",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false
        )
        // Stack: [] - clean
    }

    /**
     * Adds instructions to convert a primitive type on the stack to its corresponding wrapper
     * object.
     *
     * @param mv The MethodVisitor to which the conversion instructions will be added.
     * @param fieldType The Type of the field to be converted.
     */
    fun addObjectConverter(mv: MethodVisitor, fieldType: Type) {
        when (fieldType.sort) {
            Type.OBJECT -> return
            Type.BOOLEAN -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Boolean",
                    "valueOf",
                    "(Z)Ljava/lang/Boolean;",
                    false
                )
                return
            }

            Type.CHAR -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Character",
                    "valueOf",
                    "(C)Ljava/lang/Character;",
                    false
                )
                return
            }

            Type.BYTE -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Byte",
                    "valueOf",
                    "(B)Ljava/lang/Byte;",
                    false
                )
                return
            }

            Type.SHORT -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Short",
                    "valueOf",
                    "(S)Ljava/lang/Short;",
                    false
                )
                return
            }

            Type.INT -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Integer",
                    "valueOf",
                    "(I)Ljava/lang/Integer;",
                    false
                )
                return
            }

            Type.FLOAT -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Float",
                    "valueOf",
                    "(F)Ljava/lang/Float;",
                    false
                )
                return
            }

            Type.LONG -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Long",
                    "valueOf",
                    "(J)Ljava/lang/Long;",
                    false
                )
                return
            }

            Type.DOUBLE -> {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Double",
                    "valueOf",
                    "(D)Ljava/lang/Double;",
                    false
                )
                return
            }
        }
    }

    /**
     * Checks if the given opcode is an instantiation opcode.
     *
     * @param opcode The opcode to check.
     * @return true if the opcode is an instantiation opcode, false otherwise.
     */
    fun isInstantiation(opcode: Int): Boolean {
        return opcode == Opcodes.NEW || opcode == Opcodes.ANEWARRAY || opcode == Opcodes.MULTIANEWARRAY
    }

    /**
     * Adds a return instruction to the method visitor based on the method's return type.
     *
     * @param mv The MethodVisitor to which the return instruction will be added.
     * @param descriptor The method descriptor, which contains the return type.
     */
    fun addReturnInst(mv: MethodVisitor, descriptor: String) {
        // Find the return type of the method and add the corresponding return instruction
        val returnType: String = descriptor.substring(descriptor.lastIndexOf(')') + 1)
        when (returnType) {
            "V" -> mv.visitInsn(Opcodes.RETURN) // return for void methods
            "D" -> mv.visitInsn(Opcodes.DRETURN) // return for double
            "F" -> mv.visitInsn(Opcodes.FRETURN) // return for float
            "J" -> mv.visitInsn(Opcodes.LRETURN) // return for long
            "I", "B", "C", "S", "Z" -> mv.visitInsn(Opcodes.IRETURN) // return for int, byte, char, short, boolean
            else -> mv.visitInsn(Opcodes.ARETURN) // return for object references
        }
    }

    private val SUPPORTED_CONCURRENT_FEATURES: Set<String> = setOf<String>(
        "java.util.concurrent.atomic.AtomicBoolean.<init>",
        "java.util.concurrent.atomic.AtomicBoolean.get",
        "java.util.concurrent.atomic.AtomicBoolean.set",
        "java.util.concurrent.atomic.AtomicBoolean.compareAndSet",
        "java.util.concurrent.atomic.AtomicInteger.<init>",
        "java.util.concurrent.atomic.AtomicInteger.get",
        "java.util.concurrent.atomic.AtomicInteger.set",
        "java.util.concurrent.atomic.AtomicInteger.compareAndSet",
        "java.util.concurrent.atomic.AtomicInteger.getAndIncrement",
        "java.util.concurrent.atomic.AtomicInteger.getAndSet",
        "java.util.concurrent.atomic.AtomicInteger.addAndGet",
        "java.util.concurrent.atomic.AtomicReference.<init>",
        "java.util.concurrent.atomic.AtomicReference.get",
        "java.util.concurrent.atomic.AtomicReference.set",
        "java.util.concurrent.atomic.AtomicReference.compareAndSet",
        "java.util.concurrent.atomic.AtomicReference.getAndSet",
        "java.util.concurrent.atomic.AtomicReferenceArray.<init>",
        "java.util.concurrent.atomic.AtomicReferenceArray.get",
        "java.util.concurrent.atomic.AtomicReferenceArray.set",
        "java.util.concurrent.atomic.AtomicReferenceArray.getAndSet",
        "java.util.concurrent.CompletableFuture.<init>",
        "java.util.concurrent.ExecutorService.<init>",
        "java.util.concurrent.ExecutorService.shutdownNow",
        "java.util.concurrent.ExecutorService.shutdown",
        "java.util.concurrent.ExecutorService.awaitTermination",
        "java.util.concurrent.ExecutorService.isTerminated",
        "java.util.concurrent.ExecutorService.isShutdown",
        "java.util.concurrent.RunnableFuture.<init>",
        "java.util.concurrent.RunnableFuture.cancel",
        "java.util.concurrent.Executors.newSingleThreadExecutor",
        "java.util.concurrent.Executors.newFixedThreadPool",
        "java.util.concurrent.locks.LockSupport.park",
        "java.util.concurrent.locks.LockSupport.unpark",
        "java.util.concurrent.locks.ReentrantLock.lock",
        "java.util.concurrent.locks.ReentrantLock.unlock",
        "java.lang.Thread.run",
        "java.lang.Thread.join",
        "java.util.concurrent.ThreadFactory.newThread",
        "java.util.concurrent.ThreadPoolExecutor.<init>"
    )

    fun isConcurrentFeatureSupported(feature: String): Boolean {
        return SUPPORTED_CONCURRENT_FEATURES.contains(feature)
    }

    fun supportedFeatures(): Set<String> {
        return SUPPORTED_CONCURRENT_FEATURES
    }

    /** The MethodInfo class is used to store information about a method.  */
    class MethodInfo(
        /** Access flags of the method.  */
        private val access: Int,
        /** Name of the method.  */
        val name: String,
        /** Descriptor of the method.  */
        val descriptor: String,
        /** Signature of the method.  */
        val signature: String,
        /** Exceptions of the method.  */
        val exceptions: Array<String>
    ) {
        private val annotations: MutableList<AnnotationInfo> =
            ArrayList()

        private val parameterNames: MutableList<String> = ArrayList()
        private val parameterAccesses: MutableList<Int> = ArrayList()

        val isStatic: Boolean
            /** Returns true if the method is synchronized.  */
            get() = (access and Opcodes.ACC_STATIC) != 0

        val nonSyncAccess: Int
            /** Changes the access flags of the method to be non-synchronized.  */
            get() = access and Opcodes.ACC_SYNCHRONIZED.inv()

        val syncName: String
            /** Changes the name of the method to have a suffix of "$synchronized".  */
            get() = "$name\$synchronized"

        val unsyncName: String
            /** Changes the name of the method to have a suffix of "$unsynchronized".  */
            get() = "$name\$unsynchronized"

        fun addAnnotation(annotation: AnnotationInfo) {
            annotations.add(annotation)
        }

        fun getAnnotations(): List<AnnotationInfo> {
            return annotations
        }

        fun addParameter(name: String, access: Int) {
            parameterNames.add(name)
            parameterAccesses.add(access)
        }

        fun getParameterNames(): List<String> {
            return parameterNames
        }

        fun getParameterAccesses(): List<Int> {
            return parameterAccesses
        }
    }

    class AnnotationInfo(val descriptor: String, val visibility: Boolean) {
        private val values: MutableMap<String, AnnotationValue> = HashMap()

        fun addValue(name: String, value: AnnotationValue) {
            values.put(name, value)
        }

        fun getValues(): Map<String, AnnotationValue> {
            return values
        }

        override fun toString(): String {
            return "$descriptor $values"
        }
    }

    interface AnnotationValue {
        fun type(): Type

        enum class Type {
            Primitive,
            Enum,
            Array,
            Nested
        }
    }

    class PrimitiveValue(val value: Any) : AnnotationValue {
        override fun type(): AnnotationValue.Type {
            return AnnotationValue.Type.Primitive
        }
    }

    class EnumValue(val descriptor: String, val value: String) : AnnotationValue {
        override fun type(): AnnotationValue.Type {
            return AnnotationValue.Type.Enum
        }
    }

    class ArrayValue : AnnotationValue {
        val values: MutableList<AnnotationValue> = ArrayList()

        override fun type(): AnnotationValue.Type {
            return AnnotationValue.Type.Array
        }

        fun addValue(value: AnnotationValue) {
            values.add(value)
        }
    }

    class NestedAnnotationValue(val nested: AnnotationInfo) : AnnotationValue {
        override fun type(): AnnotationValue.Type {
            return AnnotationValue.Type.Nested
        }
    }

    class JmcAnnotationRecordVisitor(var annotationInfo: AnnotationInfo) : AnnotationVisitor(Opcodes.ASM9) {
        override fun visit(name: String, value: Any) {
            annotationInfo.addValue(name, PrimitiveValue(value))
        }

        override fun visitEnum(name: String, descriptor: String, value: String) {
            annotationInfo.addValue(name, EnumValue(descriptor, value))
        }

        override fun visitArray(name: String): AnnotationVisitor {
            val arr = ArrayValue()
            val av: AnnotationVisitor =
                object : AnnotationVisitor(Opcodes.ASM8) {
                    override fun visit(n: String, v: Any) {
                        arr.values.add(PrimitiveValue(v))
                    }
                }
            annotationInfo.addValue(name, arr)
            return av
        }

        override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor {
            val nested =
                AnnotationInfo(descriptor, true)
            annotationInfo.addValue(name, NestedAnnotationValue(nested))
            return JmcAnnotationRecordVisitor(nested)
        }
    }
}
