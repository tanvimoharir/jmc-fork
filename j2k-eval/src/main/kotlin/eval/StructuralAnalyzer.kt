package eval

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import java.io.File

/**
 * Compares the structure of original Java source with converted Kotlin source.
 *
 * This is the "structural heuristics" part of the evaluation. We parse the original
 * Java file into an AST using JavaParser, then do regex/pattern-based analysis on
 * the Kotlin output (since we don't have a standalone Kotlin parser outside IntelliJ).
 *
 * Metrics we track:
 * - Class/interface/enum count: are all type declarations preserved?
 * - Method count: did any methods get lost or merged?
 * - Field count: are all fields present?
 * - Constructor count: did constructors convert properly?
 * - Kotlin idiom usage: does the output use val/var, data class, when expressions, etc.?
 */
class StructuralAnalyzer {

    /**
     * Analyze a single Java file and its converted Kotlin output.
     */
    fun analyze(javaFile: File, kotlinSource: String): AnalysisResult {
        val javaMetrics = extractJavaMetrics(javaFile)
        val kotlinMetrics = extractKotlinMetrics(kotlinSource)
        val idiomMetrics = analyzeKotlinIdioms(kotlinSource)
        val qualityMetrics = analyzeConversionQuality(javaFile, kotlinSource)

        return AnalysisResult(
            fileName = javaFile.name,
            javaMetrics = javaMetrics,
            kotlinMetrics = kotlinMetrics,
            idiomMetrics = idiomMetrics,
            qualityMetrics = qualityMetrics,
            structuralMatch = computeStructuralMatch(javaMetrics, kotlinMetrics)
        )
    }

    /**
     * Parse the Java file and extract structural metrics using JavaParser.
     * JavaParser gives us a proper AST, so these counts are accurate.
     */
    private fun extractJavaMetrics(javaFile: File): SourceMetrics {
        return try {
            val cu = StaticJavaParser.parse(javaFile)

            val classes = cu.findAll(ClassOrInterfaceDeclaration::class.java)
            val enums = cu.findAll(EnumDeclaration::class.java)
            val methods = cu.findAll(MethodDeclaration::class.java)
            val fields = cu.findAll(FieldDeclaration::class.java)
            val constructors = cu.findAll(ConstructorDeclaration::class.java)

            SourceMetrics(
                classCount = classes.count { !it.isInterface },
                interfaceCount = classes.count { it.isInterface },
                enumCount = enums.size,
                methodCount = methods.size,
                fieldCount = fields.sumOf { it.variables.size },
                constructorCount = constructors.size,
                lineCount = javaFile.readLines().size
            )
        } catch (e: Exception) {
            // If parsing fails, return zeroes — the report will note this
            SourceMetrics(0, 0, 0, 0, 0, 0, javaFile.readLines().size)
        }
    }

    /**
     * Extract structural metrics from Kotlin source using regex patterns.
     *
     * We don't have a standalone Kotlin parser, so we use regex heuristics.
     * These aren't perfect but are good enough for comparative analysis.
     */
    private fun extractKotlinMetrics(kotlinSource: String): SourceMetrics {
        val lines = kotlinSource.lines()

        val classPattern = Regex("""^\s*(open |abstract |data |sealed |inner )*class\s+\w+""")
        val interfacePattern = Regex("""^\s*(fun )?interface\s+\w+""")
        val enumPattern = Regex("""^\s*enum\s+class\s+\w+""")
        val funPattern = Regex("""^\s*(override |open |private |protected |internal |public )*(suspend )?fun\s+""")
        val valVarPattern = Regex("""^\s*(override |private |protected |internal |public )*(val|var)\s+\w+""")
        val constructorPattern = Regex("""^\s*(constructor\s*\()""")

        return SourceMetrics(
            classCount = lines.count { classPattern.containsMatchIn(it) && !enumPattern.containsMatchIn(it) },
            interfaceCount = lines.count { interfacePattern.containsMatchIn(it) },
            enumCount = lines.count { enumPattern.containsMatchIn(it) },
            methodCount = lines.count { funPattern.containsMatchIn(it) },
            fieldCount = lines.count { valVarPattern.containsMatchIn(it) },
            constructorCount = lines.count { constructorPattern.containsMatchIn(it) },
            lineCount = lines.size
        )
    }

    /**
     * Check for idiomatic Kotlin patterns in the converted output.
     * These indicate whether the converter produced "Kotlin-style" code
     * or just "Java written in Kotlin syntax".
     */
    private fun analyzeKotlinIdioms(kotlinSource: String): IdiomMetrics {
        return IdiomMetrics(
            usesDataClass = kotlinSource.contains("data class"),
            usesValOverVar = run {
                val vals = Regex("""\bval\b""").findAll(kotlinSource).count()
                val vars = Regex("""\bvar\b""").findAll(kotlinSource).count()
                vals > vars
            },
            usesWhenExpression = kotlinSource.contains("when (") || kotlinSource.contains("when {"),
            usesNullSafety = kotlinSource.contains("?.") || kotlinSource.contains("?:"),
            usesStringTemplates = kotlinSource.contains("\${") || Regex("""\$\w+""").containsMatchIn(kotlinSource),
            usesCompanionObject = kotlinSource.contains("companion object"),
            usesExtensionFunctions = false, // hard to detect reliably via regex
            lineCount = kotlinSource.lines().size
        )
    }

    /**
     * Analyze conversion quality beyond just structural preservation.
     * Detects code smells, unconverted patterns, and measures conciseness.
     */
    private fun analyzeConversionQuality(javaFile: File, kotlinSource: String): QualityMetrics {
        val javaSource = javaFile.readText()
        val ktLines = kotlinSource.lines()
        val javaLines = javaSource.lines()

        // Count non-null assertions (!!) — a sign of poor nullability inference
        val bangBangCount = Regex("""!!""").findAll(kotlinSource).count()

        // Line count ratio — idiomatic Kotlin should be more concise
        val lineRatio = if (javaLines.size > 0) ktLines.size.toDouble() / javaLines.size else 1.0

        // Count unconverted patterns: if-else instanceof chains that should be `when`
        val instanceofChains = Regex("""else if \(.+ is \w+\)""").findAll(kotlinSource).count()

        // Count Java-style string concatenation that should be templates
        val stringConcats = Regex("""\+\s*"|\"\s*\+""").findAll(kotlinSource).count()

        // Check if static methods became companion object (good) or stayed top-level
        val hasCompanionObject = kotlinSource.contains("companion object")
        val javaStaticMethods = Regex("""static\s+\w+\s+\w+\s*\(""").findAll(javaSource).count()

        // Count explicit type casts (as Type) — fewer is better in idiomatic Kotlin
        val explicitCasts = Regex("""\bas\b\s+\w+""").findAll(kotlinSource).count()

        // Check for @JvmStatic, @JvmOverloads, @Throws — signs of Java interop awareness
        val jvmAnnotations = Regex("""@Jvm(Static|Overloads|Field)""").findAll(kotlinSource).count()
        val throwsAnnotations = Regex("""@Throws""").findAll(kotlinSource).count()

        // Check for `open` keyword usage (Kotlin classes are final by default)
        val openClasses = Regex("""^\s*open\s+class""", RegexOption.MULTILINE).findAll(kotlinSource).count()
        val openFunctions = Regex("""^\s*(open\s+)fun""", RegexOption.MULTILINE).findAll(kotlinSource).count()

        return QualityMetrics(
            bangBangCount = bangBangCount,
            lineRatio = lineRatio,
            instanceofChainCount = instanceofChains,
            stringConcatCount = stringConcats,
            explicitCastCount = explicitCasts,
            jvmAnnotationCount = jvmAnnotations + throwsAnnotations,
            openClassCount = openClasses,
            openFunctionCount = openFunctions,
            hasCompanionObject = hasCompanionObject,
            javaStaticMethodCount = javaStaticMethods
        )
    }

    /**
     * Compute a simple structural match score (0.0 to 1.0).
     * Compares how well the Kotlin output preserves the Java structure.
     */
    private fun computeStructuralMatch(java: SourceMetrics, kotlin: SourceMetrics): Double {
        val comparisons = listOf(
            compareCount(java.classCount, kotlin.classCount),
            compareCount(java.interfaceCount, kotlin.interfaceCount),
            compareCount(java.enumCount, kotlin.enumCount),
            compareCount(java.methodCount, kotlin.methodCount),
            // Fields are tricky — Kotlin may merge constructor params + fields
            compareCount(java.fieldCount, kotlin.fieldCount + kotlin.constructorCount)
        ).filter { it >= 0 }

        return if (comparisons.isEmpty()) 1.0 else comparisons.average()
    }

    /**
     * Compare two counts, returning a similarity score from 0.0 to 1.0.
     * Returns -1.0 if both are zero (not applicable).
     */
    private fun compareCount(expected: Int, actual: Int): Double {
        if (expected == 0 && actual == 0) return -1.0
        if (expected == 0) return 0.0
        val ratio = actual.toDouble() / expected.toDouble()
        return minOf(ratio, 1.0)
    }
}

/** Structural metrics extracted from a source file. */
data class SourceMetrics(
    val classCount: Int,
    val interfaceCount: Int,
    val enumCount: Int,
    val methodCount: Int,
    val fieldCount: Int,
    val constructorCount: Int,
    val lineCount: Int
)

/** Kotlin idiom usage metrics. */
data class IdiomMetrics(
    val usesDataClass: Boolean,
    val usesValOverVar: Boolean,
    val usesWhenExpression: Boolean,
    val usesNullSafety: Boolean,
    val usesStringTemplates: Boolean,
    val usesCompanionObject: Boolean,
    val usesExtensionFunctions: Boolean,
    val lineCount: Int
)

/** Conversion quality metrics. */
data class QualityMetrics(
    val bangBangCount: Int,           // !! non-null assertions (code smell)
    val lineRatio: Double,            // Kotlin lines / Java lines (< 1.0 = more concise)
    val instanceofChainCount: Int,    // if-else instanceof that should be `when`
    val stringConcatCount: Int,       // String + concat that should be templates
    val explicitCastCount: Int,       // `as Type` casts
    val jvmAnnotationCount: Int,      // @JvmStatic, @JvmOverloads, @Throws
    val openClassCount: Int,          // open classes (needed for inheritance)
    val openFunctionCount: Int,       // open functions
    val hasCompanionObject: Boolean,  // static methods converted to companion
    val javaStaticMethodCount: Int    // original static method count
)

/** Full analysis result for a single file. */
data class AnalysisResult(
    val fileName: String,
    val javaMetrics: SourceMetrics,
    val kotlinMetrics: SourceMetrics,
    val idiomMetrics: IdiomMetrics,
    val qualityMetrics: QualityMetrics,
    val structuralMatch: Double
)
