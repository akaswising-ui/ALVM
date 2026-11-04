room Arrays {
    public static void main() {
        int[] numbers = [3, 4, 5];
        numbers[1] = 10;
        AL.print(numbers[1]);
        AL.print(numbers.length);

        int total = 0;
        for (int i = 0; i < numbers.length; i = i + 1) {
            total = total + numbers[i];
        }
        AL.print(total);

        int[] alias = numbers;
        alias[0] = 7;
        AL.print(numbers[0]);

        string[] words = ["AL", "VM"];
        words[1] = "Bytecode";
        AL.print(words[0] + " " + words[1]);
        AL.print(words.length);

        boolean[] flags = [true, false];
        if (flags[0] && !flags[1]) {
            AL.print("flags ok");
        }
    }
}
