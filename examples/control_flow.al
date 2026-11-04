room ControlFlow {
    public static void main() {
        int sum = 0;
        for (int i = 0; i < 5; i = i + 1) {
            sum = sum + i;
        }
        if (sum == 10) {
            AL.print("sum is ten");
        } else {
            AL.print("wrong");
        }
        boolean ready = true;
        while (ready) {
            AL.print(sum);
            ready = false;
        }
    }
}
