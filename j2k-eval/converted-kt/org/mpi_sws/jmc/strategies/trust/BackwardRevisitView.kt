package org.mpi_sws.jmc.strategies.trust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import java.util.function.Predicate

/**
 * Represents a restricted view of the execution graph. Some nodes are removed and some relations
 * are updated.
 */
class BackwardRevisitView(graph: ExecutionGraph, read: ExecutionGraphNode, write: ExecutionGraphNode) {
    private val graph: ExecutionGraph = graph.clone()
    private val removedNodes = HashSet<Event.Key?>()
    var read: ExecutionGraphNode? = null
    var write: ExecutionGraphNode? = null

    // Additional event, maintained here for revisits of a write exclusive with a read exclusive.
    // The write exclusive of the revisited read exclusive is stored here.
    private var addEvent: Event? = null

    /**
     * Creates a new backward revisit view.
     *
     * @param graph The execution graph.
     * @param read  The read event.
     * @param write The write event.
     */
    init {
        if (EventUtils.isLockAcquireRead(read.event)) {
            // The read event is the additional event to be added
            // When revisiting this backward revisit
            // So we also mark it to be removed
            this.addEvent = read.event.clone()
            removedNodes.add(read.key())
        }
        try {
            this.read = this.graph.getEventNode(read.key())
            this.write = this.graph.getEventNode(write.key())
            // When constructing a backward revisit of a write to a
            // lock acquire read, the write cannot be ever removed from the graph.
            // So we mark it as such.
            // Leads to a cyclic exploration otherwise.
            if (EventUtils.isLockAcquireRead(read.event)) {
                EventUtils.markLockWriteFinal(this.write.event)
            }
        } catch (ignored: NoSuchEventException) {
            throw HaltCheckerException.Companion.error("The read or write event is not found.")
        }
    }

    /**
     * Just marks the node as removed, does not update the graph
     */
    fun removeNode(key: Event.Key?) {
        removedNodes.add(key)
    }

    val isMaximalExtension: Boolean
        /**
         * Checks if the restricted view is a maximal extension
         *
         *
         * Meta: Breaks the separation of concerns. Is part of the core logic of the Trust algorithm
         */
        get() {
            LOGGER.debug("Checking if the restricted view is a maximal extension")
            val nodesToCheck = HashSet(this.removedNodes)
            nodesToCheck.add(read!!.key())
            try {
                for (key in nodesToCheck) {
                    val node = graph.getEventNode(key)
                    LOGGER.debug("Checking if the node is a maximal extension: " + node.event)
                    val nodeTOIndex = graph.getTOIndex(node!!)
                    if (nodeTOIndex == -1) {
                        throw HaltExecutionException.Companion.error("The event does not have a TO index.")
                    }
                    if (node.event.type == Event.Type.NOOP
                        || node.event.type == Event.Type.ASSUME
                    ) {
                        continue
                    }

                    if (EventUtils.isFinalLockWrite(node.event)) {
                        return false
                    }

                    val previous =
                        Predicate { k: Event.Key ->
                            try {
                                val kNode = graph.getEventNode(k)
                                // Based on the definition of previous set in the TruSt paper,
                                // we need to check if the event TO-prefix of the node, or it is
                                // in the porf-prefix of the given write event.
                                return@Predicate graph.getTOIndex(k) <= nodeTOIndex
                                        || kNode!!.happensBefore(write!!)
                            } catch (e: NoSuchEventException) {
                                return@Predicate false
                            }
                        }
                    // 1. Check first if key is a write event that has a dangling read in the
                    // restricted graph.
                    if (EventUtils.isWrite(node.event)) {
                        val reads = node.getSuccessors(Relation.ReadsFrom)
                        for (readKey in reads!!) {
                            // Check if in previous
                            if (previous.test(readKey)) {
                                LOGGER.debug("The read event is in the previous set")
                                return false
                            }
                        }
                    }
                    // 2. Check if the write event associated with the node is CO maximal in previous
                    var nodeWrite = node
                    if (EventUtils.isRead(node.event)) {
                        val writes = node.getPredecessors(Relation.ReadsFrom)
                        if (writes!!.size != 1) {
                            throw HaltExecutionException.Companion.error(
                                "The read event does not have a valid rf event."
                            )
                        }
                        nodeWrite = graph.getEventNode(writes.iterator().next()!!)
                        LOGGER.debug(
                            "Checking if the write event is CO maximal: " + nodeWrite.event
                        )
                    }
                    if (!previous.test(nodeWrite!!.key())) {
                        LOGGER.debug("The write event is not in the previous set")
                        return false
                    }

                    // Now node is a write event for sure
                    // We only need to check if the CO after events for the same location are in
                    // previous
                    val writes: List<ExecutionGraphNode?>?
                    // We need to check if the nodeWrite is init or not
                    //                if (nodeWrite.getEvent().getType() == Event.Type.INIT) {
                    //                    // TODO :: This is not an efficient implementation. We need to
                    // optimize this
                    //                    writes = graph.getAllWrites();
                    //                    for (ExecutionGraphNode writeNode : writes) {
                    //                        if (previous.test(writeNode.key())) {
                    //                            LOGGER.debug("The write event is in the previous
                    // set");
                    //                            return false;
                    //                        }
                    //                    }
                    //                } else {
                    var location = nodeWrite.event.location
                    if (location == null) {
                        // This is because nodeWrite is the init event
                        // We get the location from the read event then
                        location = node.event.location
                    }
                    writes = graph.getWrites(location)
                    val index = writes.indexOf(nodeWrite)
                    if (index < writes.size - 1) {
                        for (i in index + 1..<writes.size) {
                            if (previous.test(writes[i].key())) {
                                LOGGER.debug("The write event is in the previous set")
                                return false
                            }
                        }
                    }
                    //                }
                }
            } catch (e: NoSuchEventException) {
                throw HaltExecutionException.Companion.error("The event is not found.")
            }
            LOGGER.debug("The restricted view is a maximal extension")
            return true
        }

    val restrictedGraph: ExecutionGraph
        /**
         * Gets the restricted graph.
         *
         * @return The restricted graph
         */
        get() {
            val restrictedGraph = graph
            // So far the coherency of this write is not tracked
            // TODO: Maybe this should be done in the constructor?
            // Update the reads-from relation
            restrictedGraph.changeReadsFrom(read!!, write!!)
            // Remove the nodes
            restrictedGraph.restrictBySet(removedNodes)
            restrictedGraph.recomputeVectorClocks()
            restrictedGraph.checkDanglingEdges()
            restrictedGraph.checkConsistency()
            return restrictedGraph
        }

    fun additionalEvent(): Event? {
        return addEvent
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            BackwardRevisitView::class.java
        )
    }
}
