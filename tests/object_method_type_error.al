room BadMethod {
    public static void main() {
        Player p = new Player();
        p.add("wrong");
    }
    class Player {
        int health;
        public void add(int value) { this.health = this.health + value; }
    }
}
