package org.mpi_sws.jmc.integrations.junit5.descriptors

import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor

/**
 * A JUnit 5 engine descriptor for the JMC engine.
 *
 *
 * This descriptor represents the JMC engine in the JUnit 5 test framework, allowing for the
 * execution of JMC checks as part of the test lifecycle.
 */
class JmcEngineDescriptor(uniqueId: UniqueId?) :
    EngineDescriptor(uniqueId, ENGINE_DISPLAY_NAME) {
    companion object {
        const val ENGINE_DISPLAY_NAME: String = "JMC (JUnit platform)"
    }
}
