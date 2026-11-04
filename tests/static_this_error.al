room StaticThis {
    public static void main() {
        AL.print(Counter.bad());
    }
    class Counter {
        static int bad() {
            return this.value;
        }
        int value;
    }
}
