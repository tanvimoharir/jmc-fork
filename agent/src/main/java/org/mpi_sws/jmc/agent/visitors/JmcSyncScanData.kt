package org.mpi_sws.jmc.agent.visitors

/**
 * JmcSyncScanData is a data class that holds information about synchronization constructs in a
 * class. It tracks whether the class has synchronized methods, synchronized static methods, and
 * synchronized blocks.
 */
class JmcSyncScanData {
    private var hasSyncMethods = false
    private var hasSyncStaticMethods = false
    private var hasSyncBlocks = false

    /**
     * Returns true if the class has synchronized methods.
     *
     * @return true if the class has synchronized methods, false otherwise
     */
    fun hasSyncMethods(): Boolean {
        return hasSyncMethods
    }

    /**
     * Returns true if the class has synchronized static methods.
     *
     * @return true if the class has synchronized static methods, false otherwise
     */
    fun hasSyncStaticMethods(): Boolean {
        return hasSyncStaticMethods
    }

    /**
     * Returns true if the class has synchronized blocks.
     *
     * @return true if the class has synchronized blocks, false otherwise
     */
    fun hasSyncBlocks(): Boolean {
        return hasSyncBlocks
    }

    /**
     * Sets whether the class has synchronized methods.
     *
     * @param hasSyncMethods true if the class has synchronized methods, false otherwise
     */
    fun setHasSyncMethods(hasSyncMethods: Boolean) {
        this.hasSyncMethods = hasSyncMethods
    }

    /**
     * Sets whether the class has synchronized static methods.
     *
     * @param hasSyncStaticMethods true if the class has synchronized static methods, false
     * otherwise
     */
    fun setHasSyncStaticMethods(hasSyncStaticMethods: Boolean) {
        this.hasSyncStaticMethods = hasSyncStaticMethods
    }

    /**
     * Sets whether the class has synchronized blocks.
     *
     * @param hasSyncBlocks true if the class has synchronized blocks, false otherwise
     */
    fun setHasSyncBlocks(hasSyncBlocks: Boolean) {
        this.hasSyncBlocks = hasSyncBlocks
    }
}
