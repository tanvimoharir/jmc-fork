package org.mpi_sws.jmc.strategies.trust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*

/**
 * The exploration stack used in the Trust algorithm. The stack is used to keep track of the forward
 * and backward revisits.
 *
 *
 * The stack is a list of inner stacks. Each inner stack is created for a backward revisit.
 */
class ExplorationStack {
    private val stack: MutableList<InnerStack>

    /**
     * Creates a new exploration stack.
     */
    init {
        this.stack = ArrayList()
    }

    /**
     * Pushes an item onto the stack. If the stack is empty, a new InnerStack is created and added
     * to the stack. If the item is a backward revisit, a new InnerStack is created and added to the
     * stack.
     *
     *
     * If the item contains a graph then the graph of the inner stack is updated with this graph.
     * The reasoning is that since it is a DFS based exploration, The updated graph will only change
     * the relations of later events.
     *
     * @param item The item to push onto the stack
     */
    fun push(item: Item) {
        LOGGER.debug("Adding item {} to stack", item.toString())
        if (stack.isEmpty()) {
            stack.add(InnerStack(item.graph))
        }
        if (item.type == ItemType.BRR || item.type == ItemType.BWR) {
            stack.add(InnerStack(null))
        }
        item.innerStackIndex = stack.size - 1
        stack[stack.size - 1].push(item)

        val g = item.graph
        if (g != null) {
            stack[stack.size - 1].graph = g
        }
    }

    private fun cleanStack() {
        var lastNonEmpty = stack.size - 1
        while (lastNonEmpty >= 0 && stack[lastNonEmpty].isEmpty) {
            lastNonEmpty--
        }
        if (lastNonEmpty < stack.size - 1) {
            stack.subList(lastNonEmpty + 1, stack.size).clear()
        }
    }

    /**
     * Pops an item from the stack. If the stack is empty, null is returned.
     *
     * @return The item popped from the stack
     */
    fun pop(): Item? {
        LOGGER.debug("Removing item {} from stack", peek())
        // Note that we clean before popping. This was when an inner stack is popped to empty any
        // pushes will still go to that stack.
        // This is helpful when we pop a BCK item and then push a FRW item.
        // TODO: maybe there is a bug. We should think more carefully about this.
        cleanStack()
        if (stack.isEmpty()) {
            return null
        }
        val innerStack = stack[stack.size - 1]
        return innerStack.pop()
    }

    /**
     * Peeks at the item at the top of the stack. If the stack is empty, null is returned.
     *
     * @return The item at the top of the stack
     */
    fun peek(): Item? {
        cleanStack()
        if (stack.isEmpty()) {
            return null
        }
        val innerStack = stack[stack.size - 1]
        return innerStack.peek()
    }

    /**
     * Gets the graph associated with the item.
     *
     * @param item The item
     * @return The graph associated with the item
     */
    fun getGraph(item: Item): ExecutionGraph? {
        return stack[item.innerStackIndex].graph
    }

    val isEmpty: Boolean
        /**
         * Checks if the stack is empty.
         *
         * @return True if the stack is empty, false otherwise
         */
        get() {
            cleanStack()
            return stack.isEmpty()
        }

    /**
     * Clears the stack.
     */
    fun clear() {
        stack.clear()
    }

    /**
     * Gets the size of the current inner stack.
     *
     * @return The size of the stack
     */
    fun size(): Int {
        return stack[0].size()
    }

    /**
     * Gets the total size of all inner stacks.
     *
     * @return The total size of the stack
     */
    fun totalSize(): Int {
        var total = 0
        for (innerStack in this.stack) {
            total += innerStack.size()
        }
        return total
    }

    /**
     * Logs the current state of the stack. This is a placeholder method for debugging purposes.
     */
    fun logStackState() {
        LOGGER.debug("Current stack state:")
        for (i in stack.indices) {
            val innerStack = stack[i]
            for (item in innerStack.items) {
                LOGGER.debug("Inner Stack {}: {}", i, item)
            }
        }
    }

    /**
     * Represents an item in the exploration stack.
     */
    class Item private constructor(
        /**
         * Gets the type of the item.
         *
         * @return The type of the item
         */
        // The type of the item
        val type: ItemType?,
        /**
         * Gets the first event of the item.
         *
         * @return The first event of the item
         */
        // The two events that are part of the item
        // In the case of a forward revisit of
        // - (w ->(rf) r), event1 is r and event2 is w
        // - (w1 ->(co) w2), event1 is w1 and event2 is w2
        // In the case of a backward revisit, event1 is the write event and event2 is null
        val event1: ExecutionGraphNode?,
        two: ExecutionGraphNode?,
        graph: ExecutionGraph?
    ) {
        /**
         * Gets the inner stack index of the item.
         *
         * @return The inner stack index
         */
        /**
         * Sets the inner stack index of the item.
         *
         * @param index The inner stack index
         */
        var innerStackIndex: Int = 0

        /**
         * Gets the second event of the item.
         *
         * @return The second event of the item
         */
        // TODO: Since they are graph nodes, we must use a better name
        val event2: ExecutionGraphNode?
        // TODO: Since they are graph nodes, we must use a better name

        private val additionalEventsToProcess: MutableList<Event?>

        /**
         * Gets the graph associated with the item.
         *
         * @return The graph associated with the item
         */
        // Graph is used only in the case of a backward revisit
        var graph: ExecutionGraph?

        init {
            this.event1 = event1
            this.event2 = two
            this.graph = graph
            this.additionalEventsToProcess = ArrayList()
        }

        fun addAdditionalEvent(event: Event?) {
            additionalEventsToProcess.add(event)
        }

        fun getAdditionalEventsToProcess(): List<Event?> {
            return this.additionalEventsToProcess
        }

        val isBackwardRevisit: Boolean
            /**
             * Checks if the item is a forward revisit.
             *
             * @return True if the item is a forward revisit, false otherwise
             */
            get() = (this.type == ItemType.BRR || this.type == ItemType.BWR) && this.graph != null

        val isLastWriteRevisit: Boolean
            get() = this.type == ItemType.FLW

        val isContinueCurrent: Boolean
            get() = this.type == ItemType.CONT

        override fun toString(): String {
            // return a string representation of the item type and the events. If the event2 is
            // null,
            // then just return the event1.
            return (type
                .toString() + "("
                    + (if (this.event1 != null) ":" + event1.event else "")
                    + (if (this.event2 != null) ":" + event2.event else "")
                    + ")")
        }

        companion object {
            // Do not use this method to create items. It is only used for a temporary workaround in the testor when we
            // need to create an item without knowing the type of the item.
            fun makeItem(
                type: ItemType?,
                one: ExecutionGraphNode?,
                two: ExecutionGraphNode?,
                graph: ExecutionGraph?
            ): Item {
                return Item(type, one, two, graph)
            }

            /**
             * Creates a forward revisit item for a read revisiting an alternative write.
             *
             * @param read  The read event
             * @param write The write event
             * @param graph The graph to be used in the case of a backward revisit
             * @return The created item
             */
            fun forwardRW(
                read: ExecutionGraphNode?, write: ExecutionGraphNode?, graph: ExecutionGraph?
            ): Item {
                return Item(ItemType.FRW, read, write, graph)
            }

            /**
             * Creates a forward revisit item for a write revisiting an alternative concurrent write.
             *
             * @param one   The first write event
             * @param two   The second write event
             * @param graph The graph to be used in the case of a backward revisit
             * @return The created item
             */
            fun forwardWW(
                one: ExecutionGraphNode?, two: ExecutionGraphNode?, graph: ExecutionGraph?
            ): Item {
                return Item(ItemType.FWW, one, two, graph)
            }

            fun forwardLW(one: ExecutionGraphNode?, graph: ExecutionGraph?): Item {
                return Item(ItemType.FLW, one, null, graph)
            }

            /**
             * Creates a backward revisit item for a write revisiting a read.
             *
             * @param one   The write event
             * @param graph The graph to be used in the case of a backward revisit
             * @return The created item
             */
            fun backwardRevisit(one: ExecutionGraphNode?, graph: ExecutionGraph?): Item {
                return Item(ItemType.BWR, one, null, graph)
            }

            fun continueCurrent(): Item {
                return Item(ItemType.CONT, null, null, null)
            }

            fun continueCurrent(graph: ExecutionGraph?): Item {
                return Item(ItemType.CONT, null, null, graph)
            }

            /**
             * Creates a backward revisit item for a lock read revisiting another lock read.
             *
             * @param one   The read event
             * @param two   The revisited read
             * @param graph The graph to be used in the case of a backward revisit
             * @return The created item
             */
            fun lockBackwardRevisit(
                one: ExecutionGraphNode?, two: ExecutionGraphNode?, graph: ExecutionGraph?
            ): Item {
                return Item(ItemType.BRR, one, two, graph)
            }
        }
    }

    /**
     * Represents the item type in the exploration stack.
     */
    enum class ItemType {
        // Forward revisit of read reading an alternative write
        FRW,

        // Forward revisit of write swapping with an alternative write
        FWW,

        // Forward revisit of write putting it in the maximal position of the coherent order
        FLW,

        // Backward revisit of write reading an alternative read
        BWR,

        // Backward revisit of read revisting an alternative read's read-from
        BRR,

        // Continue the current execution without any change
        CONT
    }

    /**
     * Represents an inner stack in the exploration stack.
     */
    private class InnerStack(var graph: ExecutionGraph?) {
        val items: ArrayDeque<Item> =
            ArrayDeque()

        fun push(item: Item) {
            items.push(item)
        }

        fun pop(): Item {
            return items.pop()
        }

        fun peek(): Item? {
            return items.peek()
        }

        val isEmpty: Boolean
            get() = items.isEmpty()

        fun size(): Int {
            return items.size
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            ExplorationStack::class.java
        )
    }
}
