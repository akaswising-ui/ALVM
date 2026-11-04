room Main {
    public static void main() {
        Box numberBox = new Box(7);
        Box textBox = new Box("AL");
        AL.print(numberBox.number);
        AL.print(textBox.label);
    }

    class Box {
        int number;
        string label;

        Box(int value) {
            this.number = value;
        }

        Box(string text) {
            this.label = text;
        }
    }
}
