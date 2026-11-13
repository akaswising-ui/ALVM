Room FloatingPoint {
    public static void main() {
        float a = 1.5f;
        float b = 2.25f;
        float sum = a + b;

        double x = 9.5;
        double y = 2.0;
        double quotient = x / y;

        float[] values = new float[2];
        values[0] = sum;
        values[1] = a * b;

        double[] precise = [1.25, 2.5, 3.75];

        AL.print(sum);
        AL.print(quotient);
        AL.print(values[0]);
        AL.print(values.length);
        AL.print(precise[2]);
        AL.print(a < 2.0f);
    }
}
