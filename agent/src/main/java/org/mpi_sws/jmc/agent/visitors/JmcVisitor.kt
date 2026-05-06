package org.mpi_sws.jmc.agent.visitors

import org.mpi_sws.jmc.agent.visitors.JmcFutureVisitor.JmcExecutorsClassVisitor
import org.mpi_sws.jmc.agent.visitors.JmcFutureVisitor.JmcFutureTaskClassVisitor
import org.mpi_sws.jmc.agent.visitors.JmcReadWriteVisitor.ReadWriteClassVisitor
import org.mpi_sws.jmc.agent.visitors.JmcScheduledExecutorVisitor.JmcScheduledExecutorClassVisitor
import org.mpi_sws.jmc.agent.visitors.JmcThreadVisitor.ThreadCallReplacerClassVisitor
import org.mpi_sws.jmc.agent.visitors.JmcThreadVisitor.ThreadClassVisitor
import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

/** The encapsulating visitor that applies all the other visitors in the correct order.  */
object JmcVisitor {
    /**
     * The main method that applies all the visitors in the correct order.
     *
     * @param classFileBuffer the input class file as a byte array
     * @return the transformed class file as a byte array
     */
    @kotlin.jvm.JvmStatic
    fun transform(classFileBuffer: ByteArray): ByteArray {
        val syncCr = ClassReader(classFileBuffer)
        val syncCw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)

        val syncScanData = JmcSyncScanData()
        val syncScanVisitor = JmcSyncScanVisitor(syncCw, syncScanData)
        syncCr.accept(syncScanVisitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)


        val enumCr = ClassReader(classFileBuffer)
        val enumCw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)


        val enumVisitor = JmcIgnoreEnumVisitor(enumCw)
        enumCr.accept(enumVisitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)


        if (enumVisitor.isEnum) {
            return classFileBuffer
        }

        val finalizerCr = ClassReader(classFileBuffer)
        val finalizerCw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)

        val finalizerVisitor = JmcIgnoreFinalizerVisitor(finalizerCw)
        finalizerCr.accept(finalizerVisitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        if (finalizerVisitor.hasFinalizer()) {
            return classFileBuffer
        }


        val cr = ClassReader(classFileBuffer)
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        val cv: ClassVisitor =
            JmcWaitNotifyVisitor(
                JmcStaticMethodVisitor(
                    JmcSyncMethodVisitor(
                        JmcScheduledExecutorClassVisitor(
                            JmcFutureTaskClassVisitor(
                                JmcExecutorsClassVisitor(
                                    JmcAtomicVisitor(
                                        JmcReentrantLockVisitor(
                                            ThreadClassVisitor(
                                                ThreadCallReplacerClassVisitor(
                                                    JmcNativeMethodVisitor(
                                                        ReadWriteClassVisitor(
                                                            cw
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        syncScanData
                    )
                )
            )
        try {
            cr.accept(cv, 0)
        } catch (e: Exception) {
            if (e is JmcUnsupportedFeatureException) {
                throw e
            } else {
                throw RuntimeException(e)
            }
        }
        return cw.toByteArray()
    }
}
