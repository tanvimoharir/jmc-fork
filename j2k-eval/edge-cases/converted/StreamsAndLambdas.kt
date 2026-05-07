package edgecases

/**
 * Edge case: Java streams and complex lambda expressions.
 * Hypothesis: The converter should convert streams to Kotlin collection operations
 * but may produce verbose or incorrect code for complex pipelines.
 */
class StreamsAndLambdas {
    // Simple stream → should become .filter{}.map{}
    fun filterAndMap(items: List<String?>): List<String> {
        return items.stream()
            .filter { s: String -> s.length > 3 }
            .map<String> { obj: String -> obj.uppercase(java.util.Locale.getDefault()) }
            .collect<List<String>, Any>(java.util.stream.Collectors.toList<String>())
    }

    // Grouping → should become .groupBy{}
    fun groupByLength(items: List<String?>): Map<Int, List<String>> {
        return items.stream()
            .collect<Map<Int, List<String>>, Any>(
                java.util.stream.Collectors.groupingBy<String, Int>(
                    java.util.function.Function<String, Int> { obj: String -> obj.length })
            )
    }

    // Reduce with identity
    fun sumLengths(items: List<String?>): Int {
        return items.stream()
            .mapToInt { obj: String -> obj.length }
            .reduce(0, java.util.function.IntBinaryOperator { a: Int, b: Int -> java.lang.Integer.sum(a, b) })
    }

    // Multi-line lambda with local variables
    fun complexTransform(items: List<String?>): List<String> {
        return items.stream()
            .map<String> { item: String ->
                val trimmed: String = item.trim { it <= ' ' }
                if (trimmed.isEmpty()) return@map "<empty>"
                trimmed.substring(0, 1).uppercase(java.util.Locale.getDefault()) + trimmed.substring(1)
            }
            .filter { obj: String? -> java.util.Objects.nonNull(obj) }
            .sorted(
                java.util.Comparator.comparingInt<String>(java.util.function.ToIntFunction<String> { obj: String -> obj.length })
                    .reversed()
            )
            .collect<List<String>, Any>(java.util.stream.Collectors.toList<String>())
    }

    // Method references (various kinds)
    fun methodReferences() {
        val staticRef: java.util.function.Function<String, Int> =
            java.util.function.Function<String, Int> { s: String -> s.toInt() }
        val instanceRef: java.util.function.Function<String, String> =
            java.util.function.Function<String, String> { obj: String -> obj.trim { it <= ' ' } }
        val constructorRef: java.util.function.Supplier<List<String>> =
            java.util.function.Supplier<List<String>> { ArrayList() }
        val boundRef: java.util.function.BiFunction<String, String, String> =
            java.util.function.BiFunction<String, String, String> { obj: String, str: String -> obj + str }
    }

    // Functional interface with SAM conversion
    fun interface Transformer<T, R> {
        fun transform(input: T): R
    }

    fun <T, R> applyTransformer(items: List<T>, transformer: Transformer<T, R>): List<R> {
        return items.stream()
            .map<R> { input: T -> transformer.transform(input) }
            .collect<List<R>, Any>(java.util.stream.Collectors.toList<R>())
    }
}