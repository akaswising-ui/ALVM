room Main {
    public static void main() {
        AL.print(combine(2, 3));
        AL.print(combine("A", "L"));
        AL.print(combine(9));
    }

    int combine(int left, int right) {
        return left + right;
    }

    string combine(string left, string right) {
        return left + right;
    }

    int combine(int value) {
        return value + 1;
    }
}
