room Main {
    public static void main() {
        Calculator calculator = new Calculator();
        AL.print(calculator.combine(1));
    }

    class Calculator {
        int combine(int value) {
            return value;
        }

        string combine(int other) {
            return "duplicate";
        }
    }
}
