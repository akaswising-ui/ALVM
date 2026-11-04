room Constructors {
    public static void main() {
        Player player = new Player(100, "ALVM");
        AL.print(player.health);
        AL.print(player.name);
        player.increase(20);
        AL.print(player.health);

        Dog dog = new Dog(8, 1);
        AL.print(dog.getAge());
        AL.print(dog.trained);
    }

    class Player {
        int health;
        string name;

        Player(int startingHealth, string playerName) {
            this.health = startingHealth;
            this.name = playerName;
        }

        public void increase(int amount) {
            this.health = this.health + amount;
        }
    }

    class Animal {
        int age;

        Animal() {
            this.age = 0;
        }

        public int getAge() {
            return this.age;
        }
    }

    class Dog extends Animal {
        int trained;

        Dog(int startingAge, int trainedFlag) {
            this.age = startingAge;
            this.trained = trainedFlag;
        }
    }
}
