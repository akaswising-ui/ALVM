class Main {
    public static void main() {
        change window "MeteorRush";
        UI.game("METEOR RUSH");

        // Deep space background and stars.
        UI.rect("space", 0, 0, 320, 480, 4278717462);
        UI.circle("star1", 24, 55, 2, 4294967295);
        UI.circle("star2", 76, 105, 2, 4294967295);
        UI.circle("star3", 280, 85, 2, 4294967295);
        UI.circle("star4", 210, 170, 2, 4294967295);
        UI.circle("star5", 42, 245, 2, 4294967295);
        UI.circle("star6", 250, 300, 2, 4294967295);
        UI.line("laneLeft", 4, 0, 4, 480, 4282606062);
        UI.line("laneRight", 316, 0, 316, 480, 4282606062);

        UI.text("score", "SCORE: 0");
        UI.text("best", "BEST: 0");
        UI.text("status", "Avoid the orange meteors!");
        UI.text("hint", "Tap left or right to steer your ship.");

        UI.setInt("scoreValue", 0);
        UI.setInt("bestValue", 0);
        UI.setInt("speed", 3);
        UI.setInt("seed", 19);
        UI.setInt("meteorY", -30);
        UI.setInt("meteorX", 140);
        UI.setInt("playerX", 160);
        UI.setInt("gameOver", 0);

        drawShip(160);
        drawMeteor(140, -30);

        UI.touchArea("playZone", 0, 0, 320, 480, "onTouch");
        UI.frameLoop("update", 20);
    }

    public static void drawShip(int x) {
        UI.rect("leftWing", x - 28, 426, 14, 8, 4282606062);
        UI.rect("rightWing", x + 14, 426, 14, 8, 4282606062);
        UI.circle("ship", x, 430, 16, 4280472558);
        UI.circle("cockpit", x, 427, 6, 4294967295);
    }

    public static void drawMeteor(int x, int y) {
        UI.line("meteorTrail", x, y - 18, x, y - 42, 4294921581);
        UI.circle("meteor", x, y, 16, 4294944299);
    }

    public static void onTouch(string event) {
        int stopped = UI.getInt("gameOver", 0);

        if (stopped == 1) {
            UI.setInt("scoreValue", 0);
            UI.setInt("speed", 3);
            UI.setInt("seed", 19);
            UI.setInt("meteorY", -30);
            UI.setInt("meteorX", 140);
            UI.setInt("playerX", 160);
            UI.setInt("gameOver", 0);

            UI.setText("score", "SCORE: 0");
            UI.setText("status", "GO! Dodge the meteors!");
            drawShip(160);
            drawMeteor(140, -30);
            UI.frameLoop("update", 20);
            return;
        }

        int x = UI.getInt("touchX", 160);
        if (x < 30) {
            x = 30;
        }
        if (x > 290) {
            x = 290;
        }

        UI.setInt("playerX", x);
        drawShip(x);
    }

    public static void update(string event) {
        int stopped = UI.getInt("gameOver", 0);
        if (stopped == 1) {
            UI.stopLoop();
            return;
        }

        int y = UI.getInt("meteorY", -30);
        int x = UI.getInt("meteorX", 140);
        int speed = UI.getInt("speed", 3);
        y = y + speed;

        if (y > 510) {
            int score = UI.getInt("scoreValue", 0) + 1;
            UI.setInt("scoreValue", score);
            UI.setText("score", "SCORE: " + score);

            int best = UI.getInt("bestValue", 0);
            if (score > best) {
                best = score;
                UI.setInt("bestValue", best);
                UI.setText("best", "BEST: " + best);
            }

            if (score % 3 == 0 && speed < 8) {
                speed = speed + 1;
                UI.setInt("speed", speed);
            }

            int seed = UI.getInt("seed", 19);
            seed = (seed * 17 + 11) % 97;
            UI.setInt("seed", seed);
            int lane = seed % 6;
            x = 22 + lane * 55;
            y = -30;
        }

        UI.setInt("meteorX", x);
        UI.setInt("meteorY", y);
        drawMeteor(x, y);

        if (UI.collides("ship", "meteor")) {
            int score = UI.getInt("scoreValue", 0);
            int best = UI.getInt("bestValue", 0);
            if (score > best) {
                UI.setInt("bestValue", score);
                UI.setText("best", "BEST: " + score);
            }

            UI.setInt("gameOver", 1);
            UI.stopLoop();
            UI.setText("status", "CRASH! Tap the game to restart.");
        }
    }
}
