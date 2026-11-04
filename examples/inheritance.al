room Inheritance {
    public static void main() {
        Dog dog = new Dog();
        dog.setAge(7);
        dog.trained = 1;
        AL.print(dog.getAge());
        AL.print(dog.bark());

        Animal animal = dog;
        AL.print(animal.getAge());
        AL.print(readAge(dog));
        AL.print(isOlder(dog));
        AL.print(dog.trained);
    }

    public static int readAge(Animal animal) {
        return animal.getAge();
    }

    public static boolean isOlder(Animal animal) {
        return animal.getAge() > 5;
    }

    class Animal {
        int age;

        public void setAge(int value) {
            this.age = value;
            return;
        }

        public int getAge() {
            return this.age;
        }
    }

    class Dog extends Animal {
        int trained;

        public int bark() {
            return 1;
        }
    }
}
