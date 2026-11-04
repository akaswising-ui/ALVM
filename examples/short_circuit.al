room ShortCircuit {
    public static void main() {
        boolean never = false && (1 / 0 == 0);
        boolean always = true || (1 / 0 == 0);
        if (!never && always) {
            AL.print("short circuit ok");
        }
    }
}
