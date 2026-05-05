package org.mpi_sws.jmc.strategies.trust

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent

class ExecutionGraphSimulator {
    var executionGraph: ExecutionGraph
        private set

    var coverageGraph: CoverageGraph
        private set

    init {
        this.executionGraph = ExecutionGraph()
        this.coverageGraph = CoverageGraph()
        executionGraph.addEvent(Event.Companion.init())
    }

    // Do not use this method outside the scop of `MeasureGraphCoverageStrategy` class
    fun updateEvent(event: JmcRuntimeEvent) {
        val trustEvents = EventFactory.fromRuntimeEvent(event)
        // Update the execution graph based on the event
        if (event.type == JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT) {
            return
        }
        for (trustEvent in trustEvents) {
            updateEvent(trustEvent)
        }
    }

    // Using this method to update the graph with trust event
    fun updateEvent(event: Event) {
        // Add PO
        coverageGraph.addPo(event)
        when (event.type) {
            Event.Type.END -> handleBot(event)
            Event.Type.READ -> handleRead(event)
            Event.Type.WRITE -> handleWrite(event)
            Event.Type.READ_EX -> handleReadEx(event)
            Event.Type.WRITE_EX -> handleWriteEx(event)
            Event.Type.NOOP -> handleNoop(event)
        }
    }

    fun reset() {
        this.executionGraph = ExecutionGraph()
        this.coverageGraph = CoverageGraph()
        executionGraph.addEvent(Event.Companion.init())
    }

    fun handleBot(event: Event?) {
        // SKIP
    }

    fun handleRead(event: Event) {
        val read = executionGraph.addEvent(event)
        val coMaxWrite = executionGraph.getCoMax(event.location)
        executionGraph.setReadsFrom(read, coMaxWrite!!)
        // Track the rf
        coverageGraph.addRf(event)
    }

    fun handleWrite(event: Event) {
        val write = executionGraph.addEvent(event)
        executionGraph.trackCoherency(write)
        // Track the CO (MO)
        coverageGraph.addCo(event)
    }

    fun handleReadEx(event: Event) {
        val write = executionGraph.addEvent(event)
        val coMaxRead = executionGraph.getCoMax(event.location)
        executionGraph.setReadsFrom(write, coMaxRead!!)
        coverageGraph.addRf(event)
    }

    fun handleWriteEx(event: Event) {
        val writeNode = executionGraph.addEvent(event)
        executionGraph.trackCoherency(writeNode)
        // Track the CO (MO)
        coverageGraph.addCo(event)
    }

    fun handleLockAwait(event: Event?) {
        // SKIP
    }

    fun handleNoop(event: Event) {
        val eventNode = executionGraph.addEvent(event)
        if (EventUtils.isThreadStart(event)) {
            // Track thread creation coherency
            executionGraph.trackThreadCreates(eventNode)
            if (event.taskId != 0L) { // Skip the main thread
                // Track thread start dependencies
                executionGraph.trackThreadStarts(eventNode)
            }
        } else if (EventUtils.isThreadJoin(event)) {
            executionGraph.trackThreadJoins(eventNode)
        }
    }

    val allPoMaxEvents: List<Event?>
        get() {
            val poMaxEvents = executionGraph.allPoMaxNode
            val events: MutableList<Event?> =
                ArrayList()
            for (node in poMaxEvents) {
                events.add(node.event)
            }
            return events
        }

    val allNonNoopPoMaxEvents: List<Event?>
        get() {
            val poMaxEvents = executionGraph.allPoMaxNode
            val events: MutableList<Event?> =
                ArrayList()
            for (node in poMaxEvents) {
                if (!EventUtils.isNoop(node.event) || EventUtils.isThreadFinish(node.event)) {
                    events.add(node.event)
                }
            }
            return events
        }

    fun isStartMaxWithStarter(event: Event): Boolean {
        return executionGraph.isStartMaxWithStarter(event)
    }

    fun isCoMax(event: Event): Boolean {
        return executionGraph.isCoMax(event)
    }

    fun isRfMax(event: Event): Boolean {
        return executionGraph.isRfMax(event)
    }

    fun isFrMax(event: Event): Boolean {
        return executionGraph.isFrMax(event)
    }

    fun isTcMax(event: Event): Boolean {
        return executionGraph.isTcMax(event)
    }

    fun isStMax(event: Event): Boolean {
        return executionGraph.isStMax(event)
    }

    fun isJtMax(event: Event): Boolean {
        return executionGraph.isJtMax(event)
    }

    fun getStarterTid(tid: Long): Long {
        val firstNode = executionGraph.getFirstEventOfTask(tid)
        require(EventUtils.isThreadStart(firstNode.event)) { "The first event of the task is not a START event" }
        return EventUtils.getStartedBy(firstNode.event)!!
    }

    fun getLastEventOfTask(tid: Long): Event? {
        val lastNode = executionGraph.getLastNodeOfTask(tid)
        requireNotNull(lastNode) { "No event found for task: $tid" }
        return lastNode.event
    }
}
