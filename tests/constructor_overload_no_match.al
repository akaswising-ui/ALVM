room Main {
    public static void main() {
        Box box = new Box(true);
        AL.print(box.value);
    }

    class Box {
        int value;

        Box(int number) {
            this.value = number;
        }
    }
}
