room Main {
    public static void main() {
        Calculator calculator = new Calculator();
        AL.print(calculator.combine(4, 5));
        AL.print(calculator.combine("A", "L"));
        AL.print(calculator.combine(9));
    }

    class Calculator {
        int combine(int left, int right) {
            return left + right;
        }

        string combine(string left, string right) {
            return left + right;
        }

        int combine(int value) {
            return value + 1;
        }
    }
}
