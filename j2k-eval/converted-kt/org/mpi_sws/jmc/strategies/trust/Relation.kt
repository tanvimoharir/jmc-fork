package org.mpi_sws.jmc.strategies.trust

enum class Relation(private val key: String) {
    ReadsFrom("readsFrom"),
    Coherency("coherency"),
    ProgramOrder("programOrder"),
    ThreadCreation("threadCreation"),
    ThreadStart("threadStart"),
    ThreadJoin("threadJoin"),
    ThreadJoinCompletion("threadJoinCompletion"),

    // FR = rf^-1;co
    FR("fr"),
    ;

    fun key(): String {
        return key
    }

    override fun toString(): String {
        return key
    }
}
