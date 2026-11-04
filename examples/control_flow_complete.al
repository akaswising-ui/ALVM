room ControlFlowComplete {
    public static void main() {
        int choice = 2;
        int score = 0;
        switch (choice) {
            case 1:
                score = 100;
                break;
            case 2:
                score = 10;
                break;
            default:
                score = -1;
        }
        AL.print(score);

        string label = "AL";
        switch (label) {
            case "VM":
                score = 0;
                break;
            case "AL":
                score = score + 2;
                break;
            default:
                score = -10;
        }
        AL.print(score);

        int n = 0;
        do {
            n = n + 1;
            if (n == 2) {
                continue;
            }
            score = score + n;
            if (n == 4) {
                break;
            }
        } while (n < 10);
        AL.print(n);
        AL.print(score);

        int i = 0;
        int counted = 0;
        while (i < 5) {
            i = i + 1;
            if (i == 2) {
                continue;
            }
            if (i == 4) {
                break;
            }
            counted = counted + 1;
        }
        AL.print(counted);
    }
}
