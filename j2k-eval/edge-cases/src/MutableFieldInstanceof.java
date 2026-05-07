package edgecases;

public class MutableFieldInstanceof {
    interface Shape { double area(); }
    static class Circle implements Shape {
        final double radius;
        Circle(double radius) { this.radius = radius; }
        public double area() { return Math.PI * radius * radius; }
        public double circumference() { return 2 * Math.PI * radius; }
    }
    static class Rectangle implements Shape {
        final double width, height;
        Rectangle(double w, double h) { this.width = w; this.height = h; }
        public double area() { return width * height; }
        public double perimeter() { return 2 * (width + height); }
    }
    private Shape currentShape;
    public void setShape(Shape shape) { this.currentShape = shape; }
    public String describe() {
        if (currentShape instanceof Circle) {
            return "Circle with circumference: " + ((Circle) currentShape).circumference();
        } else if (currentShape instanceof Rectangle) {
            return "Rectangle with perimeter: " + ((Rectangle) currentShape).perimeter();
        }
        return "Unknown shape";
    }
    public double computeSpecificMetric() {
        if (currentShape instanceof Circle) { return ((Circle) currentShape).circumference(); }
        else if (currentShape instanceof Rectangle) { return ((Rectangle) currentShape).perimeter(); }
        return 0.0;
    }
}
