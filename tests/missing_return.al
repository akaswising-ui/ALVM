room MissingReturn {
    public static void main() {
        AL.print(value(false));
    }
    public static int value(boolean enabled) {
        if (enabled) {
            return 1;
        }
    }
}
