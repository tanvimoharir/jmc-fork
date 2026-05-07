# J2K Evaluation Pipeline

Evaluates the static Java-to-Kotlin (J2K) converter against the [JMC (Java Model Checker)](https://github.com/mpi-sws-rse/jmc) project — a real-world open-source Java project focused on concurrency verification.

## What This Pipeline Does

1. **Converts** Java source files to Kotlin using IntelliJ IDEA's built-in static J2K converter
2. **Compiles** the converted Kotlin files to check for syntactic/semantic validity
3. **Analyzes** structural fidelity — are all classes, methods, and fields preserved?
4. **Measures** idiomatic Kotlin usage — does the output use Kotlin features or is it Java-in-Kotlin-syntax?
5. **Detects** conversion quality issues — `!!` assertions, unconverted patterns, explicit casts
6. **Generates** a Markdown report with findings, flagging potential issues with ⚠️ POTENTIAL ISSUE

## Modules Evaluated

| Module | Files | Description |
|--------|-------|-------------|
| `core` | 146 Java files | Model checker runtime, strategies, solver, annotations |
| `agent` | 24 Java files | Bytecode instrumentation using ASM visitors |

## Running the Pipeline

### Via GitHub Actions (automatic)

Push to `main` or `j2k-eval` branch, or trigger manually from the Actions tab.

The workflow:
1. Checks out the repository
2. Installs JDK 17 and Kotlin compiler
3. Runs the evaluation against pre-converted Kotlin files
4. Uploads the report as a downloadable artifact
5. Posts the report to the GitHub Actions job summary

### Locally

**Prerequisites:**
- JDK 17+
- IntelliJ IDEA (any edition) with Kotlin plugin enabled
- `kotlinc` on PATH ([install guide](https://kotlinlang.org/docs/command-line.html)) — needed for compilation check

**Step 1: Clone the repository**
```bash
git clone https://github.com/tanvimoharir/jmc-fork.git
cd jmc-fork
```

**Step 2: Run the J2K conversion** (if you want to regenerate converted files)

The conversion requires IntelliJ IDEA because the J2K converter is not available as a standalone CLI tool. The process is:
1. Copy the target Java files into a Gradle source root (so IntelliJ recognizes them)
2. Convert them using IntelliJ's "Convert Java File to Kotlin File" action
3. Copy the resulting `.kt` files to the evaluation directory
4. Revert the source directory back to Java

Example for the core module:
1. Open the project in IntelliJ IDEA
2. Wait for Gradle sync to complete
3. Right-click on `core/src/main/java/org/mpi_sws/jmc` in the Project view
4. Select Code → Convert Java File to Kotlin File (or Cmd+Shift+Alt+K)
5. Click OK on the configuration dialog, then Yes to correct references
6. Copy the converted `.kt` files:
   ```bash
   mkdir -p j2k-eval/converted-kt
   find core/src/main/java -name "*.kt" | while read f; do
     dest="j2k-eval/converted-kt/${f#core/src/main/java/}"
     mkdir -p "$(dirname "$dest")"
     cp "$f" "$dest"
   done
   ```
7. Revert core back to Java: `git checkout -- core/`

For custom edge-case files (not in a Gradle source root), copy them into a module's source directory first:
```bash
cp j2k-eval/edge-cases/src/*.java core/src/main/java/edgecases/
# Convert in IntelliJ, then:
mkdir -p j2k-eval/edge-cases/converted
cp core/src/main/java/edgecases/*.kt j2k-eval/edge-cases/converted/
rm -rf core/src/main/java/edgecases
```

**Step 3: Run the evaluation**

The evaluation compares the original Java source files against the converted Kotlin files:
```bash
# Evaluate core module
./gradlew :j2k-eval:run --args="--source core/src/main/java --converted j2k-eval/converted-kt --output j2k-eval/output/core"

# Evaluate agent module
./gradlew :j2k-eval:run --args="--source agent/src/main/java --converted j2k-eval/converted-kt-agent --output j2k-eval/output/agent"

# Evaluate edge cases
./gradlew :j2k-eval:run --args="--source j2k-eval/edge-cases/src --converted j2k-eval/edge-cases/converted --output j2k-eval/output/edge-cases"
```

The `--source` flag points to the original Java files, `--converted` points to the J2K-converted Kotlin files, and `--output` is where the report gets written.

**Step 4: View the report**

Reports are written as Markdown:
- `j2k-eval/output/core/evaluation-report.md`
- `j2k-eval/output/agent/evaluation-report.md`
- `j2k-eval/output/edge-cases/evaluation-report.md`

## How the Conversion Works

The J2K conversion is performed using **IntelliJ IDEA's "Convert Java File to Kotlin File" action** (Code → Convert Java File to Kotlin File). The converted `.kt` files are committed to the repository and the CI pipeline runs the evaluation against them.

### Why Not Headless IntelliJ in CI?

We investigated running IntelliJ's J2K converter headlessly in GitHub Actions via a custom `ApplicationStarter` plugin. This approach failed due to:

- **Internal API instability** — The J2K converter API (`J2kConverterExtension`, `NewJavaToKotlinConverter`) is internal to the Kotlin IntelliJ plugin and changes between versions without documentation
- **Version mismatch** — The Gradle IntelliJ Plugin (1.x) couldn't resolve the bundled Kotlin plugin for IntelliJ 2024.x, and the newer Platform Plugin (2.x) required Gradle 8.5+
- **No standalone CLI** — Unlike `kotlinc` or `format.sh`, IntelliJ does not expose J2K as a command-line tool
- **Metadata incompatibility** — Local IntelliJ (2024.3, Kotlin metadata 2.1.0) couldn't be used to verify plugin code targeting CI's IntelliJ (2023.3, Kotlin metadata 1.9.0)

The pre-committed approach is the pragmatic solution: the conversion is done once locally using the full IDE, and CI runs the evaluation logic (which is written entirely in Kotlin).

## Project Structure

```
j2k-eval/
├── README.md                          # This file
├── EVALUATION_FINDINGS.md             # Detailed analysis of findings
├── build.gradle.kts                   # Evaluation module build config
├── converted-kt/                      # Core module converted Kotlin files (146 files)
├── converted-kt-agent/                # Agent module converted Kotlin files (24 files)
├── edge-cases/                        # Custom stress-test dataset
│   ├── README.md
│   ├── src/                           # Java edge-case files
│   └── proposed-fix/                  # Proposed fix for smart cast issue
├── scripts/
│   └── run-j2k.sh                     # CI conversion script
└── src/main/kotlin/eval/
    ├── Main.kt                        # CLI entry point
    ├── ConversionResult.kt            # Data class for conversion results
    ├── KotlinCompiler.kt              # Compilation check via kotlinc
    ├── StructuralAnalyzer.kt          # Structural + quality analysis
    └── ReportGenerator.kt             # Markdown + JSON report generation
```

## Key Findings

| Finding | Type | Impact |
|---------|------|--------|
| Smart cast failures on `var` properties | Converter deficiency | 65 errors in core module |
| `package-info.java` produces invalid Kotlin | Converter deficiency | References private inner classes |
| Nullable type inference for collection elements | Converter deficiency | Type mismatches in agent module |
| Builder patterns not converted to idiomatic Kotlin | Low idiom adoption | Verbose but functional |
| `data class` never used | Low idiom adoption | Converter never promotes classes |
| 381 `!!` assertions in core module | Code quality | Potential NPEs at runtime |

## Edge Cases

See [`edge-cases/README.md`](edge-cases/README.md) for a custom dataset of tricky Java patterns designed to stress-test the converter, including:
- Nested anonymous classes
- Complex generics with wildcards
- Mutable field instanceof (confirmed failure)
- Synchronization patterns
- Streams and lambdas
- Enums with abstract methods

## Proposed Fix

See [`edge-cases/proposed-fix/SmartCastFix.md`](edge-cases/proposed-fix/SmartCastFix.md) for a detailed fix proposal that would resolve 65 compilation errors by introducing local `val` copies before `instanceof` chains on mutable fields.
