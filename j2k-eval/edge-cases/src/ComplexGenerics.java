package edgecases;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
public class ComplexGenerics {
    public <T extends Comparable<T>> T findMax(List<T> items) { return items.stream().max(Comparator.naturalOrder()).orElse(null); }
    public double sumOfNumbers(List<? extends Number> numbers) { return numbers.stream().mapToDouble(Number::doubleValue).sum(); }
    public void addIntegers(List<? super Integer> list) { list.add(1); list.add(2); }
    public <T extends Comparable<T> & Cloneable> void sortAndClone(List<T> items) { Collections.sort(items); }
    public <K, V> Map<V, List<K>> invertMap(Map<K, V> original) {
        return original.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
    }
    public static abstract class Builder<T extends Builder<T>> {
        private String name;
        @SuppressWarnings("unchecked") public T withName(String name) { this.name = name; return (T) this; }
        public String getName() { return name; }
    }
}
