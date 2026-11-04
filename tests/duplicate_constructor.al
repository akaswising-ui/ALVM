room DuplicateConstructor {
    public static void main() {
        Player p = new Player();
    }
    class Player {
        int value;
        Player() { this.value = 1; }
        Player(int x) { this.value = x; }
    }
}
