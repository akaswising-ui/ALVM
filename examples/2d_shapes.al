room Main {
    public static void main() {
        UI.game("AL 2D Engine Demo");

        // Logical canvas: 320 x 480 units.
        // Colors use ARGB decimal integers.
        UI.rect("sky", 0, 0, 320, 480, 4278190335);
        UI.rect("ground", 0, 410, 320, 70, 4278255360);
        UI.rect("platform", 35, 365, 250, 14, 4294967295);
        UI.circle("player", 80, 335, 18, 4294901760);
        UI.circle("coin", 235, 335, 10, 4294967040);
        UI.line("rope", 235, 0, 235, 325, 4294967295);
    }
}
