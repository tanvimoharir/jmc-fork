package org.mpi_sws.jmc.agent.visitors

import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException
import org.objectweb.asm.*
import java.util.*

/**
 * Adds instrumentation to change Future calls to JmcFuture calls.
 */
class JmcFutureVisitor {
    /**
     * Creates a ClassVisitor that will instrument classes to replace Executors with JmcExecutors.
     */
    class JmcExecutorsClassVisitor(classVisitor: ClassVisitor?) :
        ClassVisitor(Opcodes.ASM9, classVisitor) {
        private var isExtendingThreadpool = false

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
            // TODO : Record all classes extending ExecutorService, Executors, Future, or any interesting thread pool related class
            var superName = superName
            if ("java/util/concurrent/ThreadPoolExecutor" == superName) {
                isExtendingThreadpool = true
                superName = "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService"
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
            var newDescriptor: String? = descriptor
            if (newDescriptor != null) {
                if (newDescriptor.contains(JmcExecutorsMethodVisitor.THREADPOOL_EXECUTOR_DESC)) {
                    newDescriptor = newDescriptor.replace(
                        JmcExecutorsMethodVisitor.THREADPOOL_EXECUTOR_DESC,
                        JmcExecutorsMethodVisitor.JMC_EXECUTOR_SERVICE_PATH_DESC
                    )
                }
                if (newDescriptor.contains("L" + JmcExecutorsMethodVisitor.EXECUTORS_DELEGATED_WRAPPER + ";") ||
                    newDescriptor.contains("L" + JmcExecutorsMethodVisitor.EXECUTORS_FINALIZED_WRAPPER + ";")
                ) {
                    newDescriptor = newDescriptor.replace(
                        "L" + JmcExecutorsMethodVisitor.EXECUTORS_DELEGATED_WRAPPER + ";",
                        JmcExecutorsMethodVisitor.JMC_EXECUTOR_SERVICE_PATH_DESC
                    )
                    newDescriptor = newDescriptor.replace(
                        "L" + JmcExecutorsMethodVisitor.EXECUTORS_FINALIZED_WRAPPER + ";",
                        JmcExecutorsMethodVisitor.JMC_EXECUTOR_SERVICE_PATH_DESC
                    )
                }
            }
            return super.visitField(access, name, newDescriptor, signature, value)
        }

        override fun visitMethod(
            access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
        ): MethodVisitor {
            return if (isExtendingThreadpool && "<init>" == name) {
                JmcThreadPoolInitMethodVisitor(
                    super.visitMethod(
                        access,
                        name,
                        "Lorg/mpi_sws/jmc/api/util/concurrent/JmcExecutorService",
                        signature,
                        exceptions
                    )
                )
            } else {
                JmcExecutorsMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions)
                )
            }
        }
    }

    /**
     * A MethodVisitor that replaces calls to Executors with JmcExecutors.
     *
     *
     * It supports the following methods:
     *
     *
     *  * newSingleThreadExecutor()
     *  * newFixedThreadPool(int)
     *
     */
    class JmcExecutorsMethodVisitor(methodVisitor: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            if (owner == EXECUTORS_PATH) {
                if (!SUPPORTED_METHODS.containsKey(name)
                    || !SUPPORTED_METHODS[name]!!.contains(descriptor)
                ) {
                    throw JmcUnsupportedFeatureException(
                        "Unsupported method: $name with descriptor: $descriptor"
                    )
                }
                // Replace the call to Executors with a call to JmcExecutors
                super.visitMethodInsn(
                    opcode,
                    JMC_EXECUTORS_PATH,
                    name,
                    replaceDescriptor(descriptor),
                    isInterface
                )
                return
            }
            //intercepting threadpool calls via invokespecial
            //This is needed for the Executors methods which return a ThreadPoolExecutor object
            if (opcode == Opcodes.INVOKESPECIAL && owner == THREADPOOL_EXECUTOR_PATH) {
                super.visitMethodInsn(
                    opcode,
                    JMC_EXECUTOR_SERVICE_PATH,
                    name,
                    replaceDescriptor(descriptor),
                    isInterface
                )
                return
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }


        override fun visitTypeInsn(opcode: Int, type: String) {
            if (THREADPOOL_EXECUTOR_PATH == type) {
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH)
            }
            if (EXECUTORS_DELEGATED_WRAPPER == type) {
                //map wrappers to JmcExecutorService
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH)
            }
            if (EXECUTORS_FINALIZED_WRAPPER == type) {
                //map wrappers to JmcExecutorService
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH)
            }
            //default
            super.visitTypeInsn(opcode, type)
        }


        override fun visitLocalVariable(
            name: String, desc: String, signature: String, start: Label, end: Label, index: Int
        ) {
            var newDescriptor: String? = desc
            if (newDescriptor != null) {
                if (newDescriptor.contains(THREADPOOL_EXECUTOR_DESC)) {
                    newDescriptor = newDescriptor.replace(THREADPOOL_EXECUTOR_DESC, JMC_EXECUTOR_SERVICE_PATH_DESC)
                }
                if (newDescriptor.contains(EXECUTORS_DESC)) {
                    newDescriptor = newDescriptor.replace(EXECUTORS_DESC, JMC_EXECUTORS_PATH_DESC)
                }
                if (newDescriptor.contains("L" + EXECUTORS_DELEGATED_WRAPPER + ";") ||
                    newDescriptor.contains("L" + EXECUTORS_FINALIZED_WRAPPER + ";")
                ) {
                    newDescriptor =
                        newDescriptor.replace("L" + EXECUTORS_DELEGATED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_PATH_DESC)
                    newDescriptor =
                        newDescriptor.replace("L" + EXECUTORS_FINALIZED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_PATH_DESC)
                }
            }
            super.visitLocalVariable(name, newDescriptor, signature, start, end, index)
        }

        override fun visitInvokeDynamicInsn(
            name: String, descriptor: String, bsm: Handle?, vararg bsmArgs: Any
        ) {
            var isValidType = false
            if (descriptor.contains(EXECUTORS_PATH)
                || descriptor.contains(EXECUTOR_SERVICE_PATH)
                || descriptor.contains(EXECUTORS_DELEGATED_WRAPPER)
                || descriptor.contains(EXECUTORS_FINALIZED_WRAPPER)
                || descriptor.contains(THREADPOOL_EXECUTOR_PATH)
                || (bsm != null && bsm.owner.contains(EXECUTORS_PATH))
                || (bsm != null && bsm.owner.contains(EXECUTOR_SERVICE_PATH))
                || (bsm != null && bsm.owner.contains(EXECUTORS_DELEGATED_WRAPPER))
                || (bsm != null && bsm.owner.contains(EXECUTORS_FINALIZED_WRAPPER))
                || (bsm != null && bsm.owner.contains(THREADPOOL_EXECUTOR_PATH))

            ) {
                isValidType = true
            }
            if (isValidType) {
                //Replace descriptor
                val newDescriptor = replaceDescriptor(descriptor)
                var newBsm = bsm
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
                        val classname: String = t.getInternalName()
                        newBsmArgs[i] = Type.getType(replaceType(classname))
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


        private fun replaceDescriptor(desc: String?): String? {
            if (desc == null) {
                return null
            }
            var newDesc: String = desc
            if (newDesc.contains(EXECUTORS_DESC)) {
                newDesc = newDesc.replace(EXECUTORS_DESC, JMC_EXECUTORS_PATH_DESC)
            }
            if (newDesc.contains(THREADPOOL_EXECUTOR_DESC)) {
                newDesc = newDesc.replace(THREADPOOL_EXECUTOR_DESC, JMC_EXECUTOR_SERVICE_PATH_DESC)
            }
            if (newDesc.contains(EXECUTORS_DELEGATED_WRAPPER) || newDesc.contains(EXECUTORS_FINALIZED_WRAPPER)) {
                newDesc = newDesc.replace("L" + EXECUTORS_DELEGATED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_DESC_WRAPPER)
                newDesc = newDesc.replace("L" + EXECUTORS_FINALIZED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_DESC_WRAPPER)
            }
            return newDesc
        }

        private fun replaceType(type: String?): String? {
            if (type == null) {
                return null
            }
            if (type == EXECUTORS_PATH) {
                return JMC_EXECUTORS_PATH
                //            } else if (type.equals(EXECUTOR_SERVICE_PATH)) {
//                return JMC_EXECUTOR_SERVICE_PATH;
            } else if (type == THREADPOOL_EXECUTOR_PATH) {
                return JMC_EXECUTOR_SERVICE_PATH
            } else if ((type == EXECUTORS_DELEGATED_WRAPPER) || (type == EXECUTORS_FINALIZED_WRAPPER)) {
                return JMC_EXECUTOR_SERVICE_PATH
            }
            return type
        }

        companion object {
            // Set of valid method names and descriptors that can be replaced
            private const val EXECUTORS_PATH = "java/util/concurrent/Executors"
            private const val JMC_EXECUTORS_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcExecutors"
            private const val EXECUTORS_DESC = "L" + EXECUTORS_PATH + ";"
            private const val JMC_EXECUTORS_PATH_DESC = "L" + JMC_EXECUTORS_PATH + ";"

            protected const val EXECUTOR_SERVICE_PATH: String = "java/util/concurrent/ExecutorService"
            private const val JMC_EXECUTOR_SERVICE_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService"
            protected const val EXECUTOR_SERVICE_DESC: String = "L" + EXECUTOR_SERVICE_PATH + ";"
            const val JMC_EXECUTOR_SERVICE_PATH_DESC: String = "L" + JMC_EXECUTOR_SERVICE_PATH + ";"

            private const val THREADPOOL_EXECUTOR_PATH = "java/util/concurrent/ThreadPoolExecutor"

            //private static final String JMC_THREADPOOL_EXECUTOR_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcThreadPoolExecutor";
            const val THREADPOOL_EXECUTOR_DESC: String = "L" + THREADPOOL_EXECUTOR_PATH + ";"

            //private static final String JMC_THREADPOOL_EXECUTOR_DESC = "L" + JMC_THREADPOOL_EXECUTOR_PATH + ";";
            const val EXECUTORS_DELEGATED_WRAPPER: String = "java/util/concurrent/Executors\$DelegatedExecutorService"
            const val EXECUTORS_FINALIZED_WRAPPER: String =
                "java/util/concurrent/Executors\$FinalizableDelegatedExecutorService"
            private const val JMC_EXECUTOR_SERVICE_DESC_WRAPPER = JMC_EXECUTOR_SERVICE_PATH_DESC

            private const val FUTURE_PATH = "java/util/concurrent/Future"
            private const val FUTURE_DESC = "L" + FUTURE_PATH + ";"

            private val SUPPORTED_METHODS = HashMap<String, Set<String>>()

            init {
                // TODO : Check if the following is needed
                SUPPORTED_METHODS.put(
                    "newSingleThreadExecutor",
                    setOf<String>(
                        "()Ljava/util/concurrent/ExecutorService;",
                        "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
                    )
                )

                SUPPORTED_METHODS.put(
                    "newFixedThreadPool",
                    setOf<String>(
                        "(I)Ljava/util/concurrent/ExecutorService;",
                        "(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
                    )
                )
            }
        }
    }

    class JmcThreadPoolInitMethodVisitor(methodVisitor: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            if (opcode == Opcodes.INVOKESPECIAL && owner == "java/util/concurrent/ThreadPoolExecutor"
                && "<init>" == name
            ) {
                super.visitMethodInsn(
                    opcode,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService",
                    name,
                    descriptor,
                    isInterface
                )
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }

    /**
     * Creates a ClassVisitor that will instrument classes to replace FutureTask with JmcFuture.
     */
    class JmcFutureTaskClassVisitor(classVisitor: ClassVisitor?) :
        ClassVisitor(Opcodes.ASM9, classVisitor) {
        override fun visitMethod(
            access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
        ): MethodVisitor {
            return JmcFutureTaskMethodVisitor(
                super.visitMethod(access, name, descriptor, signature, exceptions)
            )
        }

        override fun visitField(
            access: Int, name: String, descriptor: String, signature: String, value: Any
        ): FieldVisitor {
            // Replace the field with JmcFuture
            return super.visitField(access, name, descriptor, signature, value)
        }
    }

    /**
     * A MethodVisitor that replaces calls to FutureTask with JmcFuture.
     *
     *
     * It supports the following methods:
     *
     *
     *  * run()
     *  * get()
     *  * cancel(boolean)
     *
     */
    class JmcFutureTaskMethodVisitor(methodVisitor: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            if (owner == "java/util/concurrent/FutureTask") {
                if (name == "<init>") {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    return
                }
                if (name == "get" || name == "cancel" || name == "run") {
                    super.visitTypeInsn(Opcodes.CHECKCAST, "org/mpi_sws/jmc/api/util/concurrent/JmcFuture")

                    // Replace the call to FutureTask with a call to JmcFuture
                    super.visitMethodInsn(
                        opcode,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcFuture",
                        name,
                        descriptor,
                        isInterface
                    )
                    return
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                return
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        } // TODO : Check if a visitInvokeDynamicInsn override is needed here
    }

    /**
     * Creates a ClassVisitor that will instrument classes to replace CompletableFuture with
     * JmcCompletableFuture.
     */
    class JmcCompletableFutureVisitor(cv: ClassVisitor?) :
        ClassVisitor(Opcodes.ASM9, cv) {
        override fun visitField(
            access: Int, name: String, descriptor: String, signature: String, value: Any
        ): FieldVisitor {
            // Replace field descriptor if it's ReentrantLock
            var descriptor = descriptor
            if (descriptor == "Ljava/util/concurrent/CompletableFuture;") {
                descriptor = "Lorg/mpi_sws/jmc/api/util/concurrent/CompletableFuture;"
            }
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(
            access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
        ): MethodVisitor {
            // First let the parent handle the method visitor creation
            val mv =
                super.visitMethod(
                    access, name, replaceDescriptor(descriptor), signature, exceptions
                )
            return CompletableFutureReplacementMethodVisitor(mv)
        }

        private class CompletableFutureReplacementMethodVisitor(mv: MethodVisitor?) :
            MethodVisitor(Opcodes.ASM9, mv) {
            override fun visitTypeInsn(opcode: Int, type: String) {
                // Replace NEW CompletableFuture with JmcCompletableFuture
                if (opcode == Opcodes.NEW
                    && type == "java/util/concurrent/CompletableFuture"
                ) {
                    super.visitTypeInsn(
                        opcode, "org/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture"
                    )
                } else {
                    super.visitTypeInsn(opcode, type)
                }
            }

            override fun visitMethodInsn(
                opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
            ) {
                // Replace CompletableFuture calls with JmcCompletableFuture calls
                var descriptor = descriptor
                descriptor = replaceDescriptor(descriptor)
                if (owner == "java/util/concurrent/CompletableFuture") {
                    super.visitMethodInsn(
                        opcode,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture",
                        name,
                        descriptor,
                        isInterface
                    )
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }
            }

            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                // Replace field references
                if (descriptor == "Ljava/util/concurrent/CompletableFuture;") {
                    super.visitFieldInsn(
                        opcode,
                        owner,
                        name,
                        "Lorg/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture;"
                    )
                } else {
                    super.visitFieldInsn(opcode, owner, name, descriptor)
                }
            }

            override fun visitLocalVariable(
                name: String,
                descriptor: String,
                signature: String,
                start: Label,
                end: Label,
                index: Int
            ) {
                if (descriptor == COMPLETABLE_FUTURE_LOCK_DESC) {
                    super.visitLocalVariable(
                        name, JMC_COMPLETABLE_FUTURE_LOCK_DESC, signature, start, end, index
                    )
                } else {
                    super.visitLocalVariable(name, descriptor, signature, start, end, index)
                }
            }
        }

        companion object {
            private const val COMPLETABLE_FUTURE_LOCK_DESC = "Ljava/util/concurrent/CompletableFuture;"
            private const val JMC_COMPLETABLE_FUTURE_LOCK_DESC =
                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture;"

            private fun replaceDescriptor(desc: String): String {
                if (desc.contains(COMPLETABLE_FUTURE_LOCK_DESC)) {
                    return desc.replace(COMPLETABLE_FUTURE_LOCK_DESC, JMC_COMPLETABLE_FUTURE_LOCK_DESC)
                }
                return desc
            }
        }
    }
}
