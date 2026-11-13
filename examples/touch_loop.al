Room TouchLoop {
    public static void main() {
        UI.game("AL TOUCH LOOP");
        UI.rect("player", 20, 20, 40, 40, 4278255360);
        UI.rect("wall", 45, 30, 40, 60, 4294901760);
        UI.text("touch", "Touch the canvas");
        UI.text("frames", "Frames: 0");
        UI.touchArea("move", 0, 0, 320, 480, "onTouch");
        UI.frameLoop("update", 30);
        AL.print(UI.collides("player", "wall"));
    }

    public static void onTouch(string id) {
        int x = UI.getInt("touchX", 0);
        int y = UI.getInt("touchY", 0);
        UI.setText("touch", x);
        UI.setInt("lastTouchY", y);
    }

    public static void update(string event) {
        int frames = UI.getInt("frames", 0);
        frames++;
        UI.setInt("frames", frames);
        UI.setText("frames", frames);
    }

    public static void stopGame(string event) {
        UI.stopLoop();
    }
}
