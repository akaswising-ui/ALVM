room FunctionTypeError {
    public static void main() {
        AL.print(add(10, "wrong"));
    }
    public static int add(int a, int b) {
        return a + b;
    }
}
