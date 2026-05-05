package eval

import java.io.File

/**
 * Entry point for the J2K evaluation pipeline.
 *
 * Usage:
 *   ./gradlew :j2k-eval:run --args="--source core/src/main/java --converted j2k-eval/converted-kt --output j2k-eval/output"
 *
 * What it does:
 * 1. Walks the --source directory to find all original .java files
 * 2. Matches them against pre-converted .kt files in --converted
 * 3. Attempts to compile the converted .kt files using kotlinc
 * 4. Runs structural analysis comparing original Java AST vs converted Kotlin
 * 5. Writes a Markdown report + JSON summary to --output
 *
 * The conversion itself is done beforehand using IntelliJ IDEA's J2K converter.
 * This pipeline focuses on evaluating the quality of that conversion.
 */
fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)

    // Resolve paths relative to the project root.
    // When Gradle runs this, the working directory is the module dir (j2k-eval/),
    // so we go up one level to reach the repo root.
    val projectRoot = File(System.getProperty("user.dir")).let { cwd ->
        if (cwd.name == "j2k-eval") cwd.parentFile else cwd
    }

    val sourceDir = resolvePath(parsedArgs.sourceDir, projectRoot)
    val convertedDir = resolvePath(parsedArgs.convertedDir, projectRoot)
    val outputDir = resolvePath(parsedArgs.outputDir, projectRoot)

    require(sourceDir.exists() && sourceDir.isDirectory) {
        "Source directory does not exist: ${sourceDir.absolutePath}"
    }
    require(convertedDir.exists() && convertedDir.isDirectory) {
        "Converted directory does not exist: ${convertedDir.absolutePath}"
    }
    outputDir.mkdirs()

    println("=== J2K Evaluation Pipeline ===")
    println("Source (Java):     ${sourceDir.absolutePath}")
    println("Converted (Kotlin): ${convertedDir.absolutePath}")
    println("Output:            ${outputDir.absolutePath}")
    println()

    // Step 1: Discover Java files (skip package-info.java)
    val javaFiles = sourceDir.walkTopDown()
        .filter { it.extension == "java" }
        .filter { it.name != "package-info.java" }
        .toList()
        .sorted()

    println("Found ${javaFiles.size} Java source files")

    // Step 2: Match each Java file to its converted Kotlin counterpart
    val ktFiles = convertedDir.walkTopDown()
        .filter { it.extension == "kt" }
        .filter { it.name != "package-info.kt" }
        .toList()

    println("Found ${ktFiles.size} converted Kotlin files")
    println()

    // Build conversion results by matching Java files to their Kotlin counterparts
    val conversionResults = javaFiles.map { javaFile ->
        val relativePath = javaFile.relativeTo(sourceDir).path
        val expectedKtPath = relativePath.replace(".java", ".kt")
        val ktFile = File(convertedDir, expectedKtPath)

        if (ktFile.exists()) {
            ConversionResult(
                sourceFile = javaFile,
                kotlinSource = ktFile.readText(),
                success = true,
                error = null
            )
        } else {
            ConversionResult(
                sourceFile = javaFile,
                kotlinSource = null,
                success = false,
                error = "No matching .kt file found at: $expectedKtPath"
            )
        }
    }

    val successful = conversionResults.count { it.success }
    val failed = conversionResults.count { !it.success }
    println("Matched: $successful files, Missing: $failed files")

    // Step 3: Try to compile the converted Kotlin files
    println()
    println("Attempting to compile converted Kotlin files...")
    val compilationResult = KotlinCompiler.compile(convertedDir)
    println("Compilation: ${if (compilationResult.success) "SUCCESS" else "FAILED"}")
    if (!compilationResult.success) {
        println("Errors: ${compilationResult.errors.size}")
        compilationResult.errors.take(10).forEach { println("  - $it") }
    }

    // Step 4: Structural analysis
    println()
    println("Running structural analysis...")
    val analyzer = StructuralAnalyzer()
    val analysisResults = conversionResults.filter { it.success }.map { result ->
        analyzer.analyze(result.sourceFile, result.kotlinSource!!)
    }

    // Step 5: Generate report
    println()
    println("Generating report...")
    val report = ReportGenerator.generate(
        javaFiles = javaFiles,
        conversionResults = conversionResults,
        compilationResult = compilationResult,
        analysisResults = analysisResults
    )

    val reportFile = File(outputDir, "evaluation-report.md")
    reportFile.writeText(report)
    println("Report written to: ${reportFile.absolutePath}")

    // JSON summary for programmatic consumption
    val jsonFile = File(outputDir, "evaluation-summary.json")
    jsonFile.writeText(
        ReportGenerator.generateJson(
            conversionResults = conversionResults,
            compilationResult = compilationResult,
            analysisResults = analysisResults
        )
    )
    println("JSON summary written to: ${jsonFile.absolutePath}")
}

private fun resolvePath(path: String, projectRoot: File): File {
    val f = File(path)
    return if (f.isAbsolute) f else File(projectRoot, path)
}

/**
 * Argument parser.
 * Expects: --source <path> --converted <path> --output <path>
 */
data class PipelineArgs(
    val sourceDir: String,
    val convertedDir: String,
    val outputDir: String
)

fun parseArgs(args: Array<String>): PipelineArgs {
    var sourceDir = "core/src/main/java"
    var convertedDir = "j2k-eval/converted-kt"
    var outputDir = "j2k-eval/output"

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--source" -> { sourceDir = args[++i] }
            "--converted" -> { convertedDir = args[++i] }
            "--output" -> { outputDir = args[++i] }
            else -> println("Unknown argument: ${args[i]}")
        }
        i++
    }
    return PipelineArgs(sourceDir, convertedDir, outputDir)
}
