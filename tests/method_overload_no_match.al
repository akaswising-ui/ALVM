room Main {
    public static void main() {
        Calculator calculator = new Calculator();
        AL.print(calculator.combine(true));
    }

    class Calculator {
        int combine(int value) {
            return value;
        }
    }
}
