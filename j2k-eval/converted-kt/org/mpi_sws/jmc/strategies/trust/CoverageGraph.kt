package org.mpi_sws.jmc.strategies.trust

class CoverageGraph {
    private val po: MutableMap<Long?, MutableList<Event>> = HashMap()
    private val rf: MutableMap<Event, Event> = HashMap()
    private val coKey: MutableMap<Int?, Event> = HashMap()
    private val co: MutableMap<Event?, MutableList<Event>> = HashMap()
    private val tc: List<Event>? = null
    private val ts: Map<Event, Event>? = null
    private val tj: Map<Event, Event>? = null

    fun addPo(e: Event) {
        if (po.containsKey(e.taskId)) {
            po[e.taskId]!!.add(e)
        } else {
            val list: MutableList<Event> = ArrayList()
            list.add(e)
            po[e.taskId] = list
        }
    }

    fun addCo(w: Event) {
        if (coKey.containsKey(w.location)) {
            val key = coKey[w.location]
            co[key]!!.add(w)
        } else {
            coKey[w.location] = w
            val list: MutableList<Event> = ArrayList()
            list.add(w)
            co[w] = list
        }
    }

    fun addRf(r: Event) {
        val w = getMaxCo(r)
        rf[r] = w
    }

    private fun getMaxCo(e: Event): Event {
        if (!coKey.containsKey(e.location)) {
            throw RuntimeException("Reading from an empty coKey for event: $e")
        }
        val key = coKey[e.location]
        //System.out.println(e + " location " + e.getLocation());
        val max = co[key]!![co[key]!!.size - 1] ?: throw RuntimeException("Max co is null")
        return max
    }

    fun printGraph() {
        println("PO:")
        for ((key, value) in po) {
            print(" ID $key: ")
            for (event in value) {
                print("$event -> ")
            }
            println()
        }

        println("RF:")
        for ((key, value) in rf) {
            println("$key -> $value")
        }

        println("CO:")
        for ((_, value) in co) {
            for (event in value) {
                print("$event -> ")
            }
            println()
        }
    }

    override fun toString(): String {
        val graph = arrayOf("")
        graph[0] += "PO:\n"
        for ((key, value) in po) {
            graph[0] += " ID $key: "
            for (event in value) {
                graph[0] += (event.type.toString() + event.key.toString() + " -> ")
            }
            graph[0] += "\n"
        }
        graph[0] += "RF:\n"
        // Sort rf by key. Each key is an event. compare the event by its getKey().
        rf.entries.stream()
            .sorted(java.util.Map.Entry.comparingByKey { e1: Event, e2: Event ->
                e1.key.compareTo(
                    e2.key
                )
            })
            .forEach { entry: Map.Entry<Event, Event> ->
                graph[0] += (entry.key.type.toString() + entry.key.key.toString() + " -> " + entry.value.type + entry.value.key.toString() + "\n")
            }
        graph[0] += "CO:\n"
        // Sort co by key. Each key is an event. compare the event by its getKey().
        co.entries.stream()
            .sorted(java.util.Map.Entry.comparingByKey<Event?, List<Event>> { e1: Event?, e2: Event? ->
                e1.getKey().compareTo(e2.getKey())
            })
            .forEach { entry: Map.Entry<Event?, List<Event>> ->
                for (event in entry.value) {
                    graph[0] += (event.type.toString() + event.key.toString() + " -> ")
                }
                graph[0] += "\n"
            }
        return graph[0]
    }
}
