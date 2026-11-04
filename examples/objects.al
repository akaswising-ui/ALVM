room Objects {
    public static void main() {
        Player player = new Player();
        player.setHealth(75);
        AL.print(player.getHealth());

        player.health = 90;
        AL.print(player.health);
        player.add(5);
        AL.print(player.health);

        player.name = "ALVM";
        player.active = true;
        AL.print(player.name);
        AL.print(player.active);

        Player alias = player;
        alias.add(5);
        AL.print(player.health);
    }

    class Player {
        int health;
        string name;
        boolean active;

        public void setHealth(int value) {
            this.health = value;
            return;
        }

        public int getHealth() {
            return this.health;
        }

        public void add(int delta) {
            this.health = this.health + delta;
        }
    }
}
