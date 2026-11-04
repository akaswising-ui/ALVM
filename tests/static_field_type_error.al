room StaticTypeError {
    public static void main() {
        Counter.value = "wrong";
    }
    class Counter {
        static int value;
    }
}
