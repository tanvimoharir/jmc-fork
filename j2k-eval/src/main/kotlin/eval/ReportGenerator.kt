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

            // Per-file conversion results
            appendLine("## Conversion Results")
            appendLine()
            appendLine("| File | Status | Error |")
            appendLine("|------|--------|-------|")
            conversionResults.forEach { result ->
                val status = if (result.success) "✅" else "❌"
                val error = result.error ?: ""
                appendLine("| ${result.sourceFile.name} | $status | $error |")
            }
            appendLine()

            // Compilation errors
            if (compilationResult.errors.isNotEmpty()) {
                appendLine("## Compilation Errors")
                appendLine()
                appendLine("Total compilation errors: **${compilationResult.errors.size}**")
                appendLine()
                appendLine("First 20 errors (see full artifact for complete list):")
                appendLine()
                appendLine("```")
                compilationResult.errors.take(20).forEach { appendLine(it) }
                if (compilationResult.errors.size > 20) {
                    appendLine("... and ${compilationResult.errors.size - 20} more errors")
                }
                appendLine("```")
                appendLine()
            }

            // Structural analysis
            if (analysisResults.isNotEmpty()) {
                appendLine("## Structural Analysis")
                appendLine()
                appendLine("Comparison of structural elements between original Java and converted Kotlin:")
                appendLine()
                appendLine("| File | Java Classes | Kt Classes | Java Methods | Kt Methods | Java Fields | Kt Fields | Match |")
                appendLine("|------|-------------|------------|-------------|------------|------------|----------|-------|")
                analysisResults.forEach { result ->
                    appendLine(
                        "| ${result.fileName} " +
                        "| ${result.javaMetrics.classCount} " +
                        "| ${result.kotlinMetrics.classCount} " +
                        "| ${result.javaMetrics.methodCount} " +
                        "| ${result.kotlinMetrics.methodCount} " +
                        "| ${result.javaMetrics.fieldCount} " +
                        "| ${result.kotlinMetrics.fieldCount} " +
                        "| ${"%.0f".format(result.structuralMatch * 100)}% |"
                    )
                }
                appendLine()

                // Kotlin idiom usage
                appendLine("## Kotlin Idiom Usage")
                appendLine()
                appendLine("How idiomatic is the converted Kotlin code?")
                appendLine()
                appendLine("| File | data class | val>var | when | null-safe | templates | companion |")
                appendLine("|------|-----------|---------|------|-----------|-----------|-----------|")
                analysisResults.forEach { result ->
                    val i = result.idiomMetrics
                    appendLine(
                        "| ${result.fileName} " +
                        "| ${bool(i.usesDataClass)} " +
                        "| ${bool(i.usesValOverVar)} " +
                        "| ${bool(i.usesWhenExpression)} " +
                        "| ${bool(i.usesNullSafety)} " +
                        "| ${bool(i.usesStringTemplates)} " +
                        "| ${bool(i.usesCompanionObject)} |"
                    )
                }
                appendLine()
            }

            // Observations section (to be filled in manually)
            appendLine("## Observations")
            appendLine()
            appendLine("_TODO: Add manual observations about conversion quality, patterns that failed, etc._")
            appendLine()

            appendLine("## Hypotheses Tested")
            appendLine()
            appendLine("_TODO: Document hypotheses about what the converter will struggle with._")
            appendLine()
            appendLine("Example hypotheses for JMC:")
            appendLine("- The converter will struggle with `JmcThread` extending `Thread` and overriding `start()`")
            appendLine("- Builder patterns (`JmcCheckerConfiguration.Builder`) may not convert to idiomatic Kotlin")
            appendLine("- Lambda-heavy test targets may lose type information")
            appendLine("- Bytecode instrumentation code in the agent module will be particularly challenging")
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
