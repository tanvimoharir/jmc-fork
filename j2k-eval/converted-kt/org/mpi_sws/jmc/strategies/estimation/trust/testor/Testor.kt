package org.mpi_sws.jmc.strategies.estimation.trust.testor

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.estimation.MetaTreeEstimator
import org.mpi_sws.jmc.strategies.trust.Algo
import org.mpi_sws.jmc.strategies.trust.ExecutionGraphNode
import org.mpi_sws.jmc.strategies.trust.ExplorationStack
import org.mpi_sws.jmc.strategies.trust.NoSuchEventException
import java.util.*

class Testor @JvmOverloads constructor(private val budget: Int = 2) : MetaTreeEstimator {
    /**
     * @return
     */
    override var isReExecutionNeeded: Boolean = false
        protected set
    override var expectedValue: Float = 0.0f
    protected var prod: Float = 1.0f
    private val current: MutableList<ExplorationStack.Item> = ArrayList()
    private val currentLeaves: MutableMap<ExplorationStack.Item?, Boolean> = HashMap()
    private val next: MutableList<ExplorationStack.Item?> = ArrayList()
    private var currentItem: ExplorationStack.Item?

    init {
        val dummy: ExplorationStack.Item = ExplorationStack.Item.Companion.continueCurrent()
        currentLeaves[dummy] = true
        currentItem = dummy
    }

    /**
     * @param alg
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateTree(alg: Algo) {
        // If we are guiding, we should not update the tree or the frontier.
        if (alg.areWeGuiding()) {
            return
        }

        // Fetch the reachable nodes from the current node in the current frontier
        var items: MutableList<ExplorationStack.Item?>? = retrieveItems(alg)
        if (items!!.isEmpty()) {
            return
        }

        // Update the leaves map for the current frontier.
        updateLeaves()

        // Add a node representing the sc-max child which will be not enumerated by the algorithm but
        // will be used for estimation.
        appendCurrentItem(items, alg)

        // Accumulate the reachable nodes into the next frontier
        updateNext(items)
        items = null // Help GC

        updateStack(alg)
    }

    @Throws(HaltTaskException::class, HaltExecutionException::class)
    private fun updateTreeBW(alg: Algo) {
        var items: MutableList<ExplorationStack.Item?>? = retrieveItems(alg)
        if (items!!.isEmpty() || items.size < 1) {
            throw HaltExecutionException.Companion.error("The number of items in the stack is less than 2")
        }

        // Update the leaves map for the current frontier.
        updateLeaves()

        // Add a node representing the FLW which we handled internally in order to fix the co-edge.
        appendCurrentItem(items, alg)

        // Accumulate the reachable nodes into the next frontier
        updateNext(items)
        items = null // Help GC

        updateStack(alg)
    }

    private fun updateLeaves() {
        if (currentItem != null && currentLeaves.containsKey(currentItem)) {
            currentLeaves[currentItem] = false
        }
    }

    private fun retrieveItems(alg: Algo): MutableList<ExplorationStack.Item?> {
        val stack = alg.explorationStack
        val items: MutableList<ExplorationStack.Item?> = ArrayList()
        while (!stack.isEmpty) {
            items.add(stack!!.pop())
        }
        return items
    }

    private fun appendCurrentItem(items: MutableList<ExplorationStack.Item?>, alg: Algo?): Boolean {
        if (items.isEmpty() || alg == null || alg.executionGraph == null) {
            return false
        }
        val currItem: ExplorationStack.Item = ExplorationStack.Item.Companion.continueCurrent(alg.executionGraph)
        items.add(currItem)
        return true
    }

    private fun updateNext(items: MutableList<ExplorationStack.Item?>): Boolean {
        var items = items
        if (items.isEmpty() || next == null) {
            return false
        }
        next.addAll(items)
        items.clear()
        items = null // Help GC
        return true
    }

    private fun updateFrontier(): Boolean {
        if (next.isEmpty() || !current.isEmpty()) {
            return false
        }

        var candidate: MutableList<ExplorationStack.Item>? = randomSelection(next)
        next.clear()

        var cloned: MutableList<ExplorationStack.Item>? = makeClone(
            candidate!!
        )
        candidate.clear()
        candidate = null // Help GC

        current.addAll(cloned!!)
        currentLeaves.clear()
        for (item in cloned) {
            currentLeaves[item] = true
        }
        cloned.clear()
        cloned = null // Help GC
        return true
    }

    private fun updateProd() {
        val d = next.size.toFloat() / countFrontier().toFloat()
        prod = prod * d
    }

    private fun updateEstimation() {
        expectedValue = expectedValue + (prod * (countLeaves().toFloat() / countFrontier().toFloat()))
    }

    private fun countFrontier(): Int {
        return currentLeaves.size
    }

    private fun countLeaves(): Int {
        var count = 0
        if (currentLeaves.isEmpty()) {
            return count
        }

        /*for (Boolean isLeaf : currentLeaves.values()) {
            if (isLeaf) {
                count++;
            }
        }*/
        for ((key, value) in currentLeaves) {
            if (value && key.getGraph() != null &&
                key.getGraph().isConsistent
            ) {
                count++
            }
        }
        return count
    }

    private val nextFrontier: ExplorationStack.Item?
        get() {
            if (current.isEmpty()) {
                return null
            }
            return current.removeAt(0)
        }

    private fun exploreNextFrontier(alg: Algo): Boolean {
        val nextItem = nextFrontier ?: return false

        return if (nextItem.isBackwardRevisit) {
            handleBWR(alg, nextItem)
        } else {
            handleNonBWR(alg, nextItem)
        }
    }

    private fun handleBWR(alg: Algo, nextItem: ExplorationStack.Item): Boolean {
        alg.processBWR(nextItem)
        if (alg.explorationStack.size() > 1) {
            fixCoEdge(alg)
            currentItem = nextItem
            updateTreeBW(alg)
            return true
        } else {
            val top = alg.explorationStack.pop()
            currentLeaves.remove(nextItem)
            currentLeaves[top] = true
            return handleNonBWR(alg, top!!)
        }
    }

    private fun handleNonBWR(alg: Algo, nextItem: ExplorationStack.Item): Boolean {
        val stack = alg.explorationStack
        stack!!.push(nextItem)
        currentItem = nextItem
        isReExecutionNeeded = true
        return true
    }

    private fun fixCoEdge(alg: Algo) {
        var top = alg.explorationStack.pop()
        if (!top!!.isLastWriteRevisit) {
            throw HaltCheckerException.Companion.error("The top item in the stack is not a last write revisit.")
        }
        alg.executionGraph = top.graph
        alg.processFLW(top)

        top = null // Help GC
    }

    // This method will pick at most `budget` number of items from the given list of items. If the number of items is less
    // than or equal to the budget, it will return all the items. Otherwise, it will randomly select budget number of
    // distinct items from the list and return them. (Fisher-Yates partial shuffle)
    private fun randomSelection(items: List<ExplorationStack.Item?>): MutableList<ExplorationStack.Item?> {
        var items = items
        if (items.size <= budget) {
            return ArrayList(items)
        }

        val copy: List<ExplorationStack.Item?> = ArrayList(items)
        items = null // Help GC

        for (i in 0..<budget) {
            val j = i + (Math.random() * (copy.size - i)).toInt()
            // Swap elements at i and j
            Collections.swap(copy, i, j)
        }

        return copy.subList(0, budget)
    }

    private fun makeClone(items: List<ExplorationStack.Item>): MutableList<ExplorationStack.Item> {
        val clones: MutableList<ExplorationStack.Item> = ArrayList()
        for (item in items) {
            // If an item is not a BWR, we need to update the item's graph with a cloned graph
            if (!item.isBackwardRevisit) {
                val cln = item.graph.clone()
                var e1: ExecutionGraphNode? = null
                if (item.event1 != null) {
                    try {
                        e1 = cln.getEventNode(item.event1.key())
                    } catch (e: NoSuchEventException) {
                        throw HaltCheckerException.Companion.error("The read or write event is not found.")
                    }
                }
                var e2: ExecutionGraphNode? = null
                if (item.event2 != null) {
                    try {
                        e2 = cln.getEventNode(item.event2.key())
                    } catch (e: NoSuchEventException) {
                        throw HaltCheckerException.Companion.error("The read or write event is not found.")
                    }
                }
                val clone: ExplorationStack.Item = ExplorationStack.Item.Companion.makeItem(item.type, e1, e2, cln)
                for (e in item.additionalEventsToProcess) {
                    clone.addAdditionalEvent(e)
                }
                clones.add(clone)
            } else {
                clones.add(item)
            }
        }
        return clones
    }

    fun updateStack(alg: Algo) {
        // Try to explore the next frontier. If we cannot explore the next frontier,
        // it means we have explored all the reachable nodes
        if (!exploreNextFrontier(alg)) {
            // We have explored all nodes in the current frontier, we can update the frontier with the next frontier
            // and continue the exploration.
            updateEstimation()
            updateProd()
            updateFrontier()
            exploreNextFrontier(alg)
        }
    }

    val isDone: Boolean
        get() = current.isEmpty() && next.isEmpty()

    /**
     *
     */
    override fun resetReExecutionFlag() {
        isReExecutionNeeded = false
    }

    /**
     * @return
     */
    override fun getExpectedValue(): Int {
        return (realExpectedValue).toInt()
    }

    val realExpectedValue: Float
        get() {
            updateEstimation()
            return expectedValue
        }

    /**
     *
     */
    override fun reset() {
        expectedValue = 0.0f
        prod = 1.0f
        current.clear()
        currentLeaves.clear()
        val dummy: ExplorationStack.Item = ExplorationStack.Item.Companion.continueCurrent()
        currentLeaves[dummy] = true
        currentItem = dummy
        next.clear()
        resetReExecutionFlag()
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(Testor::class.java)
    }
}
