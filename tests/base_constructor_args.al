room ParentConstructorArgs {
    public static void main() {
        Child c = new Child();
    }
    class Base {
        int value;
        Base(int initial) { this.value = initial; }
    }
    class Child extends Base {
        int extra;
    }
}
