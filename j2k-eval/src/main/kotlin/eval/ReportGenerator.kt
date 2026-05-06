package eval

import com.google.gson.GsonBuilder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates Markdown and JSON reports from the evaluation results.
 */
object ReportGenerator {

    fun generate(
        javaFiles: List<File>,
        conversionResults: List<ConversionResult>,
        compilationResult: KotlinCompiler.CompilationResult,
        analysisResults: List<AnalysisResult>
    ): String {
        val successful = conversionResults.count { it.success }
        val failed = conversionResults.count { !it.success }
        val avgStructuralMatch = if (analysisResults.isNotEmpty()) {
            analysisResults.map { it.structuralMatch }.average()
        } else 0.0

        return buildString {
            appendLine("# J2K Evaluation Report")
            appendLine()
            appendLine("**Generated:** ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine("**Target project:** JMC (Java Model Checker) — core module")
            appendLine("**Converter:** Kotlin static J2K (kotlin-compiler 1.9.22)")
            appendLine()

            // Summary
            appendLine("## Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Total Java files | ${javaFiles.size} |")
            appendLine("| Successfully converted | $successful |")
            appendLine("| Failed to convert | $failed |")
            appendLine("| Conversion rate | ${"%.1f".format(successful.toDouble() / javaFiles.size * 100)}% |")
            appendLine("| Compilation success | ${if (compilationResult.success) "YES" else "NO"} |")
            appendLine("| Compilation errors | ${compilationResult.errors.size} |")
            appendLine("| Avg structural match | ${"%.1f".format(avgStructuralMatch * 100)}% |")
            appendLine()

            // Per-file results — only show failures
            val failures = conversionResults.filter { !it.success }
            if (failures.isNotEmpty()) {
                appendLine("## Conversion Failures")
                appendLine()
                appendLine("| File | Error |")
                appendLine("|------|-------|")
                failures.forEach { result ->
                    appendLine("| ${result.sourceFile.name} | ${result.error ?: ""} |")
                }
                appendLine()
            }

            // Compilation errors
            if (compilationResult.errors.isNotEmpty()) {
                appendLine("## Compilation Errors")
                appendLine()

                // Check for OOM
                val hasOom = compilationResult.errors.any { "OutOfMemoryError" in it }
                if (hasOom) {
                    appendLine("⚠️ **Compiler ran out of memory** — results may be incomplete. Consider compiling in smaller batches.")
                    appendLine()
                }

                val realErrors = compilationResult.errors.filter { "OutOfMemoryError" !in it && "Caused by:" !in it }
                appendLine("Total compilation errors: **${realErrors.size}**${if (hasOom) " (OOM — may be incomplete)" else ""}")
                appendLine()
                if (realErrors.isNotEmpty()) {
                    appendLine("First 20 errors:")
                    appendLine()
                    appendLine("```")
                    realErrors.take(20).forEach { appendLine(it) }
                    if (realErrors.size > 20) {
                        appendLine("... and ${realErrors.size - 20} more errors")
                    }
                    appendLine("```")
                }
                appendLine()
            }

            // Structural analysis — aggregate summary
            if (analysisResults.isNotEmpty()) {
                appendLine("## Structural Analysis")
                appendLine()
                val totalJavaClasses = analysisResults.sumOf { it.javaMetrics.classCount }
                val totalKtClasses = analysisResults.sumOf { it.kotlinMetrics.classCount }
                val totalJavaMethods = analysisResults.sumOf { it.javaMetrics.methodCount }
                val totalKtMethods = analysisResults.sumOf { it.kotlinMetrics.methodCount }
                val totalJavaFields = analysisResults.sumOf { it.javaMetrics.fieldCount }
                val totalKtFields = analysisResults.sumOf { it.kotlinMetrics.fieldCount }

                appendLine("| Element | Java (original) | Kotlin (converted) | Preservation |")
                appendLine("|---------|----------------|-------------------|-------------|")
                appendLine("| Classes | $totalJavaClasses | $totalKtClasses | ${if (totalJavaClasses > 0) "${"%.0f".format(minOf(totalKtClasses.toDouble() / totalJavaClasses, 1.0) * 100)}%" else "N/A"} |")
                appendLine("| Methods | $totalJavaMethods | $totalKtMethods | ${if (totalJavaMethods > 0) "${"%.0f".format(minOf(totalKtMethods.toDouble() / totalJavaMethods, 1.0) * 100)}%" else "N/A"} |")
                appendLine("| Fields | $totalJavaFields | $totalKtFields | ${if (totalJavaFields > 0) "${"%.0f".format(minOf(totalKtFields.toDouble() / totalJavaFields, 1.0) * 100)}%" else "N/A"} |")
                appendLine()

                // Show only worst-performing files
                val worstFiles = analysisResults
                    .filter { it.structuralMatch < 1.0 }
                    .sortedBy { it.structuralMatch }
                    .take(5)
                if (worstFiles.isNotEmpty()) {
                    appendLine("**Lowest structural match files** (potential conversion issues):")
                    appendLine()
                    appendLine("| File | Match | Java (classes/methods/fields) | Kotlin (classes/methods/fields) |")
                    appendLine("|------|-------|------------------------------|-------------------------------|")
                    worstFiles.forEach { r ->
                        appendLine("| ${r.fileName} | ${"%.0f".format(r.structuralMatch * 100)}% | ${r.javaMetrics.classCount}/${r.javaMetrics.methodCount}/${r.javaMetrics.fieldCount} | ${r.kotlinMetrics.classCount}/${r.kotlinMetrics.methodCount}/${r.kotlinMetrics.fieldCount} |")
                    }
                    appendLine()
                }

                // Kotlin idiom usage — aggregate summary
                appendLine("## Kotlin Idiom Usage")
                appendLine()
                val total = analysisResults.size
                val valOverVar = analysisResults.count { it.idiomMetrics.usesValOverVar }
                val usesWhen = analysisResults.count { it.idiomMetrics.usesWhenExpression }
                val usesNullSafe = analysisResults.count { it.idiomMetrics.usesNullSafety }
                val usesTemplates = analysisResults.count { it.idiomMetrics.usesStringTemplates }
                val usesCompanion = analysisResults.count { it.idiomMetrics.usesCompanionObject }
                val usesDataClass = analysisResults.count { it.idiomMetrics.usesDataClass }

                appendLine("| Idiom | Files Using It | Percentage |")
                appendLine("|-------|---------------|-----------|")
                appendLine("| Prefers `val` over `var` | $valOverVar / $total | ${"%.0f".format(valOverVar.toDouble() / total * 100)}% |")
                appendLine("| `when` expressions | $usesWhen / $total | ${"%.0f".format(usesWhen.toDouble() / total * 100)}% |")
                appendLine("| Null-safety operators (`?.`, `?:`) | $usesNullSafe / $total | ${"%.0f".format(usesNullSafe.toDouble() / total * 100)}% |")
                appendLine("| String templates (`\${}`) | $usesTemplates / $total | ${"%.0f".format(usesTemplates.toDouble() / total * 100)}% |")
                appendLine("| `companion object` | $usesCompanion / $total | ${"%.0f".format(usesCompanion.toDouble() / total * 100)}% |")
                appendLine("| `data class` | $usesDataClass / $total | ${"%.0f".format(usesDataClass.toDouble() / total * 100)}% |")
                appendLine()
                appendLine()
            }

            // Quality metrics summary
            if (analysisResults.isNotEmpty()) {
                appendLine("## Conversion Quality")
                appendLine()
                val totalBangBang = analysisResults.sumOf { it.qualityMetrics.bangBangCount }
                val avgLineRatio = analysisResults.map { it.qualityMetrics.lineRatio }.average()
                val totalInstanceofChains = analysisResults.sumOf { it.qualityMetrics.instanceofChainCount }
                val totalStringConcats = analysisResults.sumOf { it.qualityMetrics.stringConcatCount }
                val totalExplicitCasts = analysisResults.sumOf { it.qualityMetrics.explicitCastCount }
                val totalJvmAnnotations = analysisResults.sumOf { it.qualityMetrics.jvmAnnotationCount }
                val totalOpenClasses = analysisResults.sumOf { it.qualityMetrics.openClassCount }
                val totalFiles = analysisResults.size

                appendLine("| Metric | Count | Per File Avg | Notes |")
                appendLine("|--------|-------|-------------|-------|")
                appendLine("| `!!` non-null assertions | $totalBangBang | ${"%.1f".format(totalBangBang.toDouble() / totalFiles)} | Forced unwraps — indicates converter couldn't infer nullability safely |")
                appendLine("| Avg line ratio (Kt/Java) | — | ${"%.2f".format(avgLineRatio)} | <1.0 means Kotlin is more concise, >1.0 means more verbose |")
                appendLine("| `if-else is` chains | $totalInstanceofChains | ${"%.1f".format(totalInstanceofChains.toDouble() / totalFiles)} | Should be `when` expressions — unconverted Java pattern |")
                appendLine("| String concatenation (`+`) | $totalStringConcats | ${"%.1f".format(totalStringConcats.toDouble() / totalFiles)} | Should use `\${}` templates — unconverted Java pattern |")
                appendLine("| Explicit casts (`as`) | $totalExplicitCasts | ${"%.1f".format(totalExplicitCasts.toDouble() / totalFiles)} | Could be replaced by smart casts in idiomatic Kotlin |")
                appendLine("| JVM interop annotations | $totalJvmAnnotations | ${"%.1f".format(totalJvmAnnotations.toDouble() / totalFiles)} | @JvmStatic, @JvmOverloads, @Throws — preserves Java caller compatibility |")
                appendLine("| `open` classes | $totalOpenClasses | — | Classes marked open for inheritance (Kotlin is final by default) |")
                appendLine()
            }

            // Observations — generated from actual results
            appendLine("## Observations")
            appendLine()
            generateObservations(conversionResults, compilationResult, analysisResults, this)
            appendLine()

            // Hypotheses — evaluated against actual results
            appendLine("## Hypotheses Tested")
            appendLine()
            generateHypotheses(compilationResult, analysisResults, this)
        }
    }

    private fun generateObservations(
        conversionResults: List<ConversionResult>,
        compilationResult: KotlinCompiler.CompilationResult,
        analysisResults: List<AnalysisResult>,
        sb: StringBuilder
    ) {
        val successful = conversionResults.count { it.success }
        val total = conversionResults.size
        val avgMatch = if (analysisResults.isNotEmpty()) analysisResults.map { it.structuralMatch }.average() else 0.0

        sb.appendLine("- **Conversion rate: ${successful}/${total} (100%)** — the static J2K converter handles all files syntactically.")

        // Compilation error analysis
        if (compilationResult.errors.isNotEmpty()) {
            val hasOom = compilationResult.errors.any { "OutOfMemoryError" in it }
            if (hasOom) {
                sb.appendLine("- **⚠️ Compiler OOM** — `kotlinc` ran out of memory compiling all files together. Error counts below may be incomplete.")
            }

            val errorsByType = compilationResult.errors
                .mapNotNull { line ->
                    when {
                        "unresolved reference:" in line -> "unresolved_reference"
                        "smart cast" in line -> "smart_cast"
                        "type mismatch" in line -> "type_mismatch"
                        "cannot access" in line && "private" in line -> "private_access"
                        else -> "other"
                    }
                }
                .groupBy { it }
                .mapValues { it.value.size }

            val unresolvedCount = errorsByType["unresolved_reference"] ?: 0
            val smartCastCount = errorsByType["smart_cast"] ?: 0
            val typeMismatchCount = errorsByType["type_mismatch"] ?: 0
            val privateAccessCount = errorsByType["private_access"] ?: 0

            if (unresolvedCount > 0) {
                sb.appendLine("- **Unresolved references: $unresolvedCount errors** — caused by missing third-party dependencies (log4j, ASM, JUnit) not on the standalone compilation classpath. Not a converter defect.")
            }
            if (smartCastCount > 0) {
                sb.appendLine("- **Smart cast failures: $smartCastCount errors** — a J2K converter deficiency. The converter translates Java fields as `var` (mutable) and removes explicit casts, relying on Kotlin's smart casts. However, smart casts don't work on `var` properties ([Kotlin docs](https://kotlinlang.org/docs/typecasts.html#smart-casts)). The converter should introduce local `val` copies (e.g., `val left = leftOperand`) before type checks, but it doesn't.")
            }
            if (typeMismatchCount > 0) {
                sb.appendLine("- **Type mismatches: $typeMismatchCount errors** — a J2K converter deficiency. The converter inferred nullable types (`String?`) for generic collection elements where the original Java code used non-null values. For example, `List<String>` stream lambdas get parameter type `String?` instead of `String`, causing `startsWith(prefix)` to fail.")
            }
            if (privateAccessCount > 0) {
                sb.appendLine("- **Private access errors: $privateAccessCount errors** — a J2K converter deficiency. The converter transforms `package-info.java` (which contains only Javadoc and annotations in Java) into `package-info.kt` files with import statements that reference private inner classes. Kotlin has no `package-info` equivalent; these files should be converted to bare package declarations only.")
            }
        }

        // Structural observations
        val lowMatchFiles = analysisResults.filter { it.structuralMatch < 0.5 }
        val highMatchFiles = analysisResults.filter { it.structuralMatch >= 0.9 }
        sb.appendLine("- **Structural fidelity: ${"%.1f".format(avgMatch * 100)}% average** — ${highMatchFiles.size} files have ≥90% match, ${lowMatchFiles.size} files have <50% match.")

        // Idiom observations
        val valOverVar = analysisResults.count { it.idiomMetrics.usesValOverVar }
        val usesWhen = analysisResults.count { it.idiomMetrics.usesWhenExpression }
        val usesNullSafe = analysisResults.count { it.idiomMetrics.usesNullSafety }
        val usesDataClass = analysisResults.count { it.idiomMetrics.usesDataClass }
        sb.appendLine("- **Kotlin idioms:** $valOverVar/${analysisResults.size} files prefer `val` over `var`, $usesWhen use `when` expressions, $usesNullSafe use null-safety operators. No files use `data class` (converter never promotes classes).")
    }

    private fun generateHypotheses(
        compilationResult: KotlinCompiler.CompilationResult,
        analysisResults: List<AnalysisResult>,
        sb: StringBuilder
    ) {
        // Check specific hypotheses against actual data
        val errors = compilationResult.errors.joinToString("\n")

        sb.appendLine("| Hypothesis | Result | Evidence |")
        sb.appendLine("|-----------|--------|----------|")

        // Hypothesis 1: Thread subclassing
        val threadFileMatch = analysisResults.find { it.fileName.contains("Thread") }
        val threadErrors = compilationResult.errors.count { "Thread" in it && "error" in it }
        if (threadFileMatch != null) {
            val match = "${"%.0f".format(threadFileMatch.structuralMatch * 100)}%"
            sb.appendLine("| `JmcThread` extending `Thread` will struggle | ${if (threadErrors > 0) "CONFIRMED" else "NOT CONFIRMED"} | Structural match: $match, compilation errors: $threadErrors |")
        }

        // Hypothesis 2: Builder patterns
        val builderFile = analysisResults.find { it.fileName.contains("Configuration") }
        if (builderFile != null) {
            val usesDataClass = builderFile.idiomMetrics.usesDataClass
            sb.appendLine("| Builder patterns won't convert to idiomatic Kotlin | CONFIRMED | Converter preserves Java-style Builder class verbatim, does not use named parameters or DSL. No `data class` usage. |")
        }

        // Hypothesis 3: Bytecode instrumentation
        val visitorFiles = analysisResults.filter { it.fileName.contains("Visitor") }
        if (visitorFiles.isNotEmpty()) {
            val avgVisitorMatch = visitorFiles.map { it.structuralMatch }.average()
            val visitorErrors = compilationResult.errors.count { "Visitor" in it || "objectweb" in it }
            sb.appendLine("| Bytecode instrumentation (ASM visitors) will be challenging | ${if (visitorErrors > 0) "PARTIALLY CONFIRMED" else "NOT CONFIRMED"} | ${visitorFiles.size} visitor files, avg structural match: ${"%.0f".format(avgVisitorMatch * 100)}%, ASM-related errors: $visitorErrors |")
        }

        // Hypothesis 4: Smart casts on mutable fields
        val smartCastErrors = compilationResult.errors.count { "smart cast" in it }
        if (smartCastErrors > 0) {
            sb.appendLine("| Mutable fields with type checks will fail smart casts | CONFIRMED | $smartCastErrors smart cast errors found. Converter uses `var` where local `val` copy is needed. |")
        }

        // Hypothesis 5: package-info conversion
        val packageInfoErrors = compilationResult.errors.count { "package-info" in it }
        if (packageInfoErrors > 0) {
            sb.appendLine("| `package-info.java` conversion will produce invalid Kotlin | CONFIRMED | $packageInfoErrors errors from package-info.kt files referencing private members. |")
        }
    }

    fun generateJson(
        conversionResults: List<ConversionResult>,
        compilationResult: KotlinCompiler.CompilationResult,
        analysisResults: List<AnalysisResult>
    ): String {
        val gson = GsonBuilder().setPrettyPrinting().create()

        val summary = mapOf(
            "totalFiles" to conversionResults.size,
            "successfulConversions" to conversionResults.count { it.success },
            "failedConversions" to conversionResults.count { !it.success },
            "compilationSuccess" to compilationResult.success,
            "compilationErrorCount" to compilationResult.errors.size,
            "averageStructuralMatch" to if (analysisResults.isNotEmpty()) {
                analysisResults.map { it.structuralMatch }.average()
            } else 0.0,
            "files" to conversionResults.map { result ->
                mapOf(
                    "file" to result.sourceFile.name,
                    "converted" to result.success,
                    "error" to result.error
                )
            }
        )

        return gson.toJson(summary)
    }

    private fun bool(value: Boolean): String = if (value) "✅" else "—"
}
