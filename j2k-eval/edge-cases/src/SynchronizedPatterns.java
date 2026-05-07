package edgecases;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
public class SynchronizedPatterns {
    private volatile boolean running = true;
    private final Object lock = new Object();
    private int counter = 0;
    public synchronized void increment() { counter++; }
    public int getAndReset() { synchronized (lock) { int value = counter; counter = 0; return value; } }
    public void waitForCondition() throws InterruptedException { synchronized (lock) { while (counter == 0) { lock.wait(); } } }
    public void signalCondition() { synchronized (lock) { counter++; lock.notifyAll(); } }
    private static volatile SynchronizedPatterns instance;
    public static SynchronizedPatterns getInstance() {
        if (instance == null) { synchronized (SynchronizedPatterns.class) { if (instance == null) { instance = new SynchronizedPatterns(); } } }
        return instance;
    }
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final Condition notEmpty = reentrantLock.newCondition();
    public void lockPattern() { reentrantLock.lock(); try { counter++; notEmpty.signal(); } finally { reentrantLock.unlock(); } }
}
