package eval

import java.io.File

/**
 * Result of matching a Java file to its converted Kotlin counterpart.
 */
data class ConversionResult(
    val sourceFile: File,
    val kotlinSource: String?,
    val success: Boolean,
    val error: String?
)
