package org.mpi_sws.jmc.util

import java.security.MessageDigest

/**
 * Utility class for string operations.
 *
 *
 * This class provides methods for common string operations, such as hashing.
 */
object StringUtil {
    /**
     * Generates a SHA-256 hash of the given input string.
     *
     * @param input the input string to hash
     * @return the SHA-256 hash of the input string as a hexadecimal string
     * @throws Exception if an error occurs during hashing
     */
    @Throws(Exception::class)
    fun sha256Hash(input: String): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(input.toByteArray())
            val hexString = StringBuilder()
            for (b in hash) {
                hexString.append(String.format("%02x", b))
            }
            return hexString.toString()
        } catch (e: Exception) {
            throw Exception(e)
        }
    }
}
