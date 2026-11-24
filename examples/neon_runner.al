class Main {
    public static void main() {
        change window "NeonRunner";
        UI.game("NEON RUNNER");

        // 320 x 480 logical canvas.
        UI.rect("sky", 0, 0, 320, 480, 4278190335);
        UI.rect("ground", 0, 452, 320, 28, 4278255360);
        UI.rect("platform", 18, 398, 180, 12, 4294967295);
        UI.rect("runner", 40, 350, 28, 38, 4294901760);

        // Scene graph: game objects share one transformed world node.
        UI.node("world");
        UI.node("effects");
        UI.transform("world", 0, 0, 1.0, 1.0, 0.0);
        UI.transform("effects", 0, 0, 1.0, 1.0, 0.0);
        UI.attach("effects", "world");
        UI.attach("ground", "world");
        UI.attach("platform", "world");
        UI.attach("runner", "world");

        // Package sprites/runner.png under the app's assets directory.
        UI.spriteSheet("spark", "sprites/runner.png", 224, 365, 24, 24, 4, 2);
        UI.animateSprite("spark", 8, 10, true);
        UI.attach("spark", "effects");

        // Dynamic player and static platforms.
        UI.physicsBody("runner", 1.0, 0.0, 0.0);
        UI.physicsBody("platform", 0.0, 0.0, 0.0);
        UI.physicsBody("ground", 0.0, 0.0, 0.0);

        UI.text("status", "SPACE or tap to jump");
        UI.textAt("frameLabel", "FRAME 0", 10, 28, 16, 4294967295);
        UI.setInt("frames", 0);
        UI.keyArea("jumpKey", "SPACE", "onKey");
        UI.keyArea("upKey", "UP", "onKey");
        UI.touchArea("jumpZone", 0, 0, 320, 480, "onTouch");

        // Canvas-projected 3D foundation, not a GPU mesh renderer.
        UI.camera3D(0, 0, -300, 0, 0, 240);
        UI.cube3D("cubeA", -38, 0, 0, 56, 4294901760);
        UI.material3D("cubeA", 0.85);
        UI.cube3D("cubeB", 0, 0, 0, 56, 4278255360);
        UI.transform3D("cubeB", 0, 0, 0, 0, 25, 0, 1.0);
        AL.print(UI.collides3D("cubeA", "cubeB"));

        // Fixed 30 Hz callback with a bounded 1/30 second physics step.
        UI.frameLoop("update", 30);
    }

    public static void onKey(string key) {
        if (key == "SPACE" || key == "UP") {
            UI.physicsBody("runner", 1.0, 0.0, -175.0);
            UI.setText("status", "JUMP!");
        }
    }

    public static void onTouch(string event) {
        UI.physicsBody("runner", 1.0, 0.0, -175.0);
        UI.setText("status", "TOUCH JUMP!");
    }

    public static void update(string event) {
        UI.physicsWorldStep(0.033, 0.0, 210.0, 0.12);
        int frames = UI.getInt("frames", 0) + 1;
        UI.setInt("frames", frames);
        UI.setText("frameLabel", frames);

        if (UI.collides("runner", "platform") || UI.collides("runner", "ground")) {
            UI.setText("status", "LANDED - TAP TO JUMP");
        }
    }
}
