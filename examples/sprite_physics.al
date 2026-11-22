class Main {
    public static void main() {
        change window "SpritePhysics";
        UI.game("Sprite and Gravity Test");

        // The image is loaded from the Android application's assets folder.
        UI.sprite("hero", "sprites/hero.png", 10, 20, 32, 32);
        UI.setVelocityY("hero", 0.0);

        // One 0.1 second step at 100 logical pixels/second squared.
        UI.physicsStep("hero", 100.0, 0.1);
        UI.textAt("caption", "Sprite + gravity API", 12, 80, 16, 4294967295);
    }
}
