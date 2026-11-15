class Main {
    public static void main() {
        int absolute = AL.Math.abs(-7);
        int smallest = AL.Math.min(8, 3);
        double largest = AL.Math.max(8.5, 3.5);
        double root = AL.Math.sqrt(81.0);
        int roundedDown = AL.Math.floor(8.9);
        int roundedUp = AL.Math.ceil(8.1);
        int rounded = AL.Math.round(8.5);
        double power = AL.Math.pow(2.0, 5.0);

        string upper = AL.String.upper("alvm");
        string lower = AL.String.lower("ALVM");
        int byteLength = AL.String.length("ALVM");
        boolean found = AL.String.contains("ALVM Studio", "Studio");
        boolean prefix = AL.String.startsWith("ALVM Studio", "ALVM");
        boolean suffix = AL.String.endsWith("ALVM Studio", "Studio");
        string replaced = AL.String.replace("ALVM Studio", "Studio", "Language");

        AL.print(absolute);
        AL.print(smallest);
        AL.print(largest);
        AL.print(root);
        AL.print(roundedDown);
        AL.print(roundedUp);
        AL.print(rounded);
        AL.print(power);
        AL.print(upper);
        AL.print(lower);
        AL.print(byteLength);
        AL.print(found);
        AL.print(prefix);
        AL.print(suffix);
        AL.print(replaced);
    }
}
