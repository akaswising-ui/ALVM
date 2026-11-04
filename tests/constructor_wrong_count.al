room ConstructorWrongCount {
    public static void main() {
        Player p = new Player(1);
    }
    class Player {
        int value;
        Player(int first, int second) {
            this.value = first + second;
        }
    }
}
