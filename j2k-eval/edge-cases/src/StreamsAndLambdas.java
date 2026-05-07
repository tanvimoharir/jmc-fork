package edgecases;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
public class StreamsAndLambdas {
    public List<String> filterAndMap(List<String> items) { return items.stream().filter(s -> s.length() > 3).map(String::toUpperCase).collect(Collectors.toList()); }
    public Map<Integer, List<String>> groupByLength(List<String> items) { return items.stream().collect(Collectors.groupingBy(String::length)); }
    public int sumLengths(List<String> items) { return items.stream().mapToInt(String::length).reduce(0, Integer::sum); }
    public List<String> complexTransform(List<String> items) {
        return items.stream().map(item -> { String trimmed = item.trim(); if (trimmed.isEmpty()) return "<empty>"; return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1); }).filter(Objects::nonNull).sorted(Comparator.comparingInt(String::length).reversed()).collect(Collectors.toList());
    }
    public void methodReferences() {
        Function<String, Integer> staticRef = Integer::parseInt;
        Function<String, String> instanceRef = String::trim;
        Supplier<List<String>> constructorRef = ArrayList::new;
    }
}
