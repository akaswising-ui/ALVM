room DuplicateConstructor {
    public static void main() {
        Player p = new Player(1);
    }
    class Player {
        int value;
        Player(int x) { this.value = x; }
        Player(int y) { this.value = y + 1; }
    }
}
