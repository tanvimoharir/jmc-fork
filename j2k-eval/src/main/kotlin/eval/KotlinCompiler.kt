package eval

import java.io.File

/**
 * Attempts to compile converted Kotlin files using the Kotlin compiler (kotlinc).
 *
 * This is a key quality signal: if the J2K converter produces Kotlin that
 * doesn't compile, that's a clear failure. We track:
 * - Overall compilation success/failure
 * - Individual error messages (to identify patterns in what breaks)
 *
 * We invoke kotlinc as a subprocess rather than using the compiler API directly,
 * because the compiler API from kotlin-compiler-embeddable can have classloader
 * conflicts when used in-process. The subprocess approach is also what you'd
 * naturally do in CI.
 */
object KotlinCompiler {

    data class CompilationResult(
        val success: Boolean,
        val errors: List<String>,
        val fileCount: Int
    )

    /**
     * Compile all .kt files in the given directory.
     *
     * Uses kotlinc from the system PATH. In the GitHub Action, we ensure kotlinc
     * is available via the Kotlin compiler installation step.
     */
    fun compile(sourceDir: File): CompilationResult {
        val ktFiles = sourceDir.walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

        if (ktFiles.isEmpty()) {
            return CompilationResult(success = true, errors = emptyList(), fileCount = 0)
        }

        // We compile all files together (not individually) because Kotlin files
        // may reference each other. This mirrors how a real project would compile.
        val kotlincPath = findKotlinc()

        val processBuilder = ProcessBuilder(
            listOf(kotlincPath) + ktFiles.map { it.absolutePath }
        ).apply {
            redirectErrorStream(true)
            directory(sourceDir.parentFile)
        }

        return try {
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val errors = output.lines()
                .filter { it.contains("error:", ignoreCase = true) }
                .map { it.trim() }

            CompilationResult(
                success = exitCode == 0,
                errors = errors,
                fileCount = ktFiles.size
            )
        } catch (e: Exception) {
            CompilationResult(
                success = false,
                errors = listOf("Failed to run kotlinc: ${e.message}"),
                fileCount = ktFiles.size
            )
        }
    }

    /**
     * Find the kotlinc executable.
     * Checks: KOTLINC_HOME env var, then PATH, then falls back to bare name.
     */
    private fun findKotlinc(): String {
        // Check environment variable first
        System.getenv("KOTLINC_HOME")?.let { home ->
            val kotlinc = File(home, "bin/kotlinc")
            if (kotlinc.exists()) return kotlinc.absolutePath
        }

        // Check if kotlinc is on PATH
        try {
            val whichCmd = if (System.getProperty("os.name").lowercase().contains("win")) {
                listOf("where", "kotlinc")
            } else {
                listOf("which", "kotlinc")
            }
            val process = ProcessBuilder(whichCmd)
                .redirectErrorStream(true)
                .start()
            val path = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && path.isNotEmpty()) return path
        } catch (_: Exception) { }

        // Fallback: assume it's on PATH
        return "kotlinc"
    }
}
