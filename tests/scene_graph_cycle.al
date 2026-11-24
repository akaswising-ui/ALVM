room SceneGraphCycle {
    public static void main() {
        change window "SceneGraphCycle";
        UI.game("cycle test");
        UI.node("parentA");
        UI.node("parentB");
        UI.attach("parentA", "parentB");
        UI.attach("parentB", "parentA");
    }
}
