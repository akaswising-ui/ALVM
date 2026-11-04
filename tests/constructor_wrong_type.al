room ConstructorWrongType {
    public static void main() {
        Player p = new Player("bad");
    }
    class Player {
        int value;
        Player(int number) { this.value = number; }
    }
}
