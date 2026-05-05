package org.mpi_sws.jmc.api.symbolic

/**
 * Enum representing different types of operations and relations
 */
enum class InstructionType {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    NOT,
    AND,
    OR,
    IMPLIES,
    IFF,
    XOR,
    EQ,
    NEQ,
    LT,
    GT,
    LEQ,
    GEQ,
    DISTINCT,
    ATOM,
}
