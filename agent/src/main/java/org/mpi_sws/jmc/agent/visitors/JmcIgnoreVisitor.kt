package org.mpi_sws.jmc.agent.visitors

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * JmcIgnoreVisitor is a ClassVisitor that checks for the presence of the JmcIgnoreInstrumentation
 * annotation on a class. If the annotation is present, it indicates that the class should not be
 * instrumented by JMC.
 */
class JmcIgnoreVisitor(classVisitor: ClassVisitor?) :
    ClassVisitor(Opcodes.ASM9, classVisitor) {
    private var hasIgnoreAnnotation = false

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        if (descriptor == IGNORE_ANNOTATION_DESCRIPTOR) {
            hasIgnoreAnnotation = true
        }
        return super.visitAnnotation(descriptor, visible)
    }

    /**
     * Checks if the class has the JmcIgnoreInstrumentation annotation.
     *
     * @return true if the class has the annotation, false otherwise
     */
    fun hasIgnoreAnnotation(): Boolean {
        return hasIgnoreAnnotation
    }

    companion object {
        private const val IGNORE_ANNOTATION_DESCRIPTOR = "Lorg/mpi_sws/jmc/annotations/JmcIgnoreInstrumentation;"
    }
}
