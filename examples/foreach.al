room Main {
    public static void main() {
        int[] values = [2, 4, 6, 8];
        int total = 0;

        foreach (int value : values) {
            if (value == 4) {
                continue;
            }
            if (value == 8) {
                break;
            }
            total = total + value;
        }
        AL.print(total);

        string[] labels = ["AL", "VM"];
        foreach (string label : labels) {
            AL.print(label);
        }
    }
}
