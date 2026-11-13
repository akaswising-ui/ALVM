room Operators {
    public static void main() {
        int value = 10;
        value += 5;
        value -= 3;
        value *= 2;
        value /= 4;
        value++;
        ++value;
        value--;
        --value;

        int total = 0;
        for (int i = 0; i < 4; i++) {
            total += i;
        }

        string label = "AL";
        label += "VM";

        AL.print(value);
        AL.print(total);
        AL.print(label);
    }
}
