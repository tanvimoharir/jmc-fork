package edgecases

/**
 * Edge case: Enums with abstract methods, fields, and complex behavior.
 * Hypothesis: The converter may struggle with enum constants that override methods.
 */
enum class EnumWithBehavior(val symbol: String) {
    ADD("+") {
        override fun apply(a: Double, b: Double): Double {
            return a + b
        }
    },
    SUBTRACT("-") {
        override fun apply(a: Double, b: Double): Double {
            return a - b
        }
    },
    MULTIPLY("*") {
        override fun apply(a: Double, b: Double): Double {
            return a * b
        }
    },
    DIVIDE("/") {
        override fun apply(a: Double, b: Double): Double {
            if (b == 0.0) throw java.lang.ArithmeticException("Division by zero")
            return a / b
        }
    };

    abstract fun apply(a: Double, b: Double): Double

    companion object {
        // Static factory method
        fun fromSymbol(symbol: String): EnumWithBehavior {
            for (op in entries) {
                if (op.symbol == symbol) return op
            }
            throw java.lang.IllegalArgumentException("Unknown operator: $symbol")
        }
    }
}