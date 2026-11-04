room BadDowncast {
    public static void main() {
        Animal animal = new Animal();
        Dog dog = animal;
        AL.print(dog.bark());
    }
    class Animal {
        int age;
    }
    class Dog extends Animal {
        int trained;
        public int bark() { return 1; }
    }
}
