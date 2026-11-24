room MultiModuleLinkDemo {
    import "modules/math_utils.al";

    public static void main() {
        int total = addScore(20, 22);
        AL.print(total);
    }
}
