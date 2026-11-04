room Main {
    public static void main() {
        Derived item = new Derived(3);
        AL.print(item.value);
    }

    class Base {
        int value;
        Base(int initial) { this.value = initial; }
    }

    class Derived extends Base {
        Derived(int initial) {
            this.value = initial;
            super(initial);
        }
    }
}
