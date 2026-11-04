room Main {
    public static void main() {
        UI.screen("AL Quiz Battle");

        UI.text("heading", "QUIZ BATTLE");
        UI.text("score", "0");
        UI.text("question", "Q1: Which planet is called the Red Planet?");
        UI.text("options", "A. Venus    B. Mars    C. Jupiter");
        UI.text("status", "Choose A, B or C");

        UI.button("a", "A", "onTap");
        UI.button("b", "B", "onTap");
        UI.button("c", "C", "onTap");
        UI.button("next", "NEXT", "onTap");
    }

    void onTap(string id) {
        int q = UI.getInt("questionNumber", 0);
        int scoreValue = UI.getInt("scoreValue", 0);
        int answered = UI.getInt("answered", 0);

        if (q >= 5) {
            UI.setText("status", "Quiz completed. Press Run to restart.");
            return;
        }

        if (id == "next") {
            if (answered == 0) {
                UI.setText("status", "Answer this question first.");
                return;
            }

            q = q + 1;
            UI.setInt("questionNumber", q);
            UI.setInt("answered", 0);

            if (q == 1) {
                UI.setText("question", "Q2: Which is the largest ocean?");
                UI.setText("options", "A. Atlantic    B. Indian    C. Pacific");
            }
            if (q == 2) {
                UI.setText("question", "Q3: What is 7 multiplied by 8?");
                UI.setText("options", "A. 56    B. 48    C. 64");
            }
            if (q == 3) {
                UI.setText("question", "Q4: Which gas do plants absorb?");
                UI.setText("options", "A. Oxygen    B. Carbon dioxide    C. Nitrogen");
            }
            if (q == 4) {
                UI.setText("question", "Q5: How many sides has a triangle?");
                UI.setText("options", "A. Two    B. Four    C. Three");
            }
            if (q == 5) {
                UI.setText("question", "QUIZ COMPLETED!");
                UI.setText("options", "You answered all five questions.");
                UI.setText("status", "Final score:");
                UI.setText("score", scoreValue);
                return;
            }

            UI.setText("status", "Choose A, B or C");
            return;
        }

        if (answered == 1) {
            UI.setText("status", "Press NEXT for the next question.");
            return;
        }

        int correct = 0;

        if (q == 0 && id == "b") {
            correct = 1;
        }
        if (q == 1 && id == "c") {
            correct = 1;
        }
        if (q == 2 && id == "a") {
            correct = 1;
        }
        if (q == 3 && id == "b") {
            correct = 1;
        }
        if (q == 4 && id == "c") {
            correct = 1;
        }

        if (correct == 1) {
            scoreValue = scoreValue + 10;
            UI.setText("status", "CORRECT! +10 points");
        } else {
            UI.setText("status", "Wrong answer. Try the next question.");
        }

        UI.setInt("scoreValue", scoreValue);
        UI.setText("score", scoreValue);
        UI.setInt("answered", 1);
    }
}
