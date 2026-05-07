package edgecases

/**
 * Edge case: Nested anonymous classes.
 * Hypothesis: The converter will struggle with deeply nested anonymous class hierarchies.
 */
class NestedAnonymousClasses {
    fun createNestedAnonymous(): java.util.concurrent.Callable<java.util.Comparator<String>> {
        return object : java.util.concurrent.Callable<java.util.Comparator<String?>?> {
            override fun call(): java.util.Comparator<String> {
                return object : java.util.Comparator<String?> {
                    override fun compare(a: String, b: String): Int {
                        return a.length - b.length
                    }
                }
            }
        }
    }

    fun createWithCapture(prefix: String): java.lang.Runnable {
        val counter: IntArray = intArrayOf(0)
        return object : java.lang.Runnable {
            override fun run() {
                counter.get(0)++
                println(prefix + ": " + counter.get(0))
            }
        }
    }
}