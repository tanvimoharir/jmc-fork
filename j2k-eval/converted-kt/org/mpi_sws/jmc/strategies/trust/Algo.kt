package org.mpi_sws.jmc.strategies.trust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.solver.SMTSolverTypes
import org.mpi_sws.jmc.solver.SolverUtil
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver
import org.mpi_sws.jmc.strategies.trust.SchedulingChoiceWrapper
import org.mpi_sws.jmc.util.FileUtil
import java.util.*

/**
 * Contains the core Trust algorithm implementation. ([](https://doi.org/10.1145/3498711)) We implement the recursive version described in the
 * paper and in the thesis. ([](https://kluedo.ub.rptu.de/frontdoor/index/index/docId/7670))
 *
 *
 * This class contains, in addition to the execution graph and the algorithm, the auxiliary state
 * needed to enforce a specific task scheduling order.
 *
 *
 * Disclaimer!! This algorithm assumes that the programs are deterministic. Meaning, if you run a
 * task, you will receive the same sequence of events in that task.
 */
class Algo {
    // The sequence of tasks to be scheduled. This is kept in sync with the execution graph that we
    // are currently visiting.
    private var guidingTaskSchedule: ArrayDeque<SchedulingChoiceWrapper>?
    var executionGraph: ExecutionGraph?
    private var isGuiding: Boolean
    val explorationStack: ExplorationStack
    private val locationStore: LocationStore
    private val tLogger: TreeLogger?
    private var numOfBlockedGraphs = 0L

    /**
     * @property [.solver] is used to store the [org.mpi_sws.jmc.solver.SymbolicSolver] object that is used
     * to solve symbolic operations.
     */
    private val solver: IncrementalSolver?

    /**
     * Creates a new instance of the Trust algorithm.
     */
    constructor() {
        this.guidingTaskSchedule = null
        this.isGuiding = false
        this.executionGraph = ExecutionGraph()
        this.explorationStack = ExplorationStack()
        this.locationStore = LocationStore()
        executionGraph!!.addEvent(Event.Companion.init())
        this.tLogger = null
        this.solver = null
    }

    constructor(hasTreeLogger: Boolean, solverType: String?) {
        this.guidingTaskSchedule = null
        this.isGuiding = false
        this.executionGraph = ExecutionGraph()
        this.explorationStack = ExplorationStack()
        this.locationStore = LocationStore()
        executionGraph!!.addEvent(Event.Companion.init())
        if (hasTreeLogger) {
            this.tLogger = TreeLogger()
        } else {
            this.tLogger = null
        }
        this.solver = initSolver(solverType)
    }

    private fun initSolver(solverType: String?): IncrementalSolver? {
        val type = getSolverType(solverType)
        if (type == SMTSolverTypes.OFF) {
            return null
        }
        return SolverUtil.getIncrementalSolver(type)
    }

    private fun getSolverType(solverType: String?): SMTSolverTypes? {
        if (solverType == null) {
            return null
        }
        return when (solverType.lowercase(Locale.getDefault())) {
            "z3" -> SMTSolverTypes.Z3
            "cvc5" -> SMTSolverTypes.CVC5
            "cvc4" -> SMTSolverTypes.CVC4
            "mathsat5" -> SMTSolverTypes.MATHSAT5
            "yices2" -> SMTSolverTypes.YICES2
            "opensmt" -> SMTSolverTypes.OPENSMT
            "smtinterpol" -> SMTSolverTypes.SMTINTERPOL
            "princess" -> SMTSolverTypes.PRINCESS
            "booleanor" -> SMTSolverTypes.BOOLECTOR
            "off" -> SMTSolverTypes.OFF
            else -> {
                LOGGER.warn("Unknown solver type: {}. Defaulting to Z3.", solverType)
                SMTSolverTypes.Z3
            }
        }
    }

    /**
     * Returns the next task to be scheduled according to the execution graph set in place.
     */
    fun nextTask(): SchedulingChoice<*>? {
        if (!isGuiding) {
            return null
        }
        if (guidingTaskSchedule == null || guidingTaskSchedule!!.isEmpty()) {
            return null
        }
        val out = guidingTaskSchedule!!.pop().choice
        if (guidingTaskSchedule!!.isEmpty()) {
            LOGGER.debug("End of guiding phase")
            isGuiding = false
        }
        return out
    }

    private fun handleGuidedEvent(event: Event) {
        if (EventUtils.isLockAcquired(event)) {
            // Ignore lock acquired events in the guiding trace
            // These are not added to the execution graph and does not bear any consequence
            // on what occurs below.

            // The lock acquired event is also not a yielding event therefore the real event will
            // follow.

            return
        }
        val choiceW = guidingTaskSchedule!!.peek()
        val choice = choiceW.choice
        if (choice.isBlockTask) {
            throw HaltTaskException.Companion.blocked(choice.taskId)
        } else if (choice.isBlockExecution) {
            throw HaltExecutionException.Companion.error("Encountered a block label")
        } else if (choice.isEnd && !EventUtils.isExclusiveRead(event)) {
            // We have observed all the events in the guiding trace, pop the end event
            // Unless it is an exclusive read, then we expect a matching exclusive write
            guidingTaskSchedule!!.pop()
            if (guidingTaskSchedule!!.isEmpty()) {
                isGuiding = false
                LOGGER.debug("The guiding task schedule is empty")
            }
        }
        if (choiceW.hasLocation()) {
            val location = choiceW.location
            if (event.location == null) {
                throw HaltExecutionException.Companion.error(
                    "Expected location with event but it contains none"
                )
            }
            if (event.location == location) {
                return
            }
            if (!locationStore.containsAlias(event.location)) {
                locationStore.addAlias(location, event.location)
            }
        }
    }

    /**
     * Records the task schedule generated by the execution graph to the specified filePath.
     *
     * @param filePath to record the task schedule in.
     */
    @Throws(JmcCheckerException::class)
    fun recordTaskSchedule(filePath: String) {
        val taskSchedule: List<SchedulingChoiceWrapper> =
            ExecutionGraph.Companion.getTaskSchedule(executionGraph!!.checkConsistency())
        FileUtil.storeTaskSchedule(
            filePath, taskSchedule.stream().map(SchedulingChoiceWrapper::choice).toList()
        )
    }

    /**
     * Handles the visit with this event. Equivalent of running a single loop iteration of the Visit
     * method of the algorithm.
     *
     *
     * We assume that the updateEvent call is followed immediately by a yield call. Therefore, we
     * check the top of a guiding trace and raise exception if the scheduling choice is blocking.
     *
     * @param event A [Event] that is used to update the execution graph.
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    fun updateEvent(event: Event) {
        LOGGER.debug("Received event: {}", event)
        if (areWeGuiding()) {
            handleGuidedEvent(event)
            return
        }

        // Need to assign the right location value to the event. Check aliases and update the event
        // location accordingly.
        if (event.location != null) {
            if (locationStore.containsAlias(event.location)) {
                event.location = locationStore.getAlias(event.location)
            } else {
                locationStore.addLocation(event.location)
            }
        }

        when (event.type) {
            Event.Type.END -> handleBot(event)
            Event.Type.READ -> handleRead(event)
            Event.Type.WRITE -> if (EventUtils.isLockReleaseWrite(event)) {
                handleLockReleaseWrite(event)
            } else {
                handleWrite(event)
            }

            Event.Type.READ_EX -> if (EventUtils.isLockAcquireRead(event)) {
                handleLockAcquireRead(event)
            } else {
                handleRead(event)
            }

            Event.Type.WRITE_EX -> if (EventUtils.isLockAcquireWrite(event)) {
                handleLockAcquireWrite(event)
            } else {
                handleWriteX(event)
            }

            Event.Type.NOOP -> {
                if (areWeGuiding()) {
                    return
                }
                handleNoop(event)
            }

            Event.Type.ASSUME -> handleAssume(event)
        }
        LOGGER.debug("Handled event: {}", event)
    }

    fun checkCoherencyEdges(): Boolean {
        return executionGraph!!.checkCoherencyEdges()
    }

    /**
     * Handles the NOOP event. This is a special case where we do not need to update the execution
     * graph.
     *
     * @param event The NOOP event.
     */
    fun handleNoop(event: Event) {
        if (EventUtils.isLockAcquired(event)) {
            handleLockAcquired(event)
            return
        }
        val eventNode = executionGraph!!.addEvent(event)
        // Maintain total order among thread start events
        if (EventUtils.isThreadStart(event)) {
            executionGraph!!.trackThreadCreates(eventNode)
            if (event.taskId != 0L) { // Skip the main thread
                executionGraph!!.trackThreadStarts(eventNode)
            }
        } else if (EventUtils.isThreadJoin(event)) {
            executionGraph!!.trackThreadJoins(eventNode)
            executionGraph!!.trackThreadJoinCompletion(eventNode)
        }
    }

    /**
     * Initializes the iteration. This method is called at the beginning of each iteration of the
     * algorithm.
     *
     * @param iteration The iteration number.
     */
    fun initIteration(iteration: Int, report: JmcModelCheckerReport?) {
        // Check if we are guiding the execution and construct the task schedule accordingly!
        if (iteration == 0) {
            LOGGER.debug("Initializing iteration")
            return
        }

        if (executionGraph!!.isBlocked) {
            logBlockedGraph()
        }

        // Check if the exploration stack is empty. If so, we are done with the exploration.
        if (explorationStack.isEmpty) {
            LOGGER.debug("Exploration stack is empty. We are done with the exploration.")
            // We have reached the end of the exploration stack.
            // We should not be guiding the execution
            throw HaltCheckerException.Companion.ok()
        }

        // Clear location aliases. By this point, we have replaced all the locations in the
        // execution graph with the latest ones.
        // TODO: need to check this properly
        locationStore.clearAliases()

        // We are guiding
        isGuiding = true

        LOGGER.debug("Initializing the {}th iteration", iteration)
        findNextExplorationChoice()
    }

    /**
     * Checks if we are guiding the execution.
     */
    fun areWeGuiding(): Boolean {
        return isGuiding && guidingTaskSchedule != null && !guidingTaskSchedule!!.isEmpty()
    }

    private fun findNextExplorationChoice() {
        if (explorationStack.isEmpty) {
            // This must not happen. We should have handled this in the resetIteration method.
            throw RuntimeException( // TODO : We need to define a better exception for this
                // case.
                "Exploration stack is empty. We should have handled this in the resetIteration method."
            )
        }

        // The main loop of the procedure
        var nextGraphSchedule: MutableList<ExecutionGraphNode?>? = ArrayList()
        while (nextGraphSchedule!!.isEmpty()) {
            if (explorationStack.isEmpty) {
                // We have reached the end of the exploration stack.
                throw HaltCheckerException.Companion.ok()
            }

            // Get the next exploration choice from the exploration stack.
            val item = explorationStack.pop()
            logUpdateGraphId(item)
            // Read the size of the exploration stack
            val stackSize = explorationStack.totalSize()
            // Check if the item is a backward revisit.
            if (item!!.isBackwardRevisit) { // TODO : Is any backward revisit type allowed? or only
                // BWR?
                processBWR(item)
                continue
            }

            // Handle the forward revisit
            val newGraph = item.graph
            if (newGraph == null) {
                // It is not possible for an item to have a null graph. This must be a bug in the
                // exploration stack.
                throw RuntimeException( // TODO : We need to define a better exception for this
                    // case.
                    "The exploration stack item has a null graph. This must be a bug in the exploration stack."
                )
            } else {
                executionGraph = newGraph
            }

            nextGraphSchedule = when (item.type) {
                ExplorationStack.ItemType.FRW -> processFRW(
                    item
                )

                ExplorationStack.ItemType.FWW -> processFWW(item)
                ExplorationStack.ItemType.FLW -> processFLW(item)
                ExplorationStack.ItemType.CONT -> processCont(item)
                else -> throw RuntimeException(
                    "The exploration stack item has an invalid type. This must be a bug in the exploration stack."
                )
            }

            if (nextGraphSchedule!!.isEmpty()) {
                if (EventUtils.isLockAcquireRead(item.event1.event)) {
                    val newStackSize = explorationStack.totalSize()
                    if (newStackSize == stackSize) {
                        LOGGER.debug(
                            "The forward revisit of lock acquire read resulted in an inconsistent graph. Continuing to next item."
                        )
                        logInconsistentGraph()
                        item.graph.isConsistent = false
                    }
                } else {
                    LOGGER.debug("The revisit resulted in an inconsistent graph. Continuing to next item.")
                    logInconsistentGraph()
                    item.graph.isConsistent = false
                }
            }
        }

        LOGGER.debug("Found the SC graph")
        checkGraphSchedule(nextGraphSchedule)

        //executionGraph.printGraph();

        // The SC graph is found. We need to set the guiding task schedule.
        // TODO : To increase efficiency, we can use the topological sort which
        guidingTaskSchedule = ArrayDeque<SchedulingChoiceWrapper>(
            ExecutionGraph.Companion.getTaskSchedule(
                nextGraphSchedule
            )
        )
        printGuidingTaskSchedule()
    }

    private fun checkGraphSchedule(graphSchedule: List<ExecutionGraphNode>?) {
        // Print
        if (graphSchedule == null || graphSchedule.isEmpty()) {
            LOGGER.debug("Graph schedule is empty")
            return
        }
        val sb = StringBuilder()
        sb.append("Graph schedule: ")
        for (node in graphSchedule) {
            sb.append(node.event.key().toString())
                .append(" - ")
                .append(node.event.toVerboseString())
                .append("\n")
        }
        LOGGER.debug(sb.toString())

        // Check if the graph schedule is consistent
        val completedTasks: MutableSet<Long?> = HashSet()
        for (node in graphSchedule) {
            if (EventUtils.isThreadFinish(node.event)) {
                completedTasks.add(node.event.taskId)
                continue
            }
            val taskId = node.event.taskId
            if (completedTasks.contains(taskId)) {
                throw HaltCheckerException.Companion.error(
                    ("The graph schedule is inconsistent. Task "
                            + taskId
                            + " has already completed but it appears again in the schedule.")
                )
            }
        }
    }

    /**
     * Prints the current guiding task schedule to the debug log. This is useful for debugging
     * purposes to see the order of tasks in the guiding schedule.
     */
    private fun printGuidingTaskSchedule() {
        if (guidingTaskSchedule == null) {
            LOGGER.debug("Guiding task schedule is null")
            return
        }
        val sb = StringBuilder()
        sb.append("Guiding task schedule:")
        for (choice in guidingTaskSchedule!!) {
            sb.append(choice.choice.taskId).append(" - ")
        }
        LOGGER.debug(sb.toString())
    }

    fun processBWR(item: ExplorationStack.Item) {
        val write = item.event1
        val restrictedGraph = item.graph

        val alternativeWrites = restrictedGraph!!.getCoherentPlacings(
            write!!
        )

        logNewBranchs()
        if (!alternativeWrites.isEmpty()) {
            for (i in alternativeWrites.indices.reversed()) {
                val newItem: ExplorationStack.Item = ExplorationStack.Item.Companion.forwardWW(
                    write, alternativeWrites[i], restrictedGraph
                )
                explorationStack.push(newItem)
                logNewChild(newItem)
            }
        }

        val forwardLW: ExplorationStack.Item = ExplorationStack.Item.Companion.forwardLW(write, restrictedGraph)
        for (additionalEvent in item.additionalEventsToProcess) {
            forwardLW.addAdditionalEvent(additionalEvent)
        }
        explorationStack.push(forwardLW)
        logLastChild(forwardLW)
    }

    private fun processFRW(item: ExplorationStack.Item): MutableList<ExecutionGraphNode?>? {
        // Forward revisit of w -> r
        val read = item.event1
        val write = item.event2

        LOGGER.debug("Processing forward revisit of w {} -> r {}", write!!.key(), read!!.key())

        executionGraph!!.changeReadsFrom(read, write)
        executionGraph!!.restrict(read)
        executionGraph!!.recomputeVectorClocks()

        for (additionalEvent in item.additionalEventsToProcess) {
            processAdditionalEvent(additionalEvent)
        }

        // The following is an optimization to avoid doing unnecessary consistency checks. If the read event is
        // a lock acquire read, we know that the graph is not consistent because the resulted graph has two
        // lock acquire reads reading from the same lock write.
        if (EventUtils.isLockAcquireRead(item.event1.event)) {
            LOGGER.debug("Skipping consistency check for lock acquire read forward revisit")
            return ArrayList()
        }
        return executionGraph!!.checkConsistencyAndTopologicallySort()
    }

    private fun processAdditionalEvent(event: Event) {
        when (event.type) {
            Event.Type.READ_EX -> {
                // The case of backward revisit of a lock acquire write to a lock acquire read
                // We would've removed the revisited read and are adding it again
                if (EventUtils.isLockAcquireRead(event)) {
                    handleLockAcquireRead(event)
                }
            }

            Event.Type.WRITE_EX -> {
                // The case when a lock acquire read wants to read from a different lock
                // write (init, or lock release). Here we add the lock acquire write to the
                // graph explicitly.
                if (EventUtils.isLockAcquireWrite(event)) {
                    handleLockAcquireWrite(event)
                }
            }
        }
    }

    private fun processFWW(item: ExplorationStack.Item): MutableList<ExecutionGraphNode?>? {
        // Forward revisit of w -> w (alternative coherence placing)
        val write1 = item.event1
        val write2 = item.event2

        LOGGER.debug("Processing forward revisit of w {} -> w {}", write1!!.key(), write2!!.key())

        executionGraph!!.swapCoherency(write1, write2)
        executionGraph!!.restrict(write1)
        return executionGraph!!.checkConsistencyAndTopologicallySort()
    }

    fun processFLW(item: ExplorationStack.Item): MutableList<ExecutionGraphNode?>? {
        // Forward revisit of w -> lw (max-co)
        val w = item.event1

        LOGGER.debug("Processing forward revisit of w {} -> lw", w!!.key())
        // set the co
        executionGraph!!.trackCoherency(w)
        executionGraph!!.restrict(w)

        val additionalEvents = item.additionalEventsToProcess
        if (additionalEvents.size > 1) {
            throw HaltCheckerException.Companion.error(
                "The forward revisit item has more than one additional event"
            )
        }
        for (additionalEvent in additionalEvents) {
            processAdditionalEvent(additionalEvent)
        }

        return executionGraph!!.checkConsistencyAndTopologicallySort()
    }

    // This method must not be called during the Trust model checking procedure.
    // This will be used for cases like estimation where we are not following the DFS exploration order strictly.
    private fun processCont(item: ExplorationStack.Item?): MutableList<ExecutionGraphNode?>? {
        // Just continue the exploration with the current graph
        return executionGraph!!.checkConsistencyAndTopologicallySort()
    }

    /**
     * Cleans up the execution graph and the task schedule. This method is called at the end of the
     * exploration.
     */
    fun teardown(report: JmcModelCheckerReport) {
        // Clean up the execution graph and the task schedule.
        logLastGraphSize()
        executionGraph!!.clear()
        explorationStack.clear()
        locationStore.clearAliases()
        locationStore.clear()
        reportInconsistentGraphLogs()
        reportBlockedGraphLogs()
        report.blockedIterations = Math.toIntExact(numOfBlockedGraphs)
    }

    val schedulableTasks: List<Long?>
        get() =// Get from execution graph
            executionGraph.getUnblockedTasks().stream().map<Long?> { l: Int? -> l }
                .toList()

    private fun handleError(event: Event) {
        // Error events, halt the current execution.
        if (event.type == Event.Type.ERROR) {
            val message = event.getAttribute<String>("message")
            throw HaltExecutionException.Companion.error(message)
        }
    }

    private fun handleBot(event: Event) {
        // End of the execution
        // No-op for now
        throw HaltExecutionException.Companion.ok()
    }

    private fun handleRead(event: Event) {
        if (areWeGuiding()) {
            return
        }
        val read = executionGraph!!.addEvent(event)
        val coMaxWrite = executionGraph!!.getCoMax(event.location)

        // TODO: If coMaxWrite is init (reading from a possibly uninitialized location). Write
        //      warning if flag is set
        // if (coMaxWrite.getEvent().isInit()) {
        //
        // }

        // Need to handle lock acquire reads
        // If the read is reading from a write of a lock acquire then we need to add a lock await
        // label after the read to block the thread.
        if (coMaxWrite!!.happensBefore(read)) {
            // TODO :: For debugging
            LOGGER.debug("Read is before the coMaxWrite")
            LOGGER.debug("The coMaxWrite is " + coMaxWrite.event)
            // Easy case. No concurrent write to revisit. [Note that this is an optimization for
            // sequential consistency model. If we are exploring relaxed memory models in the
            // future,
            // we need to remove this optimization.]
            executionGraph!!.setReadsFrom(read, coMaxWrite)
            return
        }
        val alternativeWrites = executionGraph!!.getAlternativeWrites(read)

        // Set the reads-from relation
        executionGraph!!.setReadsFrom(read, coMaxWrite)

        if (alternativeWrites.isEmpty()) {
            LOGGER.debug("No alternative writes to revisit")
            // No alternative writes to revisit.
            return
        }

        logNewBranchs()

        // We have alternative writes to revisit.
        for (i in alternativeWrites.indices.reversed()) {
            val newItem: ExplorationStack.Item =
                ExplorationStack.Item.Companion.forwardRW(
                    read, alternativeWrites[i], this.executionGraph
                )
            explorationStack.push(newItem)
            logNewChild(newItem)
        }
        logConCurrChild()
        logUpdateGraphIdWithLastGraph()
    }

    private fun handleWrite(event: Event) {
        if (areWeGuiding()) {
            return
        }

        // Add the write event to the execution graph
        val write = executionGraph!!.addEvent(event)

        /** Check for (w->w) coherent forward revisits *  */
        val concurrentWrites = executionGraph!!.getCoherentPlacings(write)

        var hasForwardRevisits = false

        if (!concurrentWrites.isEmpty()) {
            LOGGER.debug("Found concurrent writes to revisit")

            hasForwardRevisits = true
            logNewBranchs()
            // We have concurrent writes to revisit.
            // If flag is set, write race warning
            for (i in concurrentWrites.indices.reversed()) {
                val newItem: ExplorationStack.Item =
                    ExplorationStack.Item.Companion.forwardWW(
                        write, concurrentWrites[i], executionGraph
                    )
                explorationStack.push(newItem)
                logNewChild(newItem)
            }
        } else {
            LOGGER.debug("No concurrent writes to revisit")
        }

        /** Check for (w->r) backward revisits *  */
        // Find potential reads that need to be revisited
        // TODO :: I'm not sure the way `getPotentialReads` method is ordering the reads is correct.
        val potentialReads = executionGraph!!.getPotentialReads(write)
        if (potentialReads.isEmpty()) {
            LOGGER.debug("No potential reads to revisit")
            // After batching the forward revisits, since there is no backward revisit, we need to
            // continue the exploration by adding the recently added write as the CO max.
            executionGraph!!.trackCoherency(write)
            if (hasForwardRevisits) {
                logConCurrChild()
                logUpdateGraphIdWithLastGraph()
            }
            return
        }
        LOGGER.debug("Found potential reads to revisit")

        var revisitViews =
            potentialReads.stream().map { r: ExecutionGraphNode? ->
                executionGraph!!.revisitView(
                    write,
                    r!!
                )
            }.toList()

        revisitViews =
            revisitViews.stream().filter { obj: BackwardRevisitView -> obj.isMaximalExtension }.toList()

        var hasBackwardRevisits = false
        if (!revisitViews.isEmpty()) {
            hasBackwardRevisits = true
            if (!hasForwardRevisits) {
                logNewBranchs()
            }
        }

        for (i in revisitViews.indices.reversed()) {
            val newItem: ExplorationStack.Item =
                ExplorationStack.Item.Companion.backwardRevisit(
                    revisitViews[i].write,
                    revisitViews[i].restrictedGraph
                )
            explorationStack.push(newItem)
            logNewChild(newItem)
        }

        // After batching the backward and forward revisits, we need to continue the exploration by
        // adding the recently
        // added write as the CO max.
        executionGraph!!.trackCoherency(write)
        if (hasBackwardRevisits || hasForwardRevisits) {
            logConCurrChild()
            logUpdateGraphIdWithLastGraph()
        }
    }

    private fun handleWriteX(event: Event) {
        if (areWeGuiding()) {
            return
        }
        // Add the write event to the execution graph
        val write = executionGraph!!.addEvent(event)
        executionGraph!!.trackCoherency(write)

        // There will not be any (w->w) forward revisits for exclusive writes.
        // Because all exclusive writes to the same location are totally ordered by the
        // happens-before

        // Check for (w->r) backward revisits
        // Find potential reads that need to be revisited
        // Additionally, we need to add a blocking label to the writes of the corresponding revisit
        // reads.
        // The only reason there are alternative reads to consider is due to two reads
        // reading from the same exclusive write which is inconsistent but the one-step
        // inconsistency is needed to ensure we explore the alternative ordering.
        val potentialReads = executionGraph!!.getPotentialReads(write)
        if (potentialReads.isEmpty()) {
            return
        }
        var revisitViews =
            potentialReads.stream().map { r: ExecutionGraphNode? ->
                executionGraph!!.revisitView(
                    write,
                    r!!
                )
            }.toList()

        revisitViews =
            revisitViews.stream().filter { obj: BackwardRevisitView -> obj.isMaximalExtension }.toList()

        for (revisit in revisitViews) {
            // Block the tasks of the reads that need to be revisited
            executionGraph!!.addBlockingLabel(revisit.read.event.taskId)
            explorationStack.push(
                ExplorationStack.Item.Companion.backwardRevisit(
                    revisit.write, revisit.restrictedGraph
                )
            )
        }
    }

    private fun handleLockAcquireRead(event: Event) {
        if (areWeGuiding()) {
            return
        }

        val coMaxWrite = executionGraph!!.getCoMax(event.location)
        if (EventUtils.isLockAcquireWrite(coMaxWrite.event)) {
            // Then we block the task and delay the acquiring of the lock
            executionGraph!!.blockTaskForLock(event)
            return
        }

        val read = executionGraph!!.addEvent(event)
        if (coMaxWrite!!.happensBefore(read)) {
            LOGGER.debug("No alternative lock acquires to revisit")
            executionGraph!!.setReadsFrom(read, coMaxWrite)
            return
        }

        // Find alternative lock reads to revisit
        val alternativeWrites = executionGraph!!.getAlternativeLockWrites(read)
        executionGraph!!.setReadsFrom(read, coMaxWrite)
        if (alternativeWrites.isEmpty()) {
            LOGGER.debug("No alternative lock acquires to revisit")
            return
        }

        logNewBranchs()

        for (i in alternativeWrites.indices.reversed()) {
            val altWrite = alternativeWrites[i]
            LOGGER.debug("Adding revisit to alternative lock acquire write: {}", altWrite!!.key())
            val item: ExplorationStack.Item =
                ExplorationStack.Item.Companion.forwardRW(read, altWrite, executionGraph)
            val additionalWrite =
                Event(
                    read.event.taskId,
                    read.event.location,
                    Event.Type.WRITE_EX
                )
            additionalWrite.setAttribute("lock_acquire", true)
            item.addAdditionalEvent(additionalWrite)
            explorationStack.push(item)
            logNewChild(item)
        }
        logConCurrChild()
        logUpdateGraphIdWithLastGraph()
    }

    // Takes a parameter ExecutionGraph only to handle the
    // Additional event case
    private fun handleLockAcquireWrite(event: Event) {
        if (areWeGuiding()) {
            return
        }

        if (executionGraph!!.isTaskBlocked(event.taskId)) {
            // We cannot get the lock
            // Therefore, we skip the write event
            return
        }

        val write = executionGraph!!.addEvent(event)
        executionGraph!!.acquireLock(event.location, event.taskId)

        val alternateLockReads = executionGraph!!.getAlternativeLockReads(write)
        if (!alternateLockReads.isEmpty()) {
            var revisitViews =
                alternateLockReads.stream()
                    .map { r: ExecutionGraphNode? -> executionGraph!!.revisitView(write, r!!) }
                    .toList()
            revisitViews =
                revisitViews.stream().filter { obj: BackwardRevisitView -> obj.isMaximalExtension }.toList()

            var visitedConsistentBWR = false

            for (i in revisitViews.indices.reversed()) {
                val item: ExplorationStack.Item =
                    ExplorationStack.Item.Companion.backwardRevisit(
                        revisitViews[i].write,
                        revisitViews[i].restrictedGraph
                    )
                item.addAdditionalEvent(revisitViews[i].additionalEvent())
                if (item.graph.isRdxInconsistent(item.event1)) {
                    if (!visitedConsistentBWR) {
                        logNewBranchs()
                        visitedConsistentBWR = true
                    }
                    explorationStack.push(item)
                    logLastChild(item)
                }
            }
        }
        executionGraph!!.trackCoherency(write)
    }

    private fun handleLockReleaseWrite(event: Event) {
        if (areWeGuiding()) {
            return
        }

        val write = executionGraph!!.addEvent(event)
        executionGraph!!.unblockAllTasksForLock(event.location)
        executionGraph!!.trackCoherency(write)
    }

    fun handleLockAcquired(event: Event) {
        if (areWeGuiding()) {
            return
        }
        if (!executionGraph!!.waitingForLock(event.location, event.taskId)) {
            // We have acquired the lock
            return
        }

        val readEvent = Event(event.taskId, event.location, Event.Type.READ_EX)
        readEvent.setAttribute("lock_acquire", true)
        handleLockAcquireRead(readEvent)

        val writeEvent = Event(event.taskId, event.location, Event.Type.WRITE_EX)
        writeEvent.setAttribute("lock_acquire", true)
        handleLockAcquireWrite(writeEvent)
    }

    private fun handleAssume(event: Event) {
        executionGraph!!.addEvent(event)
    }

    /**
     *
     */
    fun logStackState() {
        explorationStack.logStackState()
    }

    /**
     * Writes the execution graph to a file.
     *
     * @param filePath The path to the file to write the execution graph to.
     */
    fun writeExecutionGraphToFile(filePath: String) {
        //        if (!executionGraph.checkExtensiveConsistency()) {
        //            throw HaltExecutionException.error(
        //                    "The execution graph is not consistent at the end of the iteration.");
        //        }

        val executionGraphJson = executionGraph!!.toJsonString()
        FileUtil.unsafeStoreToFile(filePath, executionGraphJson!!)
    }

    fun clear() {
        this.guidingTaskSchedule = null
        this.isGuiding = false
        executionGraph!!.clear()
        explorationStack.clear()
        locationStore.clear()
        executionGraph!!.addEvent(Event.Companion.init())
    }

    val isStackEmpty: Boolean
        get() = explorationStack.isEmpty

    private fun logNewBranchs() {
        if (tLogger == null) {
            return
        }
        tLogger.appendNewBranchs(executionGraph!!.size())
    }

    private fun logNewChild(item: ExplorationStack.Item) {
        if (tLogger == null) {
            return
        }
        tLogger.appendNewChild(item)
    }

    private fun logLastChild(item: ExplorationStack.Item) {
        if (tLogger == null) {
            return
        }
        tLogger.appendLastChild(item)
    }

    private fun logConCurrChild() {
        if (tLogger == null) {
            return
        }
        tLogger.appendContinueCurrent()
    }

    private fun logEndofChilds() {
        if (tLogger == null) {
            return
        }
        tLogger.appendNextLine()
    }

    private fun logUpdateGraphId(nextItem: ExplorationStack.Item?) {
        if (tLogger == null) {
            return
        }
        tLogger.updateLoggerGraphId(nextItem, executionGraph!!.size())
    }

    private fun logUpdateGraphIdWithLastGraph() {
        if (tLogger == null) {
            return
        }
        tLogger.updateLoggerGraphIdWithLastGraph(executionGraph!!.size())
    }

    private fun logInconsistentGraph() {
        if (tLogger == null) {
            return
        }
        tLogger.addInconsistentGraph()
    }

    private fun logBlockedGraph() {
        numOfBlockedGraphs++
        if (tLogger == null) {
            return
        }
        tLogger.addBlockedGraph()
    }

    private fun logLastGraphSize() {
        if (tLogger == null) {
            return
        }
        tLogger.addLeafSize(executionGraph!!.size())
    }

    val treeLog: StringBuilder?
        get() {
            if (tLogger == null) {
                return null
            }
            return tLogger.logger
        }

    val inconsistentGraphLog: StringBuilder?
        get() {
            if (tLogger == null) {
                return null
            }
            return tLogger.inConsistentGraphLogger
        }

    val blockedGraphLog: StringBuilder?
        get() {
            if (tLogger == null) {
                return null
            }
            return tLogger.blockedGraphLogger
        }

    val leafSizeLog: StringBuilder?
        get() {
            if (tLogger == null) {
                return null
            }
            return tLogger.leafSizeLogger
        }

    fun reportInconsistentGraphLogs() {
        if (tLogger == null) {
            return
        }
        LOGGER.info("Number of Inconsistent Graph: " + tLogger.numOfInconsistentGraphs)
    }

    fun reportBlockedGraphLogs() {
        if (tLogger == null) {
            return
        }
        LOGGER.info("Number of Blocked Graph:" + tLogger.numOfBlockedGraphs)
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(Algo::class.java)
    }
}
