package edgecases

/**
 * Edge case: Complex generics with wildcards and bounded types.
 * Hypothesis: The converter will produce incorrect or overly verbose generic signatures.
 */
class ComplexGenerics {
    // Recursive generic bound
    fun <T : Comparable<T>?> findMax(items: List<T>): T {
        return items.stream().max(java.util.Comparator.naturalOrder<T>()).orElse(null)
    }

    // Wildcard with upper bound
    fun sumOfNumbers(numbers: List<Number?>): Double {
        return numbers.stream().mapToDouble { obj: Number -> obj.toDouble() }.sum()
    }

    // Wildcard with lower bound (contravariant)
    fun addIntegers(list: MutableList<in Int?>) {
        list.add(1)
        list.add(2)
        list.add(3)
    }

    // Multiple bounded type parameter
    fun <T> sortAndClone(items: List<T>) where T : Comparable<T>?, T : kotlin.Cloneable? {
        java.util.Collections.sort<T>(items)
    }

    // Generic method with complex return type
    fun <K, V> invertMap(original: Map<K, V>): Map<V, List<K>> {
        return original.entries.stream()
            .collect<Map<V, List<K>>, Any>(
                java.util.stream.Collectors.groupingBy<Map.Entry<K, V>, V, Any, List<K>>(
                    java.util.function.Function<Map.Entry<K, V>, V> { java.util.Map.Entry.value },
                    java.util.stream.Collectors.mapping<Map.Entry<K, V>, K, Any, List<K>>(
                        java.util.function.Function<Map.Entry<K, V>, K> { java.util.Map.Entry.key },
                        java.util.stream.Collectors.toList<K>()
                    )
                )
            )
    }

    // Self-referential generic type
    abstract class Builder<T : Builder<T>?> {
        var name: String? = null
            private set

        fun withName(name: String?): T {
            this.name = name
            return this as T
        }
    }
}