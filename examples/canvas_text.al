class Main {
    public static void main() {
        change window "ALCanvasText";
        UI.game("ALVM Canvas Text");
        UI.rect("background", 0, 0, 320, 480, 4278190335);
        UI.circle("red-ball", 62, 130, 22, 4294910771);
        UI.rect("green-panel", 110, 100, 175, 70, 4278255360);
        UI.textAt("title", "ALVM Canvas Text", 16, 42, 22, 4294967295);
        UI.textAt("caption", "Text is drawn on the game canvas", 16, 72, 14, 4294967295);
        UI.textAt("green-label", "ARGB colors work", 120, 142, 16, 4278190080);
    }
}
