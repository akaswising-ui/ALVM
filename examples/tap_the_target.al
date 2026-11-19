class Main {
    public static void main() {
        change window "TapTarget";
        UI.game("TAP THE TARGET");
        UI.text("title", "TAP THE TARGET");
        UI.text("instructions", "Target ko 10 baar tap karo!");
        UI.text("score", "Score: 0");
        UI.text("status", "Game Started!");
        UI.button("target", "TAP HERE", "onTap");
        UI.button("restart", "RESTART", "restartGame");
        UI.setInt("score", 0);
    }

    public static void onTap(string buttonId) {
        int score = UI.getInt("score", 0);
        score = score + 1;
        UI.setInt("score", score);
        UI.setText("score", score);

        if (score >= 10) {
            UI.setText("status", "YOU WIN!");
        } else {
            UI.setText("status", "Keep tapping!");
        }
    }

    public static void restartGame(string buttonId) {
        UI.setInt("score", 0);
        UI.setText("score", 0);
        UI.setText("status", "Game Started!");
    }
}
