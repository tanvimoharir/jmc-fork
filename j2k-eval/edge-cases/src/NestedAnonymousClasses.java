package edgecases;
import java.util.Comparator;
import java.util.concurrent.Callable;
public class NestedAnonymousClasses {
    public Callable<Comparator<String>> createNestedAnonymous() {
        return new Callable<Comparator<String>>() {
            @Override public Comparator<String> call() {
                return new Comparator<String>() {
                    @Override public int compare(String a, String b) { return a.length() - b.length(); }
                };
            }
        };
    }
    public Runnable createWithCapture(final String prefix) {
        final int[] counter = {0};
        return new Runnable() {
            @Override public void run() { counter[0]++; System.out.println(prefix + ": " + counter[0]); }
        };
    }
}
