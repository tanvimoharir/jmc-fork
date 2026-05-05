package org.mpi_sws.jmc.strategies.trust

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.mpi_sws.jmc.util.LamportVectorClock
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Represents a node in the execution graph.
 */
class ExecutionGraphNode {
    /**
     * Returns the [Event] that this node represents.
     *
     * @return The event that this node represents.
     */
    // The event that this node represents.
    val event: Event

    // The attributes of this node.
    private var attributes: MutableMap<String, Any>

    // Forward edges from this node. Grouped by edge relation.
    val edges: MutableMap<Relation, MutableList<Event.Key>>

    // Back edges to this node. Grouped by edge relation.
    val backEdges: MutableMap<Relation?, MutableList<Event.Key>>

    /**
     * Returns the vector clock of this node.
     *
     * @return The vector clock of this node.
     */
    /**
     * Updates the vector clock of this node.
     *
     * @param newClock The new vector clock of this node.
     */
    // The vector clock of this node (Used to track only PORF relation)
    var vectorClock: LamportVectorClock

    /**
     * Constructs a new [ExecutionGraphNode] with the given event.
     *
     * @param event The [Event] that this node represents.
     */
    constructor(event: Event, vectorClock: LamportVectorClock) {
        this.event = event
        this.attributes = HashMap()
        this.edges = EnumMap(
            Relation::class.java
        )
        this.backEdges = EnumMap(
            Relation::class.java
        )
        this.vectorClock =
            if (event.isInit)
                LamportVectorClock(0)
            else
                LamportVectorClock(vectorClock, event.taskId.toInt())
    }

    /**
     * Copy constructor.
     *
     * @param node The node to copy.
     */
    private constructor(node: ExecutionGraphNode) {
        this.event = node.event.clone()
        this.attributes = HashMap(node.attributes)
        this.edges = EnumMap(
            Relation::class.java
        )
        for ((key1, value) in node.edges) {
            val newEdges: MutableList<Event.Key> = ArrayList()
            for (key in value) {
                newEdges.add(key.clone())
            }
            edges[key1] = newEdges
        }
        this.backEdges = EnumMap(
            Relation::class.java
        )
        for ((key1, value) in node.backEdges) {
            val newBackEdges: MutableList<Event.Key> = ArrayList()
            for (key in value) {
                newBackEdges.add(key.clone())
            }
            backEdges[key1] = newBackEdges
        }
        this.vectorClock = LamportVectorClock(node.vectorClock.vector)
    }

    /**
     * Constructs a new [ExecutionGraphNode] copying the given node.
     */
    override fun clone(): ExecutionGraphNode {
        return ExecutionGraphNode(this)
    }

    fun key(): Event.Key {
        return event.key()
    }

    /**
     * Adds an edge to this node. The edge is directed from this node to the given node with the
     * given adjacency.
     *
     * @param to        The node to which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    fun addEdge(to: ExecutionGraphNode, adjacency: Relation) {
        if (!edges.containsKey(adjacency)) {
            edges[adjacency] = ArrayList()
        }
        edges[adjacency]!!.add(to.key())
        to.addBackEdge(this, adjacency)
    }

    /**
     * Adds a back edge to this node. The edge is directed from the given node to this node with the
     * given adjacency. The vector clock of this node is updated with the vector clock of the given
     * node (only if the relation is not CO).
     *
     * @param from      The node from which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    private fun addBackEdge(from: ExecutionGraphNode, adjacency: Relation) {
        if (adjacency != Relation.Coherency && adjacency != Relation.FR) {
            vectorClock.update(from.vectorClock)
        }
        if (!backEdges.containsKey(adjacency)) {
            backEdges[adjacency] = ArrayList()
        }
        backEdges[adjacency]!!.add(from.key())
    }

    /**
     * Removes the edge with the given adjacency from this node.
     *
     *
     * Note that removing an edge invalidates the vector clock of all descendants. The concern of
     * fixing the vector clocks is passed to the calling function.
     *
     * @param to        The node to which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    fun removeEdge(to: ExecutionGraphNode, adjacency: Relation) {
        if (!edges.containsKey(adjacency)) {
            return
        }
        edges[adjacency]!!.removeIf { key: Event.Key -> key == to.key() }
        to.removeBackEdge(this, adjacency)
    }

    /**
     * Removes the edge with the given relation from this node.
     *
     *
     * Leads to dandling references
     *
     * @param relation The relation of the edge.
     */
    fun removeEdge(relation: Relation) {
        edges.remove(relation)
        backEdges.remove(relation)
    }

    /*
     * Removes all edges to the given node.
     *
     * <p> There might be dangling references to this node from other nodes that are not handled.
     * Additionally, the vector clock is invalidated unless the edge is CO.
     *
     * @param to The node to which the edges are directed.
     */
    fun removeAllEdgesTo(to: Event.Key) {
        for (adjacency in edges.keys) {
            edges[adjacency]!!.removeIf { key: Event.Key -> key == to }
        }
        // remove adjacency if no more edges
        edges.entries.removeIf { entry: Map.Entry<Relation, List<Event.Key>> -> entry.value.isEmpty() }
    }

    fun removeEdgeTo(to: Event.Key?, adjacency: Relation) {
        if (!edges.containsKey(adjacency)) {
            return
        }
        edges[adjacency]!!.removeIf { key: Event.Key -> key == to }
    }

    /**
     * Removes all edges from the given node.
     *
     * @param from The node from which the edges are directed.
     */
    fun removeAllEdgesFrom(from: Event.Key) {
        for (adjacency in backEdges.keys) {
            backEdges[adjacency]!!.removeIf { key: Event.Key -> key == from }
        }
        // remove adjacency if no more edges
        backEdges.entries.removeIf { entry: Map.Entry<Relation?, List<Event.Key>> -> entry.value.isEmpty() }
    }

    /**
     * Removes the predecessor with the given adjacency from this node.
     *
     * @param from      The node from which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    fun removePredecessor(from: ExecutionGraphNode, adjacency: Relation) {
        // Wraps around removeBackEdge because the external user does not know about back-edges.
        // Only successors and predecessors.
        removeBackEdge(from, adjacency)
    }

    fun removePredecessor(from: ExecutionGraphNode) {
        for (adjacency in backEdges.keys) {
            backEdges[adjacency]!!.removeIf { key: Event.Key -> key == from.key() }
        }
    }

    /**
     * Removes all the predecessors with the given adjacency from this node.
     *
     * @param adjacency The adjacency of the edges.
     */
    fun removeAllPredecessors(adjacency: Relation?) {
        if (!backEdges.containsKey(adjacency)) {
            return
        }
        backEdges[adjacency]!!.clear()
    }

    /**
     * Removes the back edge with the given adjacency from this node.
     *
     * @param from      The node from which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    private fun removeBackEdge(from: ExecutionGraphNode, adjacency: Relation) {
        if (!backEdges.containsKey(adjacency)) {
            return
        }
        backEdges[adjacency]!!.removeIf { key: Event.Key -> key == from.key() }
    }

    val allSuccessors: Map<Relation, MutableList<Event.Key>>
        /**
         * Returns the edges of this node.
         *
         * @return The edges of this node.
         */
        get() = edges

    /**
     * Returns the neighbours of this node that have the given adjacency.
     *
     * @param adjacency The adjacency of the neighbours.
     * @return The neighbours of this node that have the given adjacency.
     */
    fun getSuccessors(adjacency: Relation): List<Event.Key> {
        return edges.getOrDefault(adjacency, ArrayList())
    }

    /**
     * Returns the edges of this node.
     *
     * @return The edges of this node.
     */
    fun getEdges(): Map<Relation, MutableList<Event.Key>> {
        return edges
    }

    /**
     * Returns whether this node has an edge to the given node with the given adjacency.
     *
     * @param to        The node to which the edge is directed.
     * @param adjacency The adjacency of the edge.
     * @return Whether this node has an edge to the given node with the given adjacency.
     */
    fun hasEdge(to: Event.Key, adjacency: Relation): Boolean {
        if (!edges.containsKey(adjacency)) {
            return false
        }
        return edges[adjacency]!!.contains(to)
    }

    val allPredecessors: Map<Relation?, MutableList<Event.Key>>
        /**
         * Returns all the predecessors of this node.
         *
         * @return The predecessors of this node.
         */
        get() = backEdges

    /**
     * Returns the back edges of this node.
     *
     * @return The back edges of this node.
     */
    fun getPredecessors(adjacency: Relation?): List<Event.Key> {
        return backEdges[adjacency]!!
    }

    val inDegree: Int
        /**
         * Returns the number of incoming edges of this node.
         *
         * @return The number of incoming edges of this node.
         */
        get() {
            val inDegree = AtomicInteger()
            for (relation in allRelations) {
                if (!backEdges.containsKey(relation)) {
                    continue
                }
                backEdges[relation]!!
                    .forEach(Consumer { k: Event.Key? -> inDegree.getAndIncrement() })
            }
            return inDegree.get()
        }

    fun forEachPredecessor(iterator: BiConsumer<Relation, MutableList<Event.Key>>) {
        for (rel in allRelations) {
            if (!backEdges.containsKey(rel)) {
                continue
            }
            val predecessors = backEdges[rel]!!
            if (predecessors.isEmpty()) {
                continue
            }
            iterator.accept(rel, predecessors)
        }
    }

    fun forEachSuccessor(iterator: BiConsumer<Relation?, MutableList<Event.Key>>) {
        for (rel in allRelations) {
            if (!edges.containsKey(rel)) {
                continue
            }
            val successors = edges[rel]!!
            if (successors.isEmpty()) {
                continue
            }
            iterator.accept(rel, successors)
        }
    }

    /**
     * Check if `this` node is happens-before (_porf_ relation) the `other` node.
     *
     *
     * Determined using vector clocks
     *
     * @param other The other node to compare against.
     * @return Returns true if the `this` is happens-before `other`
     */
    fun happensBefore(other: ExecutionGraphNode): Boolean {
        return vectorClock.happensBefore(other.vectorClock)
    }

    /**
     * Updates the attributes of this node.
     *
     * @param attributes The new attributes of this node.
     */
    fun setAttributes(attributes: MutableMap<String, Any>) {
        this.attributes = attributes
    }

    /**
     * Adds an attribute to this node.
     *
     * @param key   The key of the attribute.
     * @param value The value of the attribute.
     */
    fun addAttribute(key: String, value: Any) {
        attributes[key] = value
    }

    /**
     * Returns the attribute with the given key.
     *
     * @param key The key of the attribute.
     * @param <T> The type of the attribute.
     * @return The attribute with the given key.
    </T> */
    fun <T> getAttribute(key: String): T? {
        return attributes[key] as T?
    }

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("event", event.toJson())
        val attributesObject = JsonObject()
        for ((key, value) in attributes) {
            json.addProperty(key, value.toString())
        }
        json.add("attributes", attributesObject)
        val edgesObject = JsonObject()
        for ((key1, value) in edges) {
            val edgeArray = JsonArray()
            for (key in value) {
                edgeArray.add(key.toString())
            }
            edgesObject.add(key1.toString(), edgeArray)
        }
        json.add("edges", edgesObject)
        return json
    }

    fun toJsonIgnoreLocation(): JsonElement {
        val json = JsonObject()
        json.add("event", event.toJsonIgnoreLocation())
        // Sort the attributes by key
        /*JsonObject attributesObject = new JsonObject();
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> attributesObject.addProperty(entry.getKey(), entry.getValue().toString()));
        json.add("attributes", attributesObject);*/
        val edgesObject = JsonObject()
        /*Relation[] relations = Relation.values();*/
        val relations =
            Arrays.stream<Relation>(Relation.entries.toTypedArray())
                .sorted(Comparator.comparingInt<Relation> { obj: Relation -> obj.ordinal })
                .toArray<Relation> { _Dummy_.__Array__() }
        for (i in relations.indices) {
            val relation = relations[i]
            if (!edges.containsKey(relation)) {
                continue
            }
            val successors: List<Event.Key> = edges[relation]!!
            if (successors.isEmpty()) {
                continue
            }
            val edgeArray = JsonArray()
            successors.sort(Comparator<Event.Key> { key: Event.Key? -> compareTo(key) })
            for (key in successors) {
                edgeArray.add(key.toString())
            }
            edgesObject.add(relation.toString(), edgeArray)
        }
        json.add("edges", edgesObject)
        return json
    }

    val poPredecessor: Event.Key?
        /**
         * Returns the predecessor of this node in the program order.
         *
         * @return The predecessor of this node in the program order.
         */
        get() {
            if (!backEdges.containsKey(Relation.ProgramOrder)) {
                return null
            }
            val predecessors: List<Event.Key> =
                backEdges[Relation.ProgramOrder]!!
            if (predecessors.size != 1) {
                return null
            }
            return predecessors[0]
        }

    override fun equals(obj: Any?): Boolean {
        if (obj !is ExecutionGraphNode) {
            return false
        }
        if (this === obj) {
            return true
        }
        return this.event == obj.event
    }

    fun equalsEdges(other: ExecutionGraphNode): Boolean {
        if (this === other) {
            return true
        }
        for ((key1, value) in edges) {
            if (value.isEmpty()) {
                if (!other.edges.containsKey(key1)) {
                    continue
                }
                val otherEdges: List<Event.Key> = other.edges[key1]!!
                if (!otherEdges.isEmpty()) {
                    return false
                }
            }
            if (!other.edges.containsKey(key1)) {
                return false
            }
            if (value.size != other.edges[key1]!!.size) {
                return false
            }
            for (key in value) {
                if (!other.edges[key1]!!.contains(key)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Checks if this node has a predecessor with the given key and relation.
     *
     * @param key      The key of the predecessor.
     * @param relation The relation of the predecessor.
     * @return True if this node has a predecessor with the given key and relation, false otherwise.
     */
    fun hasPredecessor(key: Event.Key?, relation: Relation?): Boolean {
        if (!backEdges.containsKey(relation)) {
            return false
        }
        return backEdges[relation]!!.contains(key!!)
    }

    /**
     * @return
     */
    override fun toString(): String {
        return event.toString()
    }

    companion object {
        private val allRelations = Relation.entries.toTypedArray()
    }
}
