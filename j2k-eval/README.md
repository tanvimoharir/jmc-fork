# J2K Evaluation Pipeline

Evaluates the static Java-to-Kotlin (J2K) converter against the JMC (Java Model Checker) core module.

## What This Does

1. **Converts** all Java source files in `core/src/main/java/` to Kotlin using the Kotlin compiler's built-in J2K translator
2. **Compiles** the converted Kotlin files to check for validity
3. **Analyzes** structural fidelity (class/method/field counts) between original Java and converted Kotlin
4. **Measures** idiomatic Kotlin usage (data classes, val vs var, null safety, etc.)
5. **Generates** a Markdown report and JSON summary

## Running Locally

### Prerequisites
- JDK 17+
- Kotlin compiler (`kotlinc`) on your PATH — [install guide](https://kotlinlang.org/docs/command-line.html)

### Run the pipeline

```bash
# From the repository root:
./gradlew :j2k-eval:run --args="--source core/src/main/java --output j2k-eval/output"
```

### View the report

The report is written to `j2k-eval/output/evaluation-report.md`.

## Running via GitHub Actions

The pipeline runs automatically on push to `main` or `j2k-eval` branches. You can also trigger it manually:

1. Go to the **Actions** tab in the GitHub repository
2. Select **J2K Evaluation Pipeline**
3. Click **Run workflow**
4. Download the report artifact after the run completes

## Project Structure

```
j2k-eval/
├── build.gradle.kts              # Module build config
├── README.md                     # This file
└── src/main/kotlin/eval/
    ├── Main.kt                   # CLI entry point & orchestration
    ├── J2KConverter.kt           # Wraps the Kotlin J2K translator API
    ├── KotlinCompiler.kt         # Compiles converted .kt files via kotlinc subprocess
    ├── StructuralAnalyzer.kt     # Compares Java/Kotlin structural metrics
    └── ReportGenerator.kt        # Produces Markdown + JSON reports
```

## Evaluation Metrics

| Metric | Description |
|--------|-------------|
| Conversion rate | % of Java files successfully converted to Kotlin |
| Compilation success | Whether the converted Kotlin files compile |
| Structural match | How well class/method/field counts are preserved |
| Idiom usage | Whether the output uses idiomatic Kotlin patterns |

## Why JMC?

JMC (Java Model Checker) is a good stress-test for J2K because it uses:
- **Concurrency primitives** — custom Thread/Lock subclasses
- **Builder patterns** — `JmcCheckerConfiguration.Builder`
- **Lambda-heavy code** — functional test targets
- **Bytecode instrumentation** — Java agent code
- **Annotations** — custom JMC annotations
- **Generics** — complex type parameters in the solver module
