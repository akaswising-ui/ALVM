room Functions {
    public static void main() {
        int result = add(10, 20);
        AL.print(result);
        AL.print(factorial(5));
        greet("AL");

        int[] numbers = [4, 5, 6];
        int total = sum(numbers);
        AL.print(total);
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static int factorial(int n) {
        if (n <= 1) {
            return 1;
        } else {
            return n * factorial(n - 1);
        }
    }

    public static void greet(string name) {
        AL.print("Hello " + name);
        return;
    }

    public static int sum(int[] values) {
        int total = 0;
        for (int i = 0; i < values.length; i = i + 1) {
            total = total + values[i];
        }
        return total;
    }
}
