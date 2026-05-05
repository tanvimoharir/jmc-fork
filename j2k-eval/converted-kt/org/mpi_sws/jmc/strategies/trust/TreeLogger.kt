package org.mpi_sws.jmc.strategies.trust

class TreeLogger {
    val logger: StringBuilder = StringBuilder()

    var graphId: Long = 1L
        private set

    private var graphCounter = 1L

    private val nextGraphIds: MutableMap<ExplorationStack.Item?, Long> = HashMap()

    var numOfInconsistentGraphs: Long = 0L
        private set

    var numOfBlockedGraphs: Long = 0L
        private set

    private val inConsistentGraphLogger = StringBuilder()

    private val blockedGraphLogger = StringBuilder()

    private val LeafSizeLogger = StringBuilder()

    private var isBranching = false

    fun appendNewBranchs(sizeOfGraph: Int) {
        isBranching = true
        logger.append(graphId).append("(").append(sizeOfGraph).append(")").append(" -> ")
    }

    fun appendNewChild(item: ExplorationStack.Item) {
        graphCounter++
        nextGraphIds[item] = graphCounter
        logger.append(graphCounter).append("(")
            .append(if (item.isBackwardRevisit) "B" else "F")
            .append("), ")
    }

    fun appendLastChild(item: ExplorationStack.Item) {
        graphCounter++
        nextGraphIds[item] = graphCounter
        logger.append(graphCounter).append("(")
            .append(if (item.isBackwardRevisit) "B" else "F")
            .append(")").append(System.lineSeparator())
    }

    fun appendContinueCurrent() {
        graphCounter++
        logger.append(graphCounter).append("(F)").append(System.lineSeparator())
    }

    fun appendNextLine() {
        logger.append(System.lineSeparator())
    }

    fun updateLoggerGraphId(nextItem: ExplorationStack.Item?, sizeOfGraph: Int) {
        if (!isBranching) {
            addLeafSize(sizeOfGraph)
        }
        val nextId = nextGraphIds[nextItem]
        nextGraphIds.remove(nextItem)
        check(!(nextId == null || nextId <= 0)) { "Next graph ID not found for the given item." }
        graphId = nextId
        isBranching = false
    }

    fun updateLoggerGraphIdWithLastGraph(sizeOfGraph: Int) {
        if (!isBranching) {
            addLeafSize(sizeOfGraph)
        }
        graphId = graphCounter
        isBranching = false
    }

    fun addInconsistentGraph() {
        numOfInconsistentGraphs++
        inConsistentGraphLogger.append(graphId).append(", ")
    }

    fun addBlockedGraph() {
        numOfBlockedGraphs++
        blockedGraphLogger.append(graphId).append(", ")
    }

    fun addLeafSize(size: Int) {
        LeafSizeLogger.append(graphId).append("(").append(size).append(")").append(", ")
    }

    fun getInConsistentGraphLogger(): StringBuilder? {
        if (inConsistentGraphLogger.length == 0) {
            return null
        }
        return inConsistentGraphLogger
    }

    fun getBlockedGraphLogger(): StringBuilder? {
        if (blockedGraphLogger.length == 0) {
            return null
        }
        return blockedGraphLogger
    }

    fun getLeafSizeLogger(): StringBuilder? {
        if (LeafSizeLogger.length == 0) {
            return null
        }
        return LeafSizeLogger
    }
}
