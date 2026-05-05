package org.mpi_sws.jmc.strategies.estimation.trust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.strategies.estimation.MetaTreeEstimator
import org.mpi_sws.jmc.strategies.trust.Algo
import org.mpi_sws.jmc.strategies.trust.EventUtils
import org.mpi_sws.jmc.strategies.trust.ExplorationStack
import java.util.random.RandomGenerator
import java.util.random.RandomGeneratorFactory

open class TrustEstimator : MetaTreeEstimator {
    override var isReExecutionNeeded: Boolean = false
        protected set

    /**
     * @return
     */
    override var expectedValue: Int = 1
        protected set

    val treeLogger: StringBuilder = StringBuilder().append("\$Iteration_0").append(System.lineSeparator())

    protected var graphId: Long = 1L

    protected var graphCounter: Long = 1L

    protected var nextGraphIds: MutableMap<ExplorationStack.Item?, Long> = HashMap()

    /**
     * @param alg
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateTree(alg: Algo) {
        if (alg.areWeGuiding()) {
            return
        }

        val stack = alg.explorationStack
        val items: MutableList<ExplorationStack.Item> = getAllItems(
            stack!!
        )
        if (items.isEmpty()) {
            return
        }

        // Create an item for continuing the current execution
        val currItem: ExplorationStack.Item = ExplorationStack.Item.Companion.continueCurrent()
        items.add(currItem)
        updateTreeLogger(items)
        val size = items.size
        expectedValue = (expectedValue * (size))
        val nextItem = pickNextOption(items, stack, alg)
        updateGraphId(nextItem)
        handleNextItem(nextItem, stack, alg)
        nextGraphIds.clear()
    }

    private fun updateTreeLogger(items: List<ExplorationStack.Item?>) {
        treeLogger.append(graphId).append(" -> ")
        for (i in items.indices) {
            graphCounter++
            nextGraphIds[items[i]] = graphCounter
            treeLogger.append(graphCounter).append("(")
                .append(if (items[i]!!.isBackwardRevisit) "B" else "F")
                .append(")")
            if (i < items.size - 1) {
                treeLogger.append(", ")
            }
        }
        treeLogger.append(System.lineSeparator())
    }

    private fun getAllItems(stack: ExplorationStack): MutableList<ExplorationStack.Item?> {
        val items: MutableList<ExplorationStack.Item?> = ArrayList()
        while (!stack.isEmpty) {
            items.add(stack.pop())
        }
        return items
    }

    protected open fun pickNextOption(
        items: List<ExplorationStack.Item>,
        stack: ExplorationStack?,
        alg: Algo?
    ): ExplorationStack.Item {
        // Pick a random int value between 0 and items.size() (both inclusive)
        val randomIndex = RandomGeneratorFactory.of<RandomGenerator>("Xoshiro256PlusPlus").create().nextInt(items.size)
        return items[randomIndex]
    }

    protected fun handleNextItem(item: ExplorationStack.Item, stack: ExplorationStack, alg: Algo) {
        if (item.isContinueCurrent) {
            // Do nothing, this means we are continuing the current execution
            return
        }

        if (item.isBackwardRevisit) {
            // If the next item is a backward revisit, we need to process it and then update the tree again
            // if the stack size is greater than 1, otherwise we need to re-execute
            LOGGER.debug("Revisiting a backward choice")
            alg.processBWR(item)
            if (alg.explorationStack.size() > 1) {
                updateTreeBW(alg)
            } else {
                val topItem = alg.explorationStack.peek()
                updateTreeLogger(java.util.List.of(topItem))
                updateGraphId(topItem)
                isReExecutionNeeded = true
            }
        } else {
            updateLoggerForRdx(item)
            stack.push(item)
            isReExecutionNeeded = true
        }
    }

    private fun updateLoggerForRdx(item: ExplorationStack.Item) {
        if (!EventUtils.isLockAcquireRead(item.event1.event)) {
            return
        }
        graphCounter++
        treeLogger.append(graphId).append(" -> ").append(graphCounter).append("(B)").append(System.lineSeparator())
        graphId = graphCounter
        graphCounter++
        treeLogger.append(graphId).append(" -> ").append(graphCounter).append("(F)").append(System.lineSeparator())
        graphId = graphCounter
    }

    private fun updateGraphId(item: ExplorationStack.Item?) {
        graphId = nextGraphIds[item]!!
    }

    @Throws(HaltTaskException::class, HaltExecutionException::class)
    private fun updateTreeBW(alg: Algo) {
        val stack = alg.explorationStack
        val items: List<ExplorationStack.Item?> = getAllItems(stack!!)
        if (items.isEmpty() || items.size < 2) {
            throw HaltExecutionException.Companion.error("The number of items in the stack is less than 2")
        }

        updateTreeLogger(items)
        val size = items.size
        expectedValue = (expectedValue * (size))
        pickNextOptionBW(items, stack, alg)
    }

    private fun pickNextOptionBW(items: List<ExplorationStack.Item?>, stack: ExplorationStack, alg: Algo) {
        val randomIndex = RandomGeneratorFactory.of<RandomGenerator>("Xoshiro256PlusPlus").create().nextInt(items.size)
        val item = items[randomIndex]!!
        updateGraphId(item)
        if (item.type != ExplorationStack.ItemType.FLW) {
            // If the next item is not a FLW, we need to track coherency for the event1 of the item
            // Otherwise, the swapCoherency will break, since the FLW event is not processed
            alg.executionGraph = item.graph
            alg.processFLW(item)
        }
        stack.push(item)
        isReExecutionNeeded = true
    }

    override fun resetReExecutionFlag() {
        isReExecutionNeeded = false
    }

    /**
     *
     */
    override fun reset() {
        expectedValue = 1
        resetReExecutionFlag()
        treeLogger.setLength(0)
        graphCounter = 1L
        graphId = 1L
        nextGraphIds.clear()
    }

    fun resetTreeLogger() {
        treeLogger.setLength(0)
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            TrustEstimator::class.java
        )
    }
}
