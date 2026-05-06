package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visitor that instruments wait() and notify() calls to use JmcObject methods.
 *
 *
 * o.wait() -> JmcObject.objectWait(o)
 *
 *
 * o.wait(timeout) -> JmcObject.objectWait(o, timeout)
 *
 *
 * o.notify() -> JmcObject.objectNotify(o)
 *
 *
 * o.notifyAll() -> JmcObject.objectNotifyAll(o) check thread visitor for reference
 */
class JmcWaitNotifyVisitor(cv: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, cv) {
    override fun visitMethod(
        access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>
    ): MethodVisitor {
        return JmcNotifyWaitMethodVisitor(
            super.visitMethod(access, name, descriptor, signature, exceptions)
        )
    }

    class JmcNotifyWaitMethodVisitor(mv: MethodVisitor?) :
        MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitMethodInsn(
            opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
        ) {
            // Only instrument wait/notify/notifyAll if they're being called on java.lang.Object
            // TODO reevaluate the second check on Object
            if (opcode != Opcodes.INVOKEVIRTUAL || owner != "java/lang/Object") {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                return
            }
            when (name) {
                "wait" -> if (descriptor == "()V") {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/api/JmcObject",
                        "objectWait",
                        "(Ljava/lang/Object;)V",
                        false
                    )
                    return
                } else if (descriptor == "(J)V") {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/api/JmcObject",
                        "objectWait",
                        "(Ljava/lang/Object;J)V",
                        false
                    )
                    return
                }

                "notify" -> {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/api/JmcObject",
                        "objectNotify",
                        "(Ljava/lang/Object;)V",
                        false
                    )
                    return
                }

                "notifyAll" -> {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/api/JmcObject",
                        "objectNotifyAll",
                        "(Ljava/lang/Object;)V",
                        false
                    )
                    return
                }

                else -> super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }
}
