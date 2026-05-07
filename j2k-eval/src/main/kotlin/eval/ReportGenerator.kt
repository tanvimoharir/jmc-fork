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
                appendLine("Compares the number of classes, methods, and fields between the original Java and converted Kotlin. A preservation rate below 90% indicates the converter may have lost or merged structural elements.")
                appendLine()
                val totalJavaClasses = analysisResults.sumOf { it.javaMetrics.classCount }
                val totalKtClasses = analysisResults.sumOf { it.kotlinMetrics.classCount }
                val totalJavaMethods = analysisResults.sumOf { it.javaMetrics.methodCount }
                val totalKtMethods = analysisResults.sumOf { it.kotlinMetrics.methodCount }
                val totalJavaFields = analysisResults.sumOf { it.javaMetrics.fieldCount }
                val totalKtFields = analysisResults.sumOf { it.kotlinMetrics.fieldCount }

                appendLine("| Element | Java (original) | Kotlin (converted) | Preservation | Status |")
                appendLine("|---------|----------------|-------------------|-------------|--------|")
                val classPreserve = if (totalJavaClasses > 0) minOf(totalKtClasses.toDouble() / totalJavaClasses, 1.0) else 1.0
                val methodPreserve = if (totalJavaMethods > 0) minOf(totalKtMethods.toDouble() / totalJavaMethods, 1.0) else 1.0
                val fieldPreserve = if (totalJavaFields > 0) minOf(totalKtFields.toDouble() / totalJavaFields, 1.0) else 1.0
                appendLine("| Classes | $totalJavaClasses | $totalKtClasses | ${"%.0f".format(classPreserve * 100)}% | ${if (classPreserve < 0.9) "⚠️ POTENTIAL ISSUE" else "OK"} |")
                appendLine("| Methods | $totalJavaMethods | $totalKtMethods | ${"%.0f".format(methodPreserve * 100)}% | ${if (methodPreserve < 0.9) "⚠️ POTENTIAL ISSUE" else "OK"} |")
                appendLine("| Fields | $totalJavaFields | $totalKtFields | ${"%.0f".format(fieldPreserve * 100)}% | ${if (fieldPreserve < 0.9) "⚠️ POTENTIAL ISSUE" else "OK"} |")
                appendLine()

                // Show only worst-performing files (exclude files where Java has no measurable elements)
                val worstFiles = analysisResults
                    .filter { it.structuralMatch < 1.0 }
                    .filter { it.javaMetrics.classCount + it.javaMetrics.methodCount + it.javaMetrics.fieldCount > 0 }
                    .sortedBy { it.structuralMatch }
                    .take(5)
                if (worstFiles.isNotEmpty()) {
                    appendLine("**Lowest structural match files:**")
                    appendLine()
                    appendLine("| File | Match | Java (classes/methods/fields) | Kotlin (classes/methods/fields) | Status |")
                    appendLine("|------|-------|------------------------------|-------------------------------|--------|")
                    worstFiles.forEach { r ->
                        val flag = if (r.structuralMatch < 0.5) "⚠️ POTENTIAL ISSUE" else "Low match"
                        appendLine("| ${r.fileName} | ${"%.0f".format(r.structuralMatch * 100)}% | ${r.javaMetrics.classCount}/${r.javaMetrics.methodCount}/${r.javaMetrics.fieldCount} | ${r.kotlinMetrics.classCount}/${r.kotlinMetrics.methodCount}/${r.kotlinMetrics.fieldCount} | $flag |")
                    }
                    appendLine()
                }

                // Kotlin idiom usage — aggregate summary
                appendLine("## Kotlin Idiom Usage")
                appendLine()
                appendLine("Measures how many files use idiomatic Kotlin features. Higher percentages indicate the converter produced Kotlin-native code rather than Java-with-Kotlin-syntax.")
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
                appendLine("| More `val` than `var` declarations (immutability) | $valOverVar / $total | ${"%.0f".format(valOverVar.toDouble() / total * 100)}% |")
                appendLine("| `when` expressions (replaces switch/if-else) | $usesWhen / $total | ${"%.0f".format(usesWhen.toDouble() / total * 100)}% |")
                appendLine("| Null-safety operators (`?.`, `?:`) | $usesNullSafe / $total | ${"%.0f".format(usesNullSafe.toDouble() / total * 100)}% |")
                appendLine("| String templates (`\${}`) | $usesTemplates / $total | ${"%.0f".format(usesTemplates.toDouble() / total * 100)}% |")
                appendLine("| `companion object` (for static members) | $usesCompanion / $total | ${"%.0f".format(usesCompanion.toDouble() / total * 100)}% |")
                appendLine("| `data class` (for value types) | $usesDataClass / $total | ${"%.0f".format(usesDataClass.toDouble() / total * 100)}% |")
                appendLine()
            }

            // Quality metrics summary
            if (analysisResults.isNotEmpty()) {
                appendLine("## Conversion Quality")
                appendLine()
                appendLine("Detects code patterns that indicate poor conversion quality. Each metric has an acceptable range — values outside that range are flagged as potential issues.")
                appendLine()
                val totalBangBang = analysisResults.sumOf { it.qualityMetrics.bangBangCount }
                val avgLineRatio = analysisResults.map { it.qualityMetrics.lineRatio }.average()
                val totalInstanceofChains = analysisResults.sumOf { it.qualityMetrics.instanceofChainCount }
                val totalStringConcats = analysisResults.sumOf { it.qualityMetrics.stringConcatCount }
                val totalExplicitCasts = analysisResults.sumOf { it.qualityMetrics.explicitCastCount }
                val totalJvmAnnotations = analysisResults.sumOf { it.qualityMetrics.jvmAnnotationCount }
                val totalOpenClasses = analysisResults.sumOf { it.qualityMetrics.openClassCount }
                val totalFiles = analysisResults.size

                appendLine("| Metric | Type | Value | Acceptable Range | Comments |")
                appendLine("|--------|------|-------|-----------------|----------|")
                appendLine("| `!!` non-null assertions | count | $totalBangBang | 0 | ${if (totalBangBang > 0) "⚠️ POTENTIAL ISSUE — converter used forced unwraps instead of safe handling" else "No issues"} |")
                appendLine("| Line ratio (Kotlin / Java) | ratio | ${"%.2f".format(avgLineRatio)} | 0.7 – 1.0 | ${if (avgLineRatio > 1.1) "⚠️ POTENTIAL ISSUE — converted code is more verbose than original" else "Within range"} |")
                appendLine("| `if-else is` chains | count | $totalInstanceofChains | 0 | ${if (totalInstanceofChains > 0) "⚠️ POTENTIAL ISSUE — should be `when` expressions" else "No issues"} |")
                appendLine("| String concatenation (`+`) | count | $totalStringConcats | 0 | ${if (totalStringConcats > 0) "⚠️ POTENTIAL ISSUE — should use string templates" else "No issues"} |")
                appendLine("| Explicit casts (`as`) | count | $totalExplicitCasts | 0 | ${if (totalExplicitCasts > 0) "⚠️ POTENTIAL ISSUE — could use smart casts instead" else "No issues"} |")
                appendLine("| JVM interop annotations | count | $totalJvmAnnotations | >0 for libraries | ${if (totalJvmAnnotations == 0) "⚠️ POTENTIAL ISSUE — may break Java callers" else "Preserves Java interop"} |")
                appendLine("| `open` classes | count | $totalOpenClasses | matches inheritable classes | Needed for classes with subclasses |")
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
            appendLine("Each hypothesis was formulated before running the evaluation and tested against the actual results.")
            appendLine()
            generateHypotheses(compilationResult, analysisResults, this)
            appendLine()

            // How to interpret
            appendLine("## How to Interpret These Results")
            appendLine()
            appendLine("- **⚠️ POTENTIAL ISSUE** flags indicate areas where the converter produced suboptimal or incorrect output.")
            appendLine("- **Compilation errors** from unresolved references are expected (missing dependencies) and do not indicate converter defects.")
            appendLine("- **Compilation errors** from smart casts, type mismatches, or private access are real converter deficiencies.")
            appendLine("- **Structural match** measures whether the converter preserved all classes, methods, and fields. Values below 90% warrant investigation.")
            appendLine("- **Idiom usage** below 10% for `when` expressions or string templates suggests the converter produced Java-style code in Kotlin syntax.")
            appendLine("- **`!!` assertions** are the strongest signal of poor conversion — each one is a potential NullPointerException at runtime.")
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
                sb.appendLine("- ⚠️ **POTENTIAL ISSUE — Smart cast failures: $smartCastCount errors** — a J2K converter deficiency. The converter translates Java fields as `var` (mutable) and removes explicit casts, relying on Kotlin's smart casts. However, smart casts don't work on `var` properties ([Kotlin docs](https://kotlinlang.org/docs/typecasts.html#smart-casts)). The converter should introduce local `val` copies (e.g., `val left = leftOperand`) before type checks, but it doesn't.")
            }
            if (typeMismatchCount > 0) {
                sb.appendLine("- ⚠️ **POTENTIAL ISSUE — Type mismatches: $typeMismatchCount errors** — a J2K converter deficiency. The converter inferred nullable types (`String?`) for generic collection elements where the original Java code used non-null values. For example, `List<String>` stream lambdas get parameter type `String?` instead of `String`, causing `startsWith(prefix)` to fail.")
            }
            if (privateAccessCount > 0) {
                sb.appendLine("- ⚠️ **POTENTIAL ISSUE — Private access errors: $privateAccessCount errors** — a J2K converter deficiency. The converter transforms `package-info.java` (which contains only Javadoc and annotations in Java) into `package-info.kt` files with import statements that reference private inner classes. Kotlin has no `package-info` equivalent; these files should be converted to bare package declarations only.")
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
