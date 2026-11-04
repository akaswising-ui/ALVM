room Main {
    public static void main() {
        Derived item = new Derived(42, "ALVM");
        AL.print(item.value);
        AL.print(item.label);
    }

    class Base {
        int value;

        Base(int initial) {
            this.value = initial;
        }
    }

    class Derived extends Base {
        string label;

        Derived(int initial, string text) {
            super(initial);
            this.label = text;
        }
    }
}
