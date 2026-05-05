package org.mpi_sws.jmc.strategies.trust

/**
 * A single class to store references to locations and to keep track of location aliases.
 *
 *
 * Location objects are shared objects used in the program. Whenever a new iteration of the model
 * checker runs, we will replace the Location object associated with the old hashcode with the new
 * one and add an alias that points the new hash code to the old one.
 *
 *
 * LocationStore is accessed when events are accessed.
 *
 *
 * The lifetime of a location store is that of the algorithm.
 */
class LocationStore {
    // A map of location hash codes to locations
    private val locations: MutableSet<Int?> = HashSet()

    // When a location is replaced, a mapping is added to aliases
    private val aliases: MutableMap<Int?, Int?>

    /** Constructs a new location store.  */
    init {
        locations.add(ThreadLocation)
        aliases = HashMap()
    }

    /** Add a location to the store.  */
    fun addLocation(location: Int?) {
        locations.add(location)
    }

    /** Remove all locations from the store.  */
    fun clear() {
        locations.clear()
    }

    /** Remove all aliases from the store.  */
    fun clearAliases() {
        aliases.clear()
    }

    /** Returns if a location is contained in the store.  */
    fun contains(hashCode: Int?): Boolean {
        return locations.contains(hashCode) || aliases.containsKey(hashCode)
    }

    /** Returns if an alias is contained in the store.  */
    fun containsAlias(hashCode: Int?): Boolean {
        return aliases.containsKey(hashCode)
    }

    /** Adds an alias between the two location codes.  */
    fun addAlias(oldL: Int?, newL: Int?) {
        locations.add(oldL)
        aliases[newL] = oldL
    }

    /** Returns the location alias for the given hash code.  */
    fun getAlias(hashCode: Int?): Int? {
        return aliases[hashCode]
    }

    companion object {
        // A special Location to represent thread events. This is used to track total order between
        // thread start events
        // Essentially, thread starts are writes on the thread location
        var ThreadLocation: Int = "thread".hashCode()
    }
}
