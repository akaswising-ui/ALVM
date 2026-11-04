room Main {
    public static void main() {
        Derived item = new Derived("wrong");
        AL.print(item.value);
    }

    class Base {
        int value;
        Base(int initial) { this.value = initial; }
    }

    class Derived extends Base {
        Derived(string text) { super(text); }
    }
}
