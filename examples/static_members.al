room StaticMembers {
    public static void main() {
        Counter.value = 10;
        Counter.increment(5);
        AL.print(Counter.value);
        AL.print(Counter.doubleIt(Counter.value));

        AdvancedCounter.increment(2);
        AL.print(Counter.value);

        AdvancedCounter.extra = 7;
        AdvancedCounter.addExtra(3);
        AL.print(AdvancedCounter.extra);
    }

    class Counter {
        static int value;

        static void increment(int amount) {
            Counter.value = Counter.value + amount;
        }

        static int doubleIt(int number) {
            return number * 2;
        }

        public int getValue() {
            return Counter.value;
        }
    }

    class AdvancedCounter extends Counter {
        static int extra;

        static void addExtra(int amount) {
            AdvancedCounter.extra = AdvancedCounter.extra + amount;
        }
    }
}
