room GameEngineFoundation {
    public static void main() {
        change window "GameEngineFoundation";
        UI.game("ALVM GAME ENGINE");

        UI.node("world");
        UI.transform("world", 0, 0, 1.0, 1.0, 0.0);

        UI.spriteSheet("hero", "sprites/hero.png", 24, 30, 32, 32, 4, 2);
        UI.animateSprite("hero", 8, 12, true);
        UI.attach("hero", "world");

        UI.rect("bodyA", 20, 100, 30, 30, 4294901760);
        UI.rect("bodyB", 42, 100, 30, 30, 4278255360);
        UI.physicsBody("bodyA", 1.0, 20.0, 0.0);
        UI.physicsBody("bodyB", 0.0, 0.0, 0.0);
        UI.physicsWorldStep(0.016, 0.0, 120.0, 0.25);

        UI.keyArea("leftKey", "LEFT", "onKey");
        UI.textAt("keyLabel", "Press LEFT", 12, 450, 16, 4294967295);

        UI.camera3D(0, 0, -300, 0, 0, 240);
        UI.cube3D("cubeA", -35, 0, 0, 70, 4294901760);
        UI.material3D("cubeA", 0.85);
        UI.transform3D("cubeA", -35, 0, 0, 0, 25, 0, 1.25);
        UI.cube3D("cubeB", 0, 0, 0, 70, 4278255360);
        AL.print(UI.collides3D("cubeA", "cubeB"));
    }

    public static void onKey(string key) {
        UI.setText("keyLabel", key);
    }

    public static void update(string frame) {
        UI.physicsWorldStep(0.016, 0.0, 120.0, 0.25);
    }
}
