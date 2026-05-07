package edgecases;
public enum EnumWithBehavior {
    ADD("+") { @Override public double apply(double a, double b) { return a + b; } },
    SUBTRACT("-") { @Override public double apply(double a, double b) { return a - b; } },
    MULTIPLY("*") { @Override public double apply(double a, double b) { return a * b; } },
    DIVIDE("/") { @Override public double apply(double a, double b) { if (b == 0) throw new ArithmeticException("Division by zero"); return a / b; } };
    private final String symbol;
    EnumWithBehavior(String symbol) { this.symbol = symbol; }
    public String getSymbol() { return symbol; }
    public abstract double apply(double a, double b);
    public static EnumWithBehavior fromSymbol(String symbol) { for (EnumWithBehavior op : values()) { if (op.symbol.equals(symbol)) return op; } throw new IllegalArgumentException("Unknown: " + symbol); }
}
