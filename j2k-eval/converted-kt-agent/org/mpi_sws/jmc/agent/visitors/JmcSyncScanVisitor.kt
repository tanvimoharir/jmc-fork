package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * JmcSyncScanVisitor is a ClassVisitor that scans for synchronized methods, static synchronized
 * methods, and synchronized blocks in a class. It collects this information in a JmcSyncScanData
 * object.
 */
class JmcSyncScanVisitor(cv: ClassVisitor?, private val jmcSyncScanData: JmcSyncScanData) :
    ClassVisitor(Opcodes.ASM9, cv) {
    override fun visitMethod(
        access: Int, name: String, desc: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        if ((access and Opcodes.ACC_SYNCHRONIZED) != 0) {
            if ((access and Opcodes.ACC_STATIC) != 0) {
                jmcSyncScanData.setHasSyncStaticMethods(true)
            } else {
                jmcSyncScanData.setHasSyncMethods(true)
            }
        }
        val mv = super.visitMethod(access, name, desc, signature, exceptions)
        return JmcSyncScanMethodVisitor(mv, jmcSyncScanData)
    }

    private class JmcSyncScanMethodVisitor(mv: MethodVisitor?, private val jmcSyncScanData: JmcSyncScanData) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitInsn(opcode: Int) {
            if (opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT) {
                jmcSyncScanData.setHasSyncBlocks(true)
            }
            super.visitInsn(opcode)
        }
    }
}
