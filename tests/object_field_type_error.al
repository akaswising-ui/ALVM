room BadField {
    public static void main() {
        Player p = new Player();
        p.health = "not an integer";
    }
    class Player {
        int health;
    }
}
