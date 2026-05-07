package edgecases

/**
 * Edge case: Mutable fields with instanceof checks.
 * Hypothesis: CONFIRMED — the converter produces smart cast errors on var properties.
 * This is the exact pattern that causes the smart cast failures in JmcConcreteFormula.
 */
class MutableFieldInstanceof {
    interface Shape {
        fun area(): Double
    }

    internal class Circle(val radius: Double) : Shape {
        override fun area(): Double {
            return java.lang.Math.PI * radius * radius
        }

        fun circumference(): Double {
            return 2 * java.lang.Math.PI * radius
        }
    }

    internal class Rectangle(val width: Double, val height: Double) : Shape {
        override fun area(): Double {
            return width * height
        }

        fun perimeter(): Double {
            return 2 * (width + height)
        }
    }

    private var currentShape: Shape? = null

    fun setShape(shape: Shape?) {
        this.currentShape = shape
    }

    /**
     * This method uses instanceof on a mutable field.
     * The J2K converter will produce:
     * if (currentShape is Circle) { currentShape.circumference() }
     * which fails because currentShape is a var.
     */
    fun describe(): String {
        if (currentShape is Circle) {
            return "Circle with circumference: " + (currentShape as Circle).circumference()
        } else if (currentShape is Rectangle) {
            return "Rectangle with perimeter: " + (currentShape as Rectangle).perimeter()
        }
        return "Unknown shape"
    }

    /**
     * Multiple instanceof checks in a switch-like pattern.
     */
    fun computeSpecificMetric(): Double {
        if (currentShape is Circle) {
            return (currentShape as Circle).circumference()
        } else if (currentShape is Rectangle) {
            return (currentShape as Rectangle).perimeter()
        }
        return 0.0
    }
}