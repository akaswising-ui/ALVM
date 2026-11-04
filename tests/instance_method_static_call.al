room InstanceViaClass {
    public static void main() {
        AL.print(Counter.get());
    }
    class Counter {
        int value;
        public int get() {
            return this.value;
        }
    }
}
