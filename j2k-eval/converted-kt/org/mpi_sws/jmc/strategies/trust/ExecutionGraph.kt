package org.mpi_sws.jmc.strategies.trust

import com.google.gson.JsonObject
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.strategies.trust.ExecutionGraph.TopologicalSorter.GraphCycleException
import org.mpi_sws.jmc.util.LamportVectorClock
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Represents an execution graph.
 *
 *
 * Contains the exploration and all the relations defined according to the Trust algorithm. For
 * now this class implements the sequential consistency model. Which, in theory, could be extended
 * to other models.
 *
 *
 * Some terminology to understand the code
 *
 *
 *  * TO: Total order of events observed in this execution graph, in the order they were added
 *  * PO: Program order. A union of reads from partial order and the total order of events per
 * task
 *  * RF: Reads from relation between reads and writes
 *  * CO: A coherency order between writes
 *
 */
class ExecutionGraph {
    // Events observed in this execution graph grouped by task. This is the PO order
    private val taskEvents: MutableList<MutableList<ExecutionGraphNode>>

    // Tracking coherency order between writes to the same location. This is the CO order
    private val coherencyOrder: HashMap<Int?, MutableList<ExecutionGraphNode>>

    // All events in the execution graph. This is the TO order
    private var allEvents: MutableList<ExecutionGraphNode>

    private val blockedLocks: HashMap<Int?, MutableList<Long>>

    var isConsistent: Boolean = true

    /**
     * Initializes a new execution graph.
     */
    constructor() {
        this.allEvents = ArrayList()
        this.coherencyOrder = HashMap()
        this.taskEvents = ArrayList()
        this.blockedLocks = HashMap()
    }

    /* Copy constructor */
    private constructor(graph: ExecutionGraph) {
        this.taskEvents = ArrayList()
        for (taskEvent in graph.taskEvents) {
            val newTaskEvent: MutableList<ExecutionGraphNode> = ArrayList()
            for (node in taskEvent) {
                if (EventUtils.isBlockingLabel(node.event)) {
                    // We ignore blocking labels when revisiting
                    // And also remove the edge pointing to the blocking label
                    newTaskEvent[newTaskEvent.size - 1]
                        .removeEdgeTo(node.key(), Relation.ProgramOrder)
                    continue
                }
                newTaskEvent.add(node.clone())
            }
            taskEvents.add(newTaskEvent)
        }
        this.allEvents = ArrayList()
        for (node in graph.allEvents) {
            if (node.event.isInit) {
                // Need to clone the init event, so far it has not been added to the task events
                allEvents.add(node.clone())
                continue
            }
            val nodeKey = node.key()
            allEvents.add(
                taskEvents[nodeKey.taskId.toInt()][nodeKey.timestamp]
            )
        }
        this.coherencyOrder = HashMap()
        for (location in graph.coherencyOrder.keys) {
            val writes: List<ExecutionGraphNode> = graph.coherencyOrder[location]!!
            val newWrites: MutableList<ExecutionGraphNode> = ArrayList()
            for (write in writes) {
                if (write.event.isInit) {
                    newWrites.add(allEvents[0])
                    continue
                }
                val nodeKey = write.key()
                newWrites.add(
                    taskEvents[nodeKey.taskId.toInt()][nodeKey.timestamp]
                )
            }
            coherencyOrder[location] = newWrites
        }

        // When we clone, we forget about this.
        // It's only used for the forward revisits and
        // in the backward revisits, we ignore it.
        // Start fresh
        this.blockedLocks = HashMap()
    }

    val unblockedTasks: ArrayList<Int?>
        /**
         * Returns the list of task identifiers in the execution graph where a new event can be added.
         *
         * @return The list of task identifiers in the execution graph.
         */
        get() {
            val unblockedTasks = ArrayList<Int?>()
            for (i in taskEvents.indices) {
                if (taskEvents[i].isEmpty()) {
                    unblockedTasks.add(i)
                } else {
                    val lastNode = taskEvents[i][taskEvents[i].size - 1]
                    if (!EventUtils.isBlockingLabel(lastNode.event)) {
                        unblockedTasks.add(i)
                    }
                }
            }
            return unblockedTasks
        }

    /**
     * Returns the index of the given node in the TO order.
     *
     * @param node The node to get the index of.
     * @return The index of the given node in the TO order (-1 if not found).
     */
    fun getTOIndex(node: ExecutionGraphNode): Int {
        // A slight optimization to get start from the max vector clock value. The assumption is
        // that is at least after this value in the TO.
        if (node.event.isInit) {
            return 0
        }
        // TODO: When we use node.getVectorClock().max() as a hint to start searching from, sometimes it
        // TODO: it leads to wrong results. Investigate why.
        for (i in allEvents.indices) {
            if (allEvents[i] === node) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns the index of the given key in the TO order.
     *
     * @param key The key to get the index of.
     * @return The index of the given key in the TO order (-1 if not found).
     */
    fun getTOIndex(key: Event.Key): Int {
        for (i in allEvents.indices) {
            if (allEvents[i].key() == key) {
                return i
            }
        }
        return -1
    }

    /**
     * Creates a clone of the execution graph.
     *
     * @return A clone of the execution graph.
     */
    override fun clone(): ExecutionGraph {
        return ExecutionGraph(this)
    }

    /**
     * Returns true if the execution graph has an event node with the given key.
     *
     * @param key The key of the event to check.
     * @return True if the execution graph has an event node with the given key.
     */
    fun hasEventNode(key: Event.Key): Boolean {
        if (key.taskId == null || key.timestamp == null) {
            // Init event
            return true
        }
        val taskId = key.taskId.toInt()
        val timestamp = key.timestamp
        if (taskId < 0 || taskId >= taskEvents.size) {
            return false
        }
        return timestamp >= 0 && timestamp < taskEvents[taskId].size
    }

    /**
     * Returns the event node with the given key.
     *
     * @param key The key of the event to get.
     * @return The event node with the given key.
     * @throws NoSuchEventException If the event with the given key is not found.
     */
    @Throws(NoSuchEventException::class)
    fun getEventNode(key: Event.Key): ExecutionGraphNode {
        if (key.taskId == null || key.timestamp == null) {
            // Init event
            return allEvents[0]
        }
        val taskId = key.taskId.toInt()
        val timestamp = key.timestamp
        if (taskId >= taskEvents.size || timestamp!! >= taskEvents[taskId].size) {
            throw NoSuchEventException(key)
        }
        return taskEvents[taskId][timestamp!!]
    }

    private fun unsafeGetEventNode(key: Event.Key): ExecutionGraphNode {
        if (key.taskId == null || key.timestamp == null) {
            // Init event
            return allEvents[0]
        }
        val taskId = key.taskId.toInt()
        val timestamp = key.timestamp
        return taskEvents[taskId][timestamp!!]
    }

    /**
     * Returns true if the execution graph contains the event with the given key.
     *
     * @param key The key of the event to check.
     * @return True if the execution graph contains the event with the given key.
     */
    fun contains(key: Event.Key): Boolean {
        if (key.taskId == null || key.timestamp == null) {
            // Init event
            return true
        }
        val taskID = key.taskId.toInt()
        val timestamp = key.timestamp
        return taskID < taskEvents.size && timestamp!! < taskEvents[taskID].size
    }

    /**
     * Adds an event to the execution graph.
     *
     * @param event The event to add.
     * @return The node representing the added event.
     */
    fun addEvent(event: Event): ExecutionGraphNode {
        if (event.isInit) {
            // Add the initial event to the TO order
            val initialNode =
                ExecutionGraphNode(event, LamportVectorClock(0))
            allEvents.add(initialNode)
            LOGGER.debug("Added initial event.")
            return initialNode
        }

        // Track the event in the PO order (fetch the latest vector clock first and use that to
        // create a node)
        val task = Math.toIntExact(event.taskId)
        if (task >= taskEvents.size) {
            // Add empty task events to accommodate for the new task
            for (i in taskEvents.size..task) {
                taskEvents.add(ArrayList())
            }
        }

        var vectorClock = LamportVectorClock(taskEvents.size)
        // The last event in the PO order (initial event by default)
        var lastNodePO = allEvents[0]
        if (!taskEvents[task].isEmpty()) {
            lastNodePO = taskEvents[task][taskEvents[task].size - 1]
            vectorClock = lastNodePO.vectorClock
        }
        if (EventUtils.isBlockingLabel(lastNodePO.event)) {
            throw HaltCheckerException.Companion.error("A blocking label is followed by an event.")
        } else if (EventUtils.isThreadFinish(lastNodePO.event)) {
            throw HaltCheckerException.Companion.error("A thread finish label is followed by an event.")
        }
        val node = ExecutionGraphNode(event, vectorClock)

        // Set timestamp to task event size
        event.timestamp = taskEvents[task].size
        event.toStamp = allEvents.size
        LOGGER.debug("Adding event: {}", event.key().toString())
        taskEvents[task].add(node)
        // Add the event to the PO order
        lastNodePO.addEdge(node, Relation.ProgramOrder)
        // Track the event in the TO order
        allEvents.add(node)

        // Track event location in the coherency order but not the event itself
        // Meaning don't add the event in the coherency order
        val location = event.location
        if (location != null && !coherencyOrder.containsKey(location)) {
            // If the location is not already tracked, add the initial event
            val newWrites: MutableList<ExecutionGraphNode> = ArrayList()
            newWrites.add(allEvents[0])
            coherencyOrder[location] = newWrites
        }

        return node
    }

    /**
     * Tracks thread join events in the execution graph. Adds a thread join edge from the last event
     * of the joined task to the thread join event.
     *
     * @param node The node representing the thread join event.
     */
    fun trackThreadJoins(node: ExecutionGraphNode) {
        if (!EventUtils.isThreadJoin(node.event)) {
            // Silent return if the event is not a thread join
            return
        }

        // Adding a thread edge from the last event of the joinedTask to this event
        // Affects porf and happens before
        val joinedTask = EventUtils.getJoinedTask(node.event)
        val lastEventJoinedTask =
            taskEvents[joinedTask][taskEvents[joinedTask].size - 1]
        lastEventJoinedTask.addEdge(node, Relation.ThreadJoin)
    }

    /**
     * Tracks the thread starts in the execution graph as a total order
     *
     *
     * Internally, it uses a special location in the coherency Order to maintain the total order.
     * Additionally, the relation is part of _porf_ and is reflected in the happens before.
     *
     * @param node The node representing the thread start event.
     */
    fun trackThreadCreates(node: ExecutionGraphNode) {
        if (!EventUtils.isThreadStart(node.event)) {
            // Silent return if the event is not a thread start
            return
        }

        // Tracking thread starts in the coherency order with a special static location object.
        val threadStarts: List<ExecutionGraphNode> = coherencyOrder[LocationStore.Companion.ThreadLocation]!!
        val lastThreadStart = threadStarts[threadStarts.size - 1]
        lastThreadStart.addEdge(node, Relation.ThreadCreation)
        coherencyOrder[LocationStore.Companion.ThreadLocation]!!.add(node)
    }

    fun trackThreadStarts(node: ExecutionGraphNode) {
        if (!EventUtils.isThreadStart(node.event)) {
            // Silent return if the event is not a thread start
            return
        }

        // Adding a thread edge from the last event of the started task to this event
        // Affects porf and happens before
        val startedBy = EventUtils.getStartedBy(node.event)
            ?: // No any ThreadStart event can be started by null. It is a bug in the code.
            throw RuntimeException( // TODO : Replace with better exception
                "The event is not started by any task."
            )

        val startedByTask = Math.toIntExact(startedBy)
        val lastEventStartedBy =
            taskEvents[startedByTask][taskEvents[startedByTask].size - 1]
        lastEventStartedBy.addEdge(node, Relation.ThreadStart)
    }

    /**
     * Adds a blocking label to the execution graph.
     *
     * @param taskId The task ID to add the blocking label for.
     */
    fun addBlockingLabel(taskId: Long?) {
        val eventType = Event.Type.BLOCK
        val event = Event(taskId, null, eventType)
        val task = Math.toIntExact(event.taskId)
        if (task >= taskEvents.size) {
            // Add empty task events to accommodate for the new task
            for (i in taskEvents.size..task) {
                taskEvents.add(ArrayList())
            }
        }
        var vectorClock = LamportVectorClock(taskEvents.size)
        // The last event in the PO order (initial event by default)
        var lastNodePO = allEvents[0]
        if (!taskEvents[task].isEmpty()) {
            lastNodePO = taskEvents[task][taskEvents[task].size - 1]
            vectorClock = lastNodePO.vectorClock
        }
        val node = ExecutionGraphNode(event, vectorClock)
        lastNodePO.addEdge(node, Relation.ProgramOrder)

        // Set timestamp to task event size
        event.timestamp = taskEvents[task].size
        taskEvents[task].add(node)
    }

    /**
     * Checks if the task with the given ID is blocked.
     *
     * @param taskId The task ID to check.
     * @return True if the task with the given ID is blocked.
     */
    fun isTaskBlocked(taskId: Long?): Boolean {
        if (taskId == null || taskId >= taskEvents.size) {
            return false
        }
        val curTaskEvents: List<ExecutionGraphNode> = taskEvents[Math.toIntExact(taskId)]
        if (curTaskEvents.isEmpty()) {
            return false
        }
        val lastNode = curTaskEvents[curTaskEvents.size - 1]
        return EventUtils.isBlockingLabel(lastNode.event)
    }

    /**
     * Unblocks the task with the given ID.
     *
     * @param taskId The task ID to block.
     * @throws HaltCheckerException If the task ID is invalid.
     */
    @Throws(HaltCheckerException::class)
    fun unBlockTask(taskId: Long) {
        if (taskId == null || taskId > taskEvents.size) {
            throw HaltCheckerException.Companion.error("Invalid Task ID.")
        }
        val curTaskEvents = taskEvents[Math.toIntExact(taskId)]
        if (curTaskEvents.isEmpty()) {
            throw HaltCheckerException.Companion.error("The task is not blocked.")
        }
        val blockNode = curTaskEvents[curTaskEvents.size - 1]
        if (blockNode.event.type != Event.Type.BLOCK) {
            throw HaltCheckerException.Companion.error("The task cannot be unblocked.")
        }
        curTaskEvents.removeAt(curTaskEvents.size - 1)
        val last = curTaskEvents[curTaskEvents.size - 1]
        last.removeEdge(blockNode, Relation.ProgramOrder)
    }

    /**
     * Returns the last write event to the given location.
     *
     * @param location The location to get the last write event for.
     * @return The last write event to the given location.
     */
    fun getCoMax(location: Int?): ExecutionGraphNode {
        val writes: List<ExecutionGraphNode>? = coherencyOrder[location]
        if (writes == null || writes.isEmpty()) {
            // No writes to the location, therefore return the initial event
            return allEvents[0]
        }
        return writes[writes.size - 1]
    }

    /**
     * Returns the nodes that are not _porf_-before the given node except the last node in the
     * returned list. Assumes that the given nodes are ordered in reverse CO order.
     *
     * @param node  The node to split before.
     * @param nodes The nodes to split.
     * @return The nodes that are not _porf_-before the given node.
     */
    private fun splitNodesBefore(
        node: ExecutionGraphNode, nodes: List<ExecutionGraphNode>
    ): MutableList<ExecutionGraphNode> {
        val result: MutableList<ExecutionGraphNode> = ArrayList()
        for (i in nodes.indices.reversed()) {
            val iterNode = nodes[i]
            if (!iterNode.happensBefore(node)) {
                result.add(iterNode)
            } else {
                // Add the one last write that is _porf_-before the read
                result.add(iterNode)
                break
            }
        }
        return result
    }

    /**
     * Returns the alternative writes (in reverse CO order) to the given read event.
     *
     *
     * All writes that are not _porf_-before the given read. (Tied to Sequential consistency
     * model) ecluding the CO max write.
     *
     * @param read The read event node.
     * @return The alternative writes to the given read event.
     */
    fun getAlternativeWrites(read: ExecutionGraphNode): List<ExecutionGraphNode> {
        val location = read.event.location
        val allWrites: List<ExecutionGraphNode> =
            coherencyOrder[location]!!.subList(0, coherencyOrder[location]!!.size - 1)
        return splitNodesBefore(read, allWrites)
    }

    /**
     * Returns the alternative reads to the given write event.
     *
     *
     * All reads that are not _porf_-before the given write. Specifically looking for lock
     * acquire reads. In the search, the concurrent writes do not include lock acquire exclusive
     * writes.
     *
     * @param write The write event node.
     * @return The alternative reads to the given write event.
     */
    fun getAlternativeLockReads(write: ExecutionGraphNode): List<ExecutionGraphNode> {
        val location = write.event.location
        val allWrites: MutableList<ExecutionGraphNode?> = ArrayList()
        for (i in coherencyOrder[location]!!.indices.reversed()) {
            val otherWrite = coherencyOrder[location]!![i]
            if (EventUtils.isFinalLockWrite(otherWrite.event)) {
                break
            }
            allWrites.add(otherWrite)
        }
        Collections.reverse(allWrites)
        val alternativeWrites: List<ExecutionGraphNode> = splitNodesBefore(write, allWrites)

        val lockReads: MutableList<ExecutionGraphNode> = ArrayList()
        for (altWrite in alternativeWrites) {
            val readKeys = altWrite.getSuccessors(Relation.ReadsFrom)
            for (readKey in readKeys!!) {
                try {
                    val readNode = getEventNode(readKey)
                    if (EventUtils.isLockAcquireRead(readNode.event)
                        && readNode.event.location == location
                        && !readNode.happensBefore(write)
                    ) {
                        lockReads.add(readNode)
                    }
                } catch (e: NoSuchEventException) {
                    throw HaltExecutionException.Companion.error("The read event is not found.")
                }
            }
        }
        return lockReads
    }

    /**
     * Returns the potential alternative writes to the given lock read.
     *
     *
     * Writes that other lock reads are reading from.
     *
     * @param read The write event node.
     * @return The potential writes to the given read event.
     */
    fun getAlternativeLockWrites(read: ExecutionGraphNode): List<ExecutionGraphNode> {
        val location = read.event.location
        val allWrites: List<ExecutionGraphNode> = coherencyOrder[location]!!
        val alternativeWrites: List<ExecutionGraphNode> = splitNodesBefore(read, allWrites)

        val filteredAlternativeWrites: MutableList<ExecutionGraphNode> = ArrayList()
        // fold alternativeWrites to exclude lock acquire writes which have a matching lock release
        // write
        val taskIDs: MutableSet<Long?> = TreeSet { x: Long?, y: Long? ->
            java.lang.Long.compare(
                x!!, y!!
            )
        }
        for (i in alternativeWrites.indices) {
            val alternativeWrite = alternativeWrites[i]
            if (EventUtils.isFinalLockWrite(alternativeWrite.event)) {
                // Should not consider any more alternate writes after
                // this since this has already been used to revisit an existing
                // lock acquire read.
                break
            }
            if (EventUtils.isLockReleaseWrite(alternativeWrite.event)) {
                taskIDs.add(alternativeWrite.event.taskId)
                filteredAlternativeWrites.add(alternativeWrite)
            } else if (EventUtils.isLockAcquireWrite(alternativeWrite.event)) {
                val taskId = alternativeWrite.event.taskId
                if (taskIDs.contains(taskId)) {
                    taskIDs.remove(taskId)
                    continue
                }
                filteredAlternativeWrites.add(alternativeWrite)
            } else {
                filteredAlternativeWrites.add(alternativeWrite)
            }
        }
        // By now, it contains also the CO max write
        // It might be the lock release write
        // Or it might be a lock acquire write
        // In either case, we remove it since it's not an alternative lock write
        if (filteredAlternativeWrites.isEmpty()) {
            return filteredAlternativeWrites
        }
        return filteredAlternativeWrites.subList(1, filteredAlternativeWrites.size)
    }

    /**
     * Returns the potential alternative reads to the given write event.
     *
     *
     * All reads that are not _porf_-before the given write.
     *
     * @param write The write event node.
     * @return The potential reads to the given write event.
     */
    fun getPotentialReads(write: ExecutionGraphNode): List<ExecutionGraphNode> {
        val otherWrites: List<ExecutionGraphNode> = coherencyOrder[write.event.location]!!

        // Drop the recently added write ( We fixed this by updating the CO as the last step of the
        // write handling proc)
        val nonPorfWrites = splitNodesBefore(write, otherWrites)

        if (nonPorfWrites.isEmpty()) {
            // No writes after the given write event
            // Should not happen. There should at least be the init.
            throw HaltExecutionException.Companion.error("No writes after the given write event.")
        }

        // Following the sequential consistency model, we only consider non-exclusive writes
        nonPorfWrites.removeIf { w: ExecutionGraphNode -> EventUtils.isExclusiveWrite(w.event) }

        var reads: MutableList<ExecutionGraphNode> = ArrayList()
        for (alternativeWrite in nonPorfWrites) {
            val readKeys = alternativeWrite.getSuccessors(Relation.ReadsFrom)
            for (readKey in readKeys!!) {
                try {
                    val readNode = getEventNode(readKey)
                    if (readNode.event.location == write.event.location) {
                        reads.add(readNode)
                    }
                } catch (e: NoSuchEventException) {
                    throw HaltExecutionException.Companion.error("The read event is not found.")
                }
            }
        }
        reads =
            reads.stream() // Filter out reads that are _porf_-before the write
                .filter { r: ExecutionGraphNode -> !r.happensBefore(write) }
                .toList()
        return reads
    }

    /**
     * Constructs a backward revisit view of the ExecutionGraph.
     *
     * @param write The write event
     * @param read  The read event that the write needs to backward revisit
     * @return The backward revisit view of the ExecutionGraph
     */
    fun revisitView(write: ExecutionGraphNode, read: ExecutionGraphNode): BackwardRevisitView {
        // Construct a restricted view of the graph
        val restrictedView = BackwardRevisitView(this, read, write)
        val readToIndex = getTOIndex(read)
        if (readToIndex == -1) {
            throw HaltCheckerException.Companion.error("The read event is not found in the TO order.")
        }

        // The following loop computes the elements of the deleted set.
        for (i in readToIndex + 1..<allEvents.size - 1) {
            val node = allEvents[i]
            if (!node.happensBefore(write)) {
                restrictedView.removeNode(node.key())
            }
        }
        return restrictedView
    }

    /**
     * Returns the writes to the given location.
     *
     * @param location The location to get the writes for.
     * @return The writes to the given location.
     */
    fun getWrites(location: Int?): List<ExecutionGraphNode> {
        return coherencyOrder[location]!!
    }

    val allWrites: List<ExecutionGraphNode>
        /**
         * Returns all the writes in the execution graph.
         *
         * @return All the writes in the execution graph.
         */
        get() {
            val allWrites: MutableList<ExecutionGraphNode> =
                ArrayList()
            for (location in coherencyOrder.keys) {
                for (write in coherencyOrder[location]!!) {
                    if (write.event.isWrite) {
                        allWrites.add(write)
                    }
                }
            }
            return allWrites
        }

    /**
     * Resets the coherency order between the given write events. The later write is added just
     * before the earlier write.
     *
     *
     * Invalidates the total order of events in the graph. The concern of fixing the total order
     * is passed to the calling function.
     *
     * @param write1 The first write event.
     * @param write2 The second write event.
     */
    fun swapCoherency(write1: ExecutionGraphNode, write2: ExecutionGraphNode) {
        // Update the coherency order
        val location = write1.event.location
        if (write2.event.location != location) {
            throw HaltCheckerException.Companion.error("The write events are not to the same location.")
        }

        val oldWrites: List<ExecutionGraphNode> = coherencyOrder[location]!!
        val writes: MutableList<ExecutionGraphNode> = ArrayList(oldWrites)

        val write1Index = writes.indexOf(write1)
        val write2Index = writes.indexOf(write2)

        if (write1Index == -1 || write2Index == -1) {
            throw HaltCheckerException.Companion.error(
                "One of the write events is not found in the coherency order."
            )
        }

        var laterWrite = write2
        var earlierIndex = write1Index
        var laterIndex = write2Index
        if (write1Index > write2Index) {
            laterWrite = write1
            earlierIndex = write2Index
            laterIndex = write1Index
        }

        // Insert later write just before the earlier write in the writes list while moving the rest
        // of the writes.
        writes.removeAt(laterIndex)
        writes.add(earlierIndex, laterWrite)

        // Update the edges
        // TODO :: The following operation is not efficient. It should be optimized.
        for (i in 0..<oldWrites.size - 1) {
            oldWrites[i].removeEdge(oldWrites[i + 1], Relation.Coherency)
        }
        for (i in 0..<writes.size - 1) {
            writes[i].addEdge(writes[i + 1], Relation.Coherency)
        }

        coherencyOrder[location] = writes
    }

    /**
     * Returns the coherency placings of the given write event under sequential consistency.
     *
     *
     * Writes that are not _porf_-before the given write event. (Tied to Sequential consistency
     * model)
     *
     * @param write The write event.
     * @return The coherency placings of the given write event.
     */
    fun getCoherentPlacings(write: ExecutionGraphNode): List<ExecutionGraphNode> {
        if (EventUtils.isExclusiveWrite(write.event)) {
            // Easy path, since the coMax will be PORF before this write.
            // Based on the assumption that there are no concurrent writes between an exclusive read
            // and an exclusive write.
            return ArrayList()
        }
        val allWrites: List<ExecutionGraphNode> = coherencyOrder[write.event.location]!!
        var writesAfter = splitNodesBefore(write, allWrites)
        if (writesAfter.isEmpty()) {
            // Bug! There should at least be the init
            throw HaltCheckerException.Companion.error("No writes after the given write event.")
        }
        writesAfter.removeAt(writesAfter.size - 1) // removing the only porf-prefix write
        if (writesAfter.isEmpty()) {
            // No writes after the given write event
            return writesAfter
        }
        // Remove exclusive writes
        // Following the sequential consistency model, we only consider non-exclusive writes
        // (referencing GenMC implementation)
        writesAfter =
            writesAfter.stream()
                .filter { w: ExecutionGraphNode -> !EventUtils.isExclusiveWrite(w.event) }
                .toList()
        return writesAfter
    }

    /**
     * Updates the reads from relation between the given read and write events.
     *
     *
     * Invalidates the total order and the vector clocks of events in the graph. The concern of
     * fixing the total order and the vector clocks is passed to the calling function.
     *
     * @param read  The read event.
     * @param write The write event.
     */
    fun changeReadsFrom(read: ExecutionGraphNode, write: ExecutionGraphNode) {
        val writes = read.getPredecessors(Relation.ReadsFrom)
        if (writes!!.size != 1) {
            throw HaltCheckerException.Companion.error("A read has more than one RF back edge.")
        }
        try {
            val previousWrite = getEventNode(writes.iterator().next()!!)
            previousWrite.removeEdge(read, Relation.ReadsFrom)
            write.addEdge(read, Relation.ReadsFrom)
        } catch (e: NoSuchEventException) {
            throw HaltCheckerException.Companion.error("The previous write event is not found.")
        }
    }

    /**
     * Sets the reads from relation between the given read and write events.
     *
     *
     * Does not validate if there is an existing reads-from edge to the corresponding read
     *
     * @param read  The read event.
     * @param write The write event.
     */
    fun setReadsFrom(read: ExecutionGraphNode, write: ExecutionGraphNode) {
        write.addEdge(read, Relation.ReadsFrom)
    }

    /**
     * Tracks the coherency order between the given write event and the previous write event to the
     * same location.
     *
     * @param write The write event.
     */
    fun trackCoherency(write: ExecutionGraphNode) {
        val location = write.event.location
        if (!coherencyOrder.containsKey(location)) {
            val writes: MutableList<ExecutionGraphNode> = ArrayList()
            writes.add(allEvents[0])
            coherencyOrder[location] = writes
        }
        var previousWrite = allEvents[0]
        if (coherencyOrder[location]!!.size > 1) {
            previousWrite =
                coherencyOrder[location]!![coherencyOrder[location]!!.size - 1]
        }
        if (previousWrite.key() == write.key()) {
            // No clue why this happens, but it does and need to figure out why!
            return
        }
        coherencyOrder[location]!!.add(write)
        LOGGER.debug(
            "Adding coherency edge between {} and {}",
            previousWrite.event.key().toString(),
            write.event.key().toString()
        )
        previousWrite.addEdge(write, Relation.Coherency)
    }

    fun restrictBySet(set: Set<Event.Key?>) {
        // We use the following map to track the modified locations of write events.
        // It is used to update the CO-edges.
        val modifiedLocations: MutableMap<Int?, List<ExecutionGraphNode>> = HashMap()
        for (key in set) {
            // Collect and remove the event in the allEvents which it holds the key
            var node: ExecutionGraphNode? = null
            for (event in allEvents) {
                if (event.key() == key) {
                    node = event
                    break
                }
            }
            if (node == null) {
                throw HaltCheckerException.Companion.error("The restricting node is not in all events")
            }

            // Collect the location of the write event
            if (node.event.isWrite || node.event.isWriteEx) {
                val location = node.event.location
                if (!modifiedLocations.containsKey(location)) {
                    modifiedLocations[location] = coherencyOrder[location]!!
                }
            }

            allEvents.removeIf { event: ExecutionGraphNode -> event.key() == key }

            // Each event is the taskEvents which holds the key must be removed
            val task = Math.toIntExact(key.getTaskId())
            if (task >= taskEvents.size) {
                throw HaltCheckerException.Companion.error("The restricting node is not in task events")
            }
            taskEvents[task].removeIf { event: ExecutionGraphNode -> event.key() == key }

            // Each event in the coherencyOrder which holds the key must be removed
            val location = node.event.location
            if (location != null) {
                if (!coherencyOrder.containsKey(location)) {
                    throw HaltCheckerException.Companion.error(
                        "The restricting node is not in coherency order"
                    )
                }
                coherencyOrder[location]!!.removeIf { e: ExecutionGraphNode -> e.key() == key }
            }
        }

        // Remove dangling edges
        for (node in allEvents) {
            val successors = node.allSuccessors
            successors!!.forEach { (relation: Relation?, edges: MutableList<Event.Key?>?) ->
                edges!!.removeIf { o: Event.Key? -> set.contains(o) }
            }
            val predecessors = node.allPredecessors
            predecessors!!.forEach { (relation: Relation?, edges: MutableList<Event.Key?>?) ->
                edges!!.removeIf { o: Event.Key? -> set.contains(o) }
            }
        }

        // Recompute the co-edges
        // TODO :: This approach is not efficient and must be revisited
        for ((key, value) in modifiedLocations) {
            recomputeCoEdges(key, value)
        }

        // Remove blocking labels
    }

    private fun recomputeCoEdges(location: Int?, oldWrites: List<ExecutionGraphNode>) {
        if (!coherencyOrder.containsKey(location)) {
            throw HaltCheckerException.Companion.error("The location is not in the coherency order")
        }

        if (coherencyOrder[location]!!.size == 1) {
            // No need to recompute the edges
            return
        }

        val writes: List<ExecutionGraphNode> = coherencyOrder[location]!!
        // Update the edges
        for (i in 0..<oldWrites.size - 1) {
            oldWrites[i].removeEdge(oldWrites[i + 1], Relation.Coherency)
        }
        for (i in 0..<writes.size - 1) {
            writes[i].addEdge(writes[i + 1], Relation.Coherency)
        }
    }

    //
    //    public void checkCoBackEdges() {
    //        for (Map.Entry<Integer, List<ExecutionGraphNode>> entry : coherencyOrder.entrySet()) {
    //            for (ExecutionGraphNode write : entry.getValue()) {
    //                List<Event.Key> coBackEdges = write.getPredecessors(Relation.Coherency);
    //                if (coBackEdges != null && coBackEdges.size() > 1) {
    //                    throw HaltExecutionException.error("The previous writes are more than 1");
    //                }
    //            }
    //        }
    //    }
    /**
     * Recomputes the vector clocks of all nodes in the execution graph.
     */
    fun recomputeVectorClocks() {
        val topoSorter = TopologicalSorter(this)
        try {
            topoSorter.sortWithVisitor(
                object : ExecutionGraphNodeVisitor {
                    private val clocks = HashMap<Event.Key?, LamportVectorClock>()

                    override fun visit(node: ExecutionGraphNode) {
                        if (node.event.isInit) {
                            clocks[node.key()] = LamportVectorClock(0)
                            return
                        }

                        if (EventUtils.isBlockingLabel(node.event)) {
                            // Blocking labels are not tracked in the vector clock
                            return
                        }

                        val poBeforeNode = node.poPredecessor
                            ?: // No PO predecessor, this is the first event in the task
                            throw HaltCheckerException.Companion.error(
                                "Invalid PO predecessor for the event."
                            )
                        val newClock =
                            LamportVectorClock(
                                clocks[poBeforeNode]!!,
                                Math.toIntExact(node.key().taskId)
                            )
                        node.forEachPredecessor { relation: Relation, preds: List<Event.Key> ->
                            if (relation == Relation.Coherency) {
                                return@forEachPredecessor
                            }
                            preds.forEach(
                                Consumer<Event.Key> { pred: Event.Key? ->
                                    val predClock = clocks[pred]
                                        ?: throw HaltCheckerException.Companion.error(
                                            "The predecessors clock is not found."
                                        )
                                    newClock.update(predClock)
                                })
                        }

                        // Update the clock of the node
                        clocks[node.key()] = newClock
                        node.vectorClock = newClock
                    }
                })
        } catch (e: GraphCycleException) {
            throw HaltCheckerException.Companion.error("The execution graph is not a DAG.")
        }
    }

    fun restrict(restrictingNode: ExecutionGraphNode?) {
        // We use the following map to track the modified locations of write events.
        // It is used to update the CO-edges.
        val modifiedLocations: MutableMap<Int?, List<ExecutionGraphNode>> = HashMap()

        // Removing and storing all inserted events after the restricting node from allEvents (
        // Insertion order )
        val indexRestrictingNode = allEvents.indexOf(restrictingNode)
        if (indexRestrictingNode == -1) {
            throw HaltCheckerException.Companion.error("The restricting node is not found.")
        }
        val newAllEvents: MutableList<ExecutionGraphNode> = ArrayList(indexRestrictingNode + 1)
        val removedNodes: MutableList<ExecutionGraphNode> =
            ArrayList(allEvents.size - indexRestrictingNode)

        for (i in allEvents.indices) {
            if (i <= indexRestrictingNode) {
                newAllEvents.add(allEvents[i])
            } else {
                removedNodes.add(allEvents[i])
            }
        }
        allEvents = newAllEvents
        // Iterating over these nodes and remove them from the taskEvents and coherencyOrder
        for (node in removedNodes) {
            if (node.event.isWrite || node.event.isWriteEx) {
                // Based on the assumption that the init node is never removed. So, we only have to
                // update the CO if
                // the node is a write or writeEx event.
                val location = node.event.location
                if (!modifiedLocations.containsKey(location)) {
                    modifiedLocations[location] = coherencyOrder[location]!!
                }
            }

            // Removing from coherencyOrder
            val location = node.event.location

            if (location != null) {
                if (!coherencyOrder.containsKey(location)) {
                    throw HaltCheckerException.Companion.error(
                        "The restricting node is not in coherency order"
                    )
                }

                coherencyOrder[location]!!.removeIf { e: ExecutionGraphNode -> e.key() == node.key() }
            }

            // Removing from taskEvents
            val task = Math.toIntExact(node.event.taskId)
            if (task >= taskEvents.size) {
                throw HaltCheckerException.Companion.error("The restricting node is not in task events")
            }
            taskEvents[task].removeIf { e: ExecutionGraphNode -> e.key() == node.key() }
        }

        // Removing dangling edges
        val removedKeys =
            removedNodes.stream().map { obj: ExecutionGraphNode -> obj.key() }.collect(Collectors.toSet())
        for (node in allEvents) {
            node.forEachSuccessor { relation: Relation?, edges: MutableList<Event.Key> ->
                edges.removeIf { o: Event.Key? -> removedKeys.contains(o) }
            }
            node.forEachPredecessor { relation: Relation?, edges: MutableList<Event.Key> ->
                edges.removeIf { o: Event.Key? -> removedKeys.contains(o) }
            }
        }

        // Recompute the co-edges
        // TODO :: This approach is not efficient and must be revisited
        for ((key, value) in modifiedLocations) {
            recomputeCoEdges(key, value)
        }
    }

    /**
     * Returns an iterator walking through the nodes in a topological sort order.
     */
    @Throws(GraphCycleException::class)
    fun iterator(): MutableList<ExecutionGraphNode> {
        return (TopologicalSorter(this)).sort()
    }

    /**
     * Returns List of nodes while silently ignoring any errors with cycles *
     */
    fun unsafeIterator(): List<ExecutionGraphNode> {
        return try {
            (TopologicalSorter(this)).sort()
        } catch (e: GraphCycleException) {
            emptyList()
        }
    }

    fun checkExtensiveConsistency(): Boolean {
        try {
            checkConsistency()

            //            List<ExecutionGraphNode> topologicalSort =
            // checkConsistencyAndTopologicallySort();

            // Check that finish is the last event in each task
            for (i in taskEvents.indices) {
                val taskEventList: List<ExecutionGraphNode> = taskEvents[i]
                if (taskEventList.isEmpty()) {
                    continue  // No events for this task
                }
                val lastEvent = taskEventList[taskEventList.size - 1]
                if (!EventUtils.isThreadFinish(lastEvent.event)) {
                    LOGGER.error(
                        "Task {} does not end with a finish event: {}",
                        i,
                        lastEvent.event
                    )
                    return false
                }
            }

            if (!checkCoherencyEdges()) {
                LOGGER.error("Coherency edges are not consistent.")
                return false
            }
            if (!checkReadsFromEdges()) {
                LOGGER.error("Reads from edges are not consistent.")
                return false
            }
            checkDanglingEdges()
            checkBrokenEdges()
        } catch (e: Exception) {
            // If any exception is thrown, the graph is not consistent
            LOGGER.error("Failed to check consistency of the execution graph: {}", e.message)
            return false
        }
        return true
    }

    private fun checkBrokenEdges() {
        val eventMap: MutableMap<Event.Key?, ExecutionGraphNode> = HashMap()
        for (node in allEvents) {
            eventMap[node.key()] = node
        }

        for (node in allEvents) {
            val successors = node.allSuccessors
            for ((key1, value) in successors!!) {
                for (key in value!!) {
                    if (!eventMap.containsKey(key)) {
                        throw HaltCheckerException.Companion.error(
                            String.format(
                                "Broken edge found from %s to %s",
                                node.key().toString(), key.toString()
                            )
                        )
                    }
                    val successorNode = eventMap[key]
                    if (!successorNode!!.hasPredecessor(node.key(), key1)) {
                        throw HaltCheckerException.Companion.error(
                            String.format(
                                "Broken edge found from %s to %s",
                                node.key().toString(), key.toString()
                            )
                        )
                    }
                }
            }
        }
    }

    fun checkReadsFromEdges(): Boolean {
        for (i in allEvents.indices) {
            val node = allEvents[i]
            if (!node.event.isRead && !node.event.isReadEx) {
                // Only check read events
                continue
            }
            val readsFrom = node.getPredecessors(Relation.ReadsFrom)
            if (readsFrom!!.size != 1) {
                LOGGER.error(
                    "Read event {} has {} reads from predecessors, expected 1.",
                    node.event.key(),
                    readsFrom.size
                )
                return false
            }
        }
        return true
    }

    fun checkConsistency(): MutableList<ExecutionGraphNode> {
        val clone = ExecutionGraph(this)
        try {
            // Add edges from reads to alternative writes
            for ((_, writes) in clone.coherencyOrder) {
                for (write in writes) {
                    val readsPerLocation: MutableMap<Int?, MutableList<ExecutionGraphNode>> = HashMap()
                    val reads = write.getSuccessors(Relation.ReadsFrom)

                    for (readKey in reads!!) {
                        try {
                            val readNode = getEventNode(readKey)
                            if (!readNode.event.isReadEx) {
                                // We only check for read exclusive events
                                continue
                            }
                            val readLocation = readNode.event.location
                            if (!readsPerLocation.containsKey(readLocation)) {
                                readsPerLocation[readLocation] = ArrayList()
                            }
                            readsPerLocation[readLocation]!!.add(readNode)
                        } catch (e: NoSuchEventException) {
                            throw HaltCheckerException.Companion.error("The read event is not found.")
                        }
                    }

                    for ((_, locationReads) in readsPerLocation) {
                        if (locationReads.size > 1) {
                            // More than one read to the same location
                            // This is not allowed in the sequential consistency model
                            return ArrayList()
                        }
                    }
                }

                for (i in 0..<writes.size - 1) {
                    val write = writes[i]
                    if (write.event.isInit) {
                        continue
                    }
                    val reads = write.getSuccessors(Relation.ReadsFrom)
                    if (reads!!.isEmpty()) {
                        // No reads from this write, continue
                        continue
                    }
                    val readNodes: MutableList<ExecutionGraphNode> = ArrayList()
                    for (key in reads) {
                        readNodes.add(clone.getEventNode(key))
                    }
                    val nextWrite = writes[i + 1]
                    for (read in readNodes) {
                        read.addEdge(nextWrite, Relation.FR)
                    }
                }
            }
            return fixTopologicalSort(clone.iterator())
        } catch (e: NoSuchEventException) {
            throw HaltCheckerException.Companion.error(
                "Hit an event that doesn't exist in the graph: " + e.message
            )
        } catch (e: GraphCycleException) {
            LOGGER.debug("Hit an inconsistent graph: {}", e.message)
            return ArrayList()
        }
    }

    fun checkDanglingEdges() {
        for (node in allEvents) {
            val successors = node.allSuccessors
            for ((_, value) in successors!!) {
                if (value!!.isEmpty()) {
                    continue  // No successors
                }
                for (key in value) {
                    if (!hasEventNode(key)) {
                        throw HaltCheckerException.Companion.error(
                            String.format(
                                "Dangling edge found from %s to %s",
                                node.key().toString(), key
                            )
                        )
                    }
                }
            }
        }
    }

    fun checkConsistencyAndTopologicallySort(): MutableList<ExecutionGraphNode> {
        checkDanglingEdges()
        return checkConsistency()
    }

    private fun fixTopologicalSort(topologicalSort: MutableList<ExecutionGraphNode>): MutableList<ExecutionGraphNode> {
        // The problem arises between ReadEx and WriteEx events of the same task ID.
        // Other events can sneak in between them. since the WriteEx first requires that the ReadEx
        // is scheduled.
        // We need to fix the topological sort by moving the WriteEx event before the ReadEx event.

        val fixedTopologicalSort: MutableList<ExecutionGraphNode> = ArrayList()

        for (i in topologicalSort.indices) {
            val node = topologicalSort[i]
            fixedTopologicalSort.add(node)
            if (EventUtils.isLockAcquireRead(node.event)) {
                val next = topologicalSort[i + 1]
                if (EventUtils.isLockAcquireWrite(next.event)
                    && node.event.taskId == next.event.taskId
                ) {
                    // Next event is a WriteEx event of the same task ID
                    continue
                }

                // We need to find the WriteEx event of the same task ID
                for (j in i + 1..<topologicalSort.size) {
                    val nextNode = topologicalSort[j]
                    if (EventUtils.isLockAcquireWrite(nextNode.event)
                        && node.event.taskId == nextNode.event.taskId
                    ) {
                        // Move the WriteEx event before the ReadEx event
                        fixedTopologicalSort.add(nextNode)

                        // Remove the WriteEx event from the topological sort
                        topologicalSort.removeAt(j)
                        break
                    }
                }
            }
        }
        return fixedTopologicalSort
    }

    val isEmpty: Boolean
        /**
         * Returns true if the graph contains only the initial event.
         */
        get() = allEvents.size == 1 && allEvents[0].event.isInit

    /**
     * Clears the execution graph.
     */
    fun clear() {
        allEvents.clear()
        coherencyOrder.clear()
        taskEvents.clear()
        blockedLocks.clear()
    }

    fun toJsonString(): String {
        val nodes = JsonObject()
        for (node in allEvents) {
            nodes.add(node.key().toString(), node.toJson())
        }
        val gson = JsonObject()
        gson.add("nodes", nodes)
        return gson.toString()
    }

    fun toJsonStringIgnoreLocation(): String {
        val nodes = JsonObject()
        val sortedEvents: List<ExecutionGraphNode> = ArrayList(allEvents)
        sortedEvents.sort(
            Comparator { o1: ExecutionGraphNode, o2: ExecutionGraphNode -> o1.event.key().compareTo(o2.event.key()) })
        for (node in sortedEvents) {
            nodes.add(node.key().toString(), node.toJsonIgnoreLocation())
        }
        val gson = JsonObject()
        gson.add("nodes", nodes)

        return gson.toString()
    }

    // For debugging
    fun printCO() {
        for (loc in coherencyOrder.keys) {
            println("[Exec Graph debug]: printCO $loc")
            for (write in coherencyOrder[loc]!!) {
                println("[Exec Graph debug]: the writes " + write.event.toString())
            }
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is ExecutionGraph) {
            return false
        }

        // Check if the two graphs have the same number of events
        if (allEvents.size != o.allEvents.size) {
            return false
        }

        // Check if the two graphs have the same events in topological order
        val curNodes = unsafeIterator()

        val thatNodes = o.unsafeIterator()

        for (i in curNodes.indices) {
            if (curNodes[i] != thatNodes[i]) {
                return false
            }
        }

        // Check edges between the nodes
        for (i in curNodes.indices) {
            if (!curNodes[i].equalsEdges(thatNodes[i])) {
                return false
            }
        }

        return true
    }

    fun checkCoherencyEdges(): Boolean {
        for ((key, writes) in coherencyOrder) {
            if (key == LocationStore.Companion.ThreadLocation) {
                // Skip the thread location
                continue
            }
            for (i in 0..<writes.size - 1) {
                val write = writes[i]
                if (!write.hasEdge(writes[i + 1].event.key(), Relation.Coherency)) {
                    return false
                }
                if (write.event.isInit) {
                    // Skip the init event
                    continue
                }
                val successiveWrites = write.getSuccessors(Relation.Coherency)
                if (successiveWrites!!.size > 1) {
                    // More than one write to the same location
                    // This is not allowed in the sequential consistency model
                    return false
                }
            }
        }
        return true
    }

    fun trackThreadJoinCompletion(eventNode: ExecutionGraphNode?) {
        // TODO: complete this
    }

    // When a new task wants to acquire a lock
    // We keep track of it and add a blocking label
    fun blockTaskForLock(event: Event) {
        addBlockingLabel(event.taskId)
        if (!blockedLocks.containsKey(event.location)) {
            blockedLocks[event.location] = ArrayList()
        }
        blockedLocks[event.location]!!.add(event.taskId)
    }

    // When a lock is released,
    // We unblock all the tasks that are waiting for it
    // This is done by removing the blocking label
    // Yet, we retain the task in the blockedLocks map
    fun unblockAllTasksForLock(location: Int?) {
        if (!blockedLocks.containsKey(location)) {
            // Nothing to unblock
            return
        }
        for (taskId in blockedLocks[location]!!) {
            unBlockTask(taskId)
        }
    }

    // When a task acquires a lock,
    // We remove it from the blockedLocks map
    // Here the assumption is that the task has already been unblocked
    // Then for all remaining tasks that are waiting for the lock,
    // We add a blocking label
    fun acquireLock(location: Int?, taskId: Long?) {
        if (!blockedLocks.containsKey(location)) {
            return
        }
        blockedLocks[location]!!.remove(taskId!!)
        if (blockedLocks[location]!!.isEmpty()) {
            blockedLocks.remove(location)
            return
        }
        for (taskID in blockedLocks[location]!!) {
            addBlockingLabel(taskID)
        }
    }

    fun waitingForLock(location: Int?, taskId: Long?): Boolean {
        if (!blockedLocks.containsKey(location)) {
            // No tasks waiting for this.
            // Hence by definition, the current task is not waiting
            return false
        }
        return blockedLocks[location]!!.contains(taskId!!)
    }

    /**
     * Generic visitor interface for the execution graph nodes.
     */
    interface ExecutionGraphNodeVisitor {
        fun visit(node: ExecutionGraphNode)
    }

    /**
     * Topological sorter for the execution graph.
     *
     *
     * Sorts the nodes in topological order and throws an exception if the graph has cycles.
     */
    class TopologicalSorter(private val graph: ExecutionGraph) {
        private val nodeMap: MutableMap<Event.Key?, ExecutionGraphNode> =
            HashMap()

        /**
         * Initializes a new topological sorter for the given graph.
         *
         * @param graph The graph to sort.
         */
        init {
            for (node in graph.allEvents) {
                nodeMap[node.key()] = node
            }

            // Need to include blocking labels in the graph
            for (i in graph.taskEvents.indices) {
                val tasksForI = graph.taskEvents[i].size
                if (tasksForI > 0) {
                    val lastTask = graph.taskEvents[i][tasksForI - 1]
                    nodeMap[lastTask.key()] = lastTask
                }
            }
        }

        /**
         * Sorts the graph in topological order.
         *
         * @return The sorted list of nodes.
         * @throws GraphCycleException If the graph has cycles.
         */
        @Throws(GraphCycleException::class)
        fun sort(): MutableList<ExecutionGraphNode> {
            val queue: Deque<ExecutionGraphNode> = ArrayDeque()
            val inDegreeMap: MutableMap<Event.Key?, Int> = HashMap()
            val output: MutableList<ExecutionGraphNode> = ArrayList()

            queue.add(graph.allEvents[0])

            for (node in graph.allEvents) {
                inDegreeMap[node.key()] = node.inDegree
            }

            while (!queue.isEmpty()) {
                val node = queue.pop()
                if (!EventUtils.isBlockingLabel(node.event)) {
                    output.add(node)
                }

                val toAdd: MutableList<Event.Key> = ArrayList()

                node.forEachSuccessor { relation: Relation?, successors: List<Event.Key> ->
                    successors.forEach(
                        Consumer { successor: Event.Key ->
                            val newIndegree =
                                inDegreeMap.getOrDefault(successor, 1) - 1
                            inDegreeMap[successor] = newIndegree
                            if (newIndegree == 0) {
                                toAdd.add(successor)
                            }
                        })
                }

                toAdd.sort(Comparator<Event.Key> { key: Event.Key? -> compareTo(key) })
                toAdd.forEach(
                    Consumer { key: Event.Key? ->
                        if (!nodeMap.containsKey(key)) {
                            LOGGER.debug("Error finding the node for key: {}", key)
                        }
                        queue.add(nodeMap[key])
                    })
            }

            if (output.size != graph.allEvents.size) {
                throw GraphCycleException("Graph has cycles")
            } else {
                return output
            }
        }

        /**
         * Sorts the graph in topological order and visits each node using the given visitor.
         *
         * @param visitor The visitor to use for each node.
         * @throws GraphCycleException If the graph has cycles.
         */
        @Throws(GraphCycleException::class)
        fun sortWithVisitor(visitor: ExecutionGraphNodeVisitor) {
            val queue: Deque<ExecutionGraphNode> = ArrayDeque()
            val inDegreeMap: MutableMap<Event.Key?, Int> = HashMap()
            val output: MutableList<Event.Key?> = ArrayList()

            queue.add(graph.allEvents[0])

            for (node in graph.allEvents) {
                inDegreeMap[node.key()] = node.inDegree
            }
            try {
                while (!queue.isEmpty()) {
                    val node = queue.pop()
                    if (!EventUtils.isBlockingLabel(node.event)) {
                        output.add(node.key())
                    }
                    visitor.visit(node)

                    val toAdd: MutableList<Event.Key> = ArrayList()

                    node.forEachSuccessor { relation: Relation?, successors: List<Event.Key> ->
                        successors.forEach(
                            Consumer { successor: Event.Key ->
                                val newIndegree =
                                    inDegreeMap.getOrDefault(successor, 1) - 1
                                inDegreeMap[successor] = newIndegree
                                if (newIndegree == 0) {
                                    toAdd.add(successor)
                                }
                            })
                    }

                    toAdd.sort(Comparator<Event.Key> { key: Event.Key? -> compareTo(key) })
                    toAdd.forEach(
                        Consumer { key: Event.Key? ->
                            if (!nodeMap.containsKey(key)) {
                                LOGGER.debug("Error finding the node")
                            }
                            queue.add(nodeMap[key])
                        })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (output.size != graph.allEvents.size) {
                throw GraphCycleException("Graph has cycles")
            }
        }

        /**
         * Exception thrown when the graph has cycles.
         */
        class GraphCycleException
        /**
         * Initializes a new graph cycle exception with the given message.
         *
         * @param message The message for the exception.
         */
            (message: String?) : Exception(message)
    }

    // For debugging
    fun printGraph() {
        val sb = StringBuilder()
        sb.append("Execution Graph:\n")
        for (i in taskEvents.indices) {
            sb.append("Tasks ").append(i).append(": \n")
            for (node in taskEvents[i]) {
                sb.append(node.event)
                // Print predecessors and successors
                sb.append(" [P: ")
                for (relation in node.allPredecessors.keys) {
                    sb.append("{").append(relation).append(": ")
                    for (key in node.getPredecessors(relation)) {
                        sb.append(key).append("/")
                    }
                    sb.append("} ")
                }
                sb.append("] [S: ")
                for (relation in node.getEdges().keys) {
                    sb.append("{").append(relation).append(": ")
                    for (key in node.getSuccessors(relation)) {
                        sb.append(key).append("/")
                    }
                    sb.append("] ")
                }
                sb.append(" ---> \n")
            }
            sb.append("\n")
        }
        sb.append("\n")

        sb.append("All Events:\n")
        for (node in allEvents) {
            sb.append(node.event).append(" -> ")
        }
        sb.append("\n")
        sb.append("\n")

        sb.append("Coherency Order:\n")
        for (loc in coherencyOrder.keys) {
            sb.append("Location ").append(loc).append(": ")
            for (node in coherencyOrder[loc]!!) {
                sb.append(node.event).append(" -> ")
            }
            sb.append("\n")
        }
        sb.append("\n")
        LOGGER.debug("{}", sb.toString())
    }

    val isRfConsistent: Boolean
        get() {
            // For each read event, check if the read-from edge is present
            for (node in allEvents) {
                if (node.event.isRead) {
                    val writes =
                        node.getPredecessors(Relation.ReadsFrom)
                    if (writes != null && writes.size != 1) {
                        return false
                    }
                }
            }
            return true
        }

    val allPoMaxNode: List<ExecutionGraphNode>
        get() {
            val result: MutableList<ExecutionGraphNode> = ArrayList()
            // loop over all the lists of the taskEvents and collect the last event of each list
            for (taskEventList in taskEvents) {
                if (!taskEventList.isEmpty()) {
                    result.add(taskEventList[taskEventList.size - 1])
                }
            }
            return result
        }

    fun isCoMax(event: Event): Boolean {
        if (EventUtils.isWrite(event)) {
            try {
                val node = getEventNode(event.key())
                val succ = node.getSuccessors(Relation.Coherency)
                if (succ != null && !succ.isEmpty()) {
                    return false
                }
            } catch (e: NoSuchEventException) {
                throw HaltCheckerException.Companion.error(
                    "The write event does not exist in the execution graph."
                )
            }
        }
        return true
    }

    fun isRfMax(event: Event): Boolean {
        if (EventUtils.isWrite(event)) {
            try {
                val node = getEventNode(event.key())
                val succ = node.getSuccessors(Relation.ReadsFrom)
                if (succ != null && !succ.isEmpty()) {
                    return false
                }
            } catch (e: NoSuchEventException) {
                throw HaltCheckerException.Companion.error(
                    "The write event does not exist in the execution graph."
                )
            }
        }
        return true
    }

    fun isFrMax(event: Event): Boolean {
        if (EventUtils.isRead(event)) {
            try {
                val node = getEventNode(event.key())
                val pred = node.getPredecessors(Relation.ReadsFrom)
                if (pred == null || pred.isEmpty()) {
                    throw HaltCheckerException.Companion.error(
                        "The read event does not have a FR predecessor."
                    )
                }
                val w = getEventNode(pred[0]!!)
                return isCoMax(w.event)
            } catch (e: NoSuchEventException) {
                throw RuntimeException(e)
            }
        }
        return true
    }

    fun isTcMax(event: Event): Boolean {
        if (EventUtils.isThreadStart(event)) {
            try {
                val node = getEventNode(event.key())
                val succ = node.getSuccessors(Relation.ThreadCreation)
                if (succ != null && !succ.isEmpty()) {
                    return false
                }
            } catch (e: NoSuchEventException) {
                throw HaltCheckerException.Companion.error(
                    "The thread start event does not exist in the execution graph."
                )
            }
        }
        return true
    }

    fun isStMax(event: Event): Boolean {
        try {
            val node = getEventNode(event.key())
            val succ = node.getSuccessors(Relation.ThreadStart)
            if (succ != null && !succ.isEmpty()) {
                return false
            }
        } catch (e: NoSuchEventException) {
            throw HaltCheckerException.Companion.error(
                "The thread start event does not exist in the execution graph."
            )
        }

        return true
    }

    fun isJtMax(event: Event): Boolean {
        if (EventUtils.isThreadFinish(event)) {
            try {
                val node = getEventNode(event.key())
                val succ = node.getSuccessors(Relation.ThreadJoin)
                if (succ != null && !succ.isEmpty()) {
                    return false
                }
            } catch (e: NoSuchEventException) {
                throw HaltCheckerException.Companion.error(
                    "The thread join event does not exist in the execution graph."
                )
            }
        }

        return true
    }

    fun isStartMaxWithStarter(e: Event): Boolean {
        if (e == null) {
            throw HaltCheckerException.Companion.error(
                "The event parameter is null"
            )
        }

        if (EventUtils.isThreadStart(e)) {
            val startedBy = EventUtils.getStartedBy(e)!!
            val starterPoMaxNode = getPoMaxNode(startedBy)
            val succ = starterPoMaxNode!!.getSuccessors(Relation.ThreadStart)
            // The following if checks if the PO-MAX event of the starter thread is still the cause event
            return !succ!!.isEmpty()
        } else {
            throw HaltCheckerException.Companion.error(
                "The event parameter is not a start event"
            )
        }
    }

    fun getPoMaxNode(taskId: Long): ExecutionGraphNode? {
        if (taskId < 0 || taskId >= taskEvents.size) {
            throw HaltCheckerException.Companion.error("Invalid task ID: $taskId")
        }
        val taskEventList: List<ExecutionGraphNode> = taskEvents[Math.toIntExact(taskId)]
        if (taskEventList.isEmpty()) {
            return null
        }
        return taskEventList[taskEventList.size - 1]
    }

    fun getFirstEventOfTask(taskId: Long): ExecutionGraphNode? {
        if (taskId < 0 || taskId >= taskEvents.size) {
            throw HaltCheckerException.Companion.error("Invalid task ID: $taskId")
        }
        val taskEventList: List<ExecutionGraphNode> = taskEvents[Math.toIntExact(taskId)]
        if (taskEventList.isEmpty()) {
            return null
        }
        return taskEventList[0]
    }

    fun getLastNodeOfTask(taskId: Long): ExecutionGraphNode? {
        if (taskId < 0 || taskId >= taskEvents.size) {
            throw HaltCheckerException.Companion.error("Invalid task ID: $taskId")
        }
        val taskEventList: List<ExecutionGraphNode> = taskEvents[Math.toIntExact(taskId)]
        if (taskEventList.isEmpty()) {
            return null
        }
        return taskEventList[taskEventList.size - 1]
    }

    fun isRdxInconsistent(wrxNode: ExecutionGraphNode): Boolean {
        val wrxNodeIndex = taskEvents[Math.toIntExact(wrxNode.event.taskId)].indexOf(wrxNode)
        val rdxNodeIndex = wrxNodeIndex - 1
        if (rdxNodeIndex < 0) {
            throw HaltCheckerException.Companion.error("The WRx node does not have a preceding RDX node.")
        }

        val rdxNode = taskEvents[Math.toIntExact(wrxNode.event.taskId)][rdxNodeIndex]

        if (!EventUtils.isLockAcquireRead(rdxNode.event)) {
            throw HaltCheckerException.Companion.error("The preceding event is not a RDX event.")
        }

        val rfPredecessors = rdxNode.getPredecessors(Relation.ReadsFrom)
        if (rfPredecessors!!.isEmpty() || rfPredecessors.size != 1) {
            throw HaltCheckerException.Companion.error("The RDX event does not have exactly one ReadsFrom predecessor.")
        }
        val rfKey = rfPredecessors[0]
        val rfNode: ExecutionGraphNode
        try {
            rfNode = getEventNode(rfKey!!)
        } catch (e: NoSuchEventException) {
            throw HaltCheckerException.Companion.error("The ReadsFrom predecessor event does not exist in the execution graph.")
        }

        val rfSuccessors = rfNode.getSuccessors(Relation.ReadsFrom)
        return rfSuccessors!!.size <= 1
    }

    val isBlocked: Boolean
        get() {
            var blocked = false
            // Iterate over taskEvents to see if any task has a blocking label as its last event, or
            // the event before last is a blocked-assume event (the last event is the noop event representing the finish)
            for (taskEventList in taskEvents) {
                if (!taskEventList.isEmpty()) {
                    val lastEvent = taskEventList[taskEventList.size - 1]
                    if (EventUtils.isBlockingLabel(lastEvent.event)) {
                        blocked = true
                        break
                    }
                    if (taskEventList.size > 1) {
                        val beforeLastEvent = taskEventList[taskEventList.size - 2]
                        if (EventUtils.isBlockedAssume(beforeLastEvent.event)) {
                            blocked = true
                            break
                        }
                    }
                }
            }
            return blocked
        }

    fun size(): Int {
        return allEvents.size
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            ExecutionGraph::class.java
        )

        /**
         * Generate a task Schedule from a given sorted list of event nodes.
         *
         *
         * Note that the generated Schedule involves tasks that are 1-indexed and The trust
         * ExecutionGraph has tasks that are 0-indexed.
         *
         * @param taskEvents A sorted list of event nodes
         * @return A list of SchedulingChoiceWrappers.
         */
        fun getTaskSchedule(
            taskEvents: MutableList<ExecutionGraphNode?>
        ): List<SchedulingChoiceWrapper> {
            val result: MutableList<SchedulingChoiceWrapper> = ArrayList()
            taskEvents.removeAt(0) // Remove the init event
            taskEvents.removeAt(0) // Remove the first event of the main thread

            var oldLocation: Int? = null
            var i = 0
            while (i < taskEvents.size) {
                val node = taskEvents[i]
                var newLocation = node.getEvent().location
                // If the event is a blocking label then add the relevant task to the schedule
                if (EventUtils.isBlockingLabel(node.getEvent())) {
                    val taskId = node.getEvent().taskId
                    if (taskId == null) {
                        result.add(
                            SchedulingChoiceWrapper(
                                SchedulingChoice.Companion.blockExecution(), oldLocation
                            )
                        )
                    } else {
                        result.add(
                            SchedulingChoiceWrapper(
                                SchedulingChoice.Companion.blockTask(node.getEvent().taskId),
                                oldLocation
                            )
                        )
                    }
                } else if (EventUtils.isThreadStart(node.getEvent())) {
                    result.add(
                        SchedulingChoiceWrapper(
                            SchedulingChoice.Companion.task(EventUtils.getStartedBy(node.getEvent())!! + 1),
                            oldLocation
                        )
                    )
                } else if (EventUtils.isLockAcquireRead(node.getEvent())) {
                    result.add(
                        SchedulingChoiceWrapper(
                            SchedulingChoice.Companion.task(node.getEvent().taskId + 1),
                            oldLocation
                        )
                    )
                    // We skip the lock acquire write since the two events are added for a single
                    // runtime event
                    i++
                } else if (EventUtils.isThreadJoin(node.getEvent())) {
                    // If we are scheduling a thread join,
                    // we duplicate the task ID. since each join in trust is two separate events in the
                    // runtime. Join request and join completion.
                    val taskId = node.getEvent().taskId + 1
                    result.add(SchedulingChoiceWrapper(SchedulingChoice.Companion.task(taskId), oldLocation))
                    oldLocation = newLocation
                    newLocation = null
                    result.add(SchedulingChoiceWrapper(SchedulingChoice.Companion.task(taskId), oldLocation))
                } else {
                    // Adding 1 to the task ID since the task ID is 0-indexed inside Trust but 1-indexed
                    // in JMC
                    val taskId = node.getEvent().taskId + 1
                    result.add(SchedulingChoiceWrapper(SchedulingChoice.Companion.task(taskId), oldLocation))
                }
                oldLocation = newLocation
                i++
            }
            result.add(SchedulingChoiceWrapper(SchedulingChoice.Companion.end(), oldLocation))
            return result
        }
    }
}
