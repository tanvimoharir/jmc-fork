package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class JmcIgnoreEnumVisitor(classVisitor: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, classVisitor) {
    var isEnum: Boolean = false
        private set
    private var className: String? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        this.className = name
        this.isEnum = (access and Opcodes.ACC_ENUM) != 0
        //        if (this.isEnum) {
//            System.out.println("JmcIgnoreEnumVisitor ignored the class : " + className);
//        }
        super.visit(version, access, name, signature, superName, interfaces)
    }
}
