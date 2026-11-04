room Main {
    public static void main() {
        Box box = new Box(1);
        AL.print(box.value);
    }

    class Box {
        int value;

        Box(int number) {
            this.value = number;
        }

        Box(int other) {
            this.value = other + 1;
        }
    }
}
