room StaticViaObject {
    public static void main() {
        Counter c = new Counter();
        c.run();
    }
    class Counter {
        static void run() {
            return;
        }
    }
}
