package org.mpi_sws.jmc.agent.visitors

import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.*

/**
 * Visitor for instrumenting ScheduledExecutorService, ScheduledThreadPoolExecutor,
 * and ScheduledFuture to use JMC's controlled execution versions.
 */
class JmcScheduledExecutorVisitor {
    /**
     * ClassVisitor that replaces ScheduledThreadPoolExecutor, ScheduledExecutorService,
     * and ScheduledFuture with JMC equivalents.
     */
    class JmcScheduledExecutorClassVisitor(classVisitor: ClassVisitor?) :
        ClassVisitor(Opcodes.ASM9, classVisitor) {
        private var isExtendingScheduledThreadPool = false

        override fun visit(
            version: Int, access: Int, name: String, signature: String,
            superName: String, interfaces: Array<String>
        ) {
            // Replace superclass if extending ScheduledThreadPoolExecutor
            var superName = superName
            if ("java/util/concurrent/ScheduledThreadPoolExecutor" == superName) {
                isExtendingScheduledThreadPool = true
                superName = "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService"
            }
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(
            access: Int, name: String, descriptor: String,
            signature: String, value: Any
        ): FieldVisitor {
            var newDescriptor: String? = descriptor
            if (newDescriptor != null) {
                if (newDescriptor.startsWith(JmcScheduledExecutorMethodVisitor.SCHEDULED_THREADPOOL_EXECUTOR_PATH)) {
                    newDescriptor = newDescriptor.replace(
                        JmcScheduledExecutorMethodVisitor.SCHEDULED_THREADPOOL_EXECUTOR_PATH,
                        JmcScheduledExecutorMethodVisitor.JMC_SCHEDULED_EXECUTOR_SERVICE_PATH
                    )
                }
            }
            return super.visitField(access, name, newDescriptor, signature, value)
        }

        override fun visitMethod(
            access: Int, name: String, descriptor: String,
            signature: String, exceptions: Array<String>
        ): MethodVisitor {
            // Handle constructor for classes extending ScheduledThreadPoolExecutor
            if (isExtendingScheduledThreadPool && "<init>" == name) {
                return JmcScheduledThreadPoolInitMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions)
                )
            }

            return JmcScheduledExecutorMethodVisitor(
                super.visitMethod(access, name, descriptor, signature, exceptions)
            )
        }
    }

    /**
     * MethodVisitor that replaces calls to Executors.newScheduledThreadPool,
     * direct instantiation of ScheduledThreadPoolExecutor, and ScheduledFuture method calls.
     */
    class JmcScheduledExecutorMethodVisitor(methodVisitor: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String,
            descriptor: String, isInterface: Boolean
        ) {
            // Replace Executors.newScheduledThreadPool() calls
            if (owner == EXECUTORS_PATH) {
                if (SUPPORTED_METHODS.containsKey(name)) {
                    if (!SUPPORTED_METHODS[name]!!.contains(descriptor)) {
                        throw JmcUnsupportedFeatureException(
                            "Unsupported ScheduledExecutor method: " + name +
                                    " with descriptor: " + descriptor
                        )
                    }
                    super.visitMethodInsn(
                        opcode,
                        JMC_EXECUTORS_PATH,
                        name,
                        descriptor,
                        isInterface
                    )
                    return
                }
            }

            // Replace ScheduledThreadPoolExecutor constructor calls
            if (opcode == Opcodes.INVOKESPECIAL &&
                owner == SCHEDULED_THREADPOOL_EXECUTOR_PATH
            ) {
                super.visitMethodInsn(
                    opcode,
                    JMC_SCHEDULED_EXECUTOR_SERVICE_PATH,
                    name,
                    descriptor,
                    isInterface
                )
                return
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            // Replace NEW ScheduledThreadPoolExecutor
            if (SCHEDULED_THREADPOOL_EXECUTOR_PATH == type) {
                super.visitTypeInsn(opcode, JMC_SCHEDULED_EXECUTOR_SERVICE_PATH)
                return
            }
            super.visitTypeInsn(opcode, type)
        }


        companion object {
            // Path constants
            private const val EXECUTORS_PATH = "java/util/concurrent/Executors"
            private const val JMC_EXECUTORS_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcExecutors"

            private const val SCHEDULED_EXECUTOR_SERVICE_PATH = "java/util/concurrent/ScheduledExecutorService"
            const val JMC_SCHEDULED_EXECUTOR_SERVICE_PATH: String =
                "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService"

            const val SCHEDULED_THREADPOOL_EXECUTOR_PATH: String = "java/util/concurrent/ScheduledThreadPoolExecutor"

            private const val SCHEDULED_FUTURE_PATH = "java/util/concurrent/ScheduledFuture"
            private const val JMC_SCHEDULED_FUTURE_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledFuture"

            // Descriptor constants
            private const val SCHEDULED_EXECUTOR_SERVICE_DESC = "L" + SCHEDULED_EXECUTOR_SERVICE_PATH + ";"
            private const val JMC_SCHEDULED_EXECUTOR_SERVICE_DESC = "L" + JMC_SCHEDULED_EXECUTOR_SERVICE_PATH + ";"

            private const val SCHEDULED_THREADPOOL_EXECUTOR_DESC = "L" + SCHEDULED_THREADPOOL_EXECUTOR_PATH + ";"

            private const val SCHEDULED_FUTURE_DESC = "L" + SCHEDULED_FUTURE_PATH + ";"
            private const val JMC_SCHEDULED_FUTURE_DESC = "L" + JMC_SCHEDULED_FUTURE_PATH + ";"

            // Supported Executors methods
            private val SUPPORTED_METHODS = HashMap<String, Set<String>>()

            init {
                SUPPORTED_METHODS.put(
                    "newScheduledThreadPool",
                    setOf<String>(
                        "(I)Ljava/util/concurrent/ScheduledExecutorService;",
                        "(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ScheduledExecutorService;"
                    )
                )

                SUPPORTED_METHODS.put(
                    "newSingleThreadScheduledExecutor",
                    setOf<String>(
                        "()Ljava/util/concurrent/ScheduledExecutorService;",
                        "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ScheduledExecutorService;"
                    )
                )
            }

            /**
             * Replace type descriptors for scheduled executor types.
             */
            fun replaceDescriptor(desc: String?): String? {
                if (desc == null) {
                    return null
                }
                var newDesc: String = desc

                // Replace ScheduledExecutorService
                if (newDesc.contains(SCHEDULED_EXECUTOR_SERVICE_DESC)) {
                    newDesc = newDesc.replace(
                        SCHEDULED_EXECUTOR_SERVICE_DESC,
                        JMC_SCHEDULED_EXECUTOR_SERVICE_DESC
                    )
                }

                // Replace ScheduledThreadPoolExecutor
                if (newDesc.contains(SCHEDULED_THREADPOOL_EXECUTOR_DESC)) {
                    newDesc = newDesc.replace(
                        SCHEDULED_THREADPOOL_EXECUTOR_DESC,
                        JMC_SCHEDULED_EXECUTOR_SERVICE_DESC
                    )
                }

                // Replace ScheduledFuture
                if (newDesc.contains(SCHEDULED_FUTURE_DESC)) {
                    newDesc = newDesc.replace(
                        SCHEDULED_FUTURE_DESC,
                        JMC_SCHEDULED_FUTURE_DESC
                    )
                }

                return newDesc
            }
        }
    }

    /**
     * MethodVisitor for handling constructors of classes that extend
     * ScheduledThreadPoolExecutor.
     */
    class JmcScheduledThreadPoolInitMethodVisitor(methodVisitor: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String,
            descriptor: String, isInterface: Boolean
        ) {
            // Replace super() calls to ScheduledThreadPoolExecutor
            if (opcode == Opcodes.INVOKESPECIAL &&
                owner == "java/util/concurrent/ScheduledThreadPoolExecutor" &&
                "<init>" == name
            ) {
                super.visitMethodInsn(
                    opcode,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService",
                    name,
                    descriptor,
                    isInterface
                )
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }
}
