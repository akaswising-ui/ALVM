room Main {
    public static void main() {
        int[] scores = new int[5];
        scores[0] = 10;
        scores[4] = 50;
        AL.print(scores.length);
        AL.print(scores[0]);
        AL.print(scores[1]);
        AL.print(scores[4]);

        string[] names = new string[2];
        names[1] = "AL";
        AL.print(names[0]);
        AL.print(names[1]);

        int[] empty = new int[0];
        AL.print(empty.length);

        boolean[] flags = new boolean[2];
        AL.print(flags[0]);
        flags[1] = true;
        AL.print(flags[1]);
    }
}
