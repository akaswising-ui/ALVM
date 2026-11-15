class Main {
    public static void main() {
        UI.screen("ALVM Scientific Calculator");
        UI.text("display", "0");
        UI.text("status", "Ready");
        UI.text("memoryLabel", "MEM");
        UI.text("memoryValue", "0");

        UI.button("mc", "MC", "onKey");
        UI.button("mr", "MR", "onKey");
        UI.button("mplus", "M+", "onKey");
        UI.button("mminus", "M-", "onKey");

        UI.button("clear", "AC", "onKey");
        UI.button("clearEntry", "CE", "onKey");
        UI.button("delete", "DEL", "onKey");
        UI.button("divide", "÷", "onKey");

        UI.button("7", "7", "onKey");
        UI.button("8", "8", "onKey");
        UI.button("9", "9", "onKey");
        UI.button("multiply", "×", "onKey");

        UI.button("4", "4", "onKey");
        UI.button("5", "5", "onKey");
        UI.button("6", "6", "onKey");
        UI.button("minus", "−", "onKey");

        UI.button("1", "1", "onKey");
        UI.button("2", "2", "onKey");
        UI.button("3", "3", "onKey");
        UI.button("plus", "+", "onKey");

        UI.button("0", "0", "onKey");
        UI.button("sign", "+/−", "onKey");
        UI.button("percent", "%", "onKey");
        UI.button("equals", "=", "onKey");

        UI.button("mod", "MOD", "onKey");
        UI.button("power", "x^y", "onKey");
        UI.button("sqrt", "√x", "onKey");
        UI.button("square", "x²", "onKey");

        UI.button("cube", "x³", "onKey");
        UI.button("factorial", "x!", "onKey");

        UI.setInt("value", 0);
        UI.setInt("left", 0);
        UI.setInt("operation", 0);
        UI.setInt("newEntry", 1);
        UI.setInt("digitCount", 0);
        UI.setInt("memory", 0);
        UI.setInt("repeatOperation", 0);
        UI.setInt("repeatValue", 0);
        UI.setInt("error", 0);
    }

    int safeAdd(int a, int b) {
        int maximum = 9223372036854775807;
        int minimum = 0 - maximum - 1;

        if (b > 0 && a > maximum - b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        if (b < 0 && a < minimum - b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        return a + b;
    }

    int safeSubtract(int a, int b) {
        int maximum = 9223372036854775807;
        int minimum = 0 - maximum - 1;

        if (b < 0 && a > maximum + b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        if (b > 0 && a < minimum + b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        return a - b;
    }

    int safeMultiply(int a, int b) {
        int maximum = 9223372036854775807;
        int minimum = 0 - maximum - 1;

        if (a > 0 && b > 0 && a > maximum / b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        if (a > 0 && b < 0 && b < minimum / a) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        if (a < 0 && b > 0 && a < minimum / b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        if (a < 0 && b < 0 && a < maximum / b) {
            UI.setInt("error", 1);
            UI.setText("status", "Number overflow");
            return 0;
        }
        return a * b;
    }

    int calculate(int a, int b, int op) {
        int maximum = 9223372036854775807;
        int minimum = 0 - maximum - 1;
        int result = 0;
        int count = 0;

        UI.setInt("error", 0);

        if (op == 1) {
            return safeAdd(a, b);
        }
        if (op == 2) {
            return safeSubtract(a, b);
        }
        if (op == 3) {
            return safeMultiply(a, b);
        }
        if (op == 4) {
            if (b == 0) {
                UI.setInt("error", 1);
                UI.setText("status", "Cannot divide by zero");
                return 0;
            }
            if (a == minimum && b == -1) {
                UI.setInt("error", 1);
                UI.setText("status", "Number overflow");
                return 0;
            }
            return a / b;
        }
        if (op == 5) {
            if (b == 0) {
                UI.setInt("error", 1);
                UI.setText("status", "Cannot modulo by zero");
                return 0;
            }
            if (a == minimum && b == -1) {
                return 0;
            }
            return a % b;
        }
        if (op == 6) {
            if (b < 0 || b > 12) {
                UI.setInt("error", 1);
                UI.setText("Power exponent must be 0 to 12");
                return 0;
            }
            result = 1;
            while (count < b && UI.getInt("error", 0) == 0) {
                result = safeMultiply(result, a);
                count++;
            }
            return result;
        }
        return b;
    }

    int integerSqrt(int number) {
        int low = 0;
        int high = number;
        int middle = 0;
        int answer = 0;

        while (low <= high) {
            middle = low + (high - low) / 2;
            if (middle == 0 || middle <= number / middle) {
                answer = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return answer;
    }

    int factorial(int number) {
        int result = 1;
        int count = 2;

        if (number < 0 || number > 20) {
            UI.setInt("error", 1);
            UI.setText("status", "Factorial range is 0 to 20");
            return 0;
        }

        while (count <= number) {
            result = result * count;
            count++;
        }
        return result;
    }

    public static void onKey(string key) {
        int value = UI.getInt("value", 0);
        int left = UI.getInt("left", 0);
        int operation = UI.getInt("operation", 0);
        int fresh = UI.getInt("newEntry", 1);
        int digits = UI.getInt("digitCount", 0);
        int memory = UI.getInt("memory", 0);
        int repeatOperation = UI.getInt("repeatOperation", 0);
        int repeatValue = UI.getInt("repeatValue", 0);
        int digit = -1;
        int result = 0;
        int temporary = 0;
        int count = 0;

        UI.setInt("error", 0);

        switch (key) {
            case "0": digit = 0; break;
            case "1": digit = 1; break;
            case "2": digit = 2; break;
            case "3": digit = 3; break;
            case "4": digit = 4; break;
            case "5": digit = 5; break;
            case "6": digit = 6; break;
            case "7": digit = 7; break;
            case "8": digit = 8; break;
            case "9": digit = 9; break;
            default: break;
        }

        if (digit >= 0) {
            if (fresh == 1) {
                value = 0;
                digits = 0;
                fresh = 0;
                repeatOperation = 0;
            }
            if (digits >= 9) {
                UI.setText("status", "Maximum 9 digits per entry");
                return;
            }
            if (value < 0) {
                value = value * 10 - digit;
            } else {
                value = value * 10 + digit;
            }
            digits++;
        } else {
            switch (key) {
                case "clear":
                    value = 0;
                    left = 0;
                    operation = 0;
                    fresh = 1;
                    digits = 0;
                    repeatOperation = 0;
                    repeatValue = 0;
                    UI.setText("status", "Cleared");
                    break;

                case "clearEntry":
                    value = 0;
                    fresh = 0;
                    digits = 0;
                    UI.setText("status", "Entry cleared");
                    break;

                case "delete":
                    if (fresh == 1) {
                        value = 0;
                        digits = 0;
                        fresh = 0;
                    } else {
                        value = value / 10;
                        if (digits > 0) {
                            digits--;
                        }
                    }
                    break;

                case "sign":
                    if (value == 0 - 9223372036854775807 - 1) {
                        UI.setInt("error", 1);
                        UI.setText("status", "Cannot change sign of minimum integer");
                    } else {
                        value = 0 - value;
                        fresh = 0;
                    }
                    break;

                case "plus":
                case "minus":
                case "multiply":
                case "divide":
                case "mod":
                case "power":
                    if (operation != 0 && fresh == 0) {
                        result = calculate(left, value, operation);
                        if (UI.getInt("error", 0) == 0) {
                            left = result;
                            value = result;
                        }
                    } else {
                        if (operation == 0) {
                            left = value;
                        }
                    }

                    if (key == "plus") { operation = 1; }
                    if (key == "minus") { operation = 2; }
                    if (key == "multiply") { operation = 3; }
                    if (key == "divide") { operation = 4; }
                    if (key == "mod") { operation = 5; }
                    if (key == "power") { operation = 6; }

                    value = 0;
                    fresh = 1;
                    digits = 0;
                    repeatOperation = 0;
                    break;

                case "equals":
                    if (operation != 0) {
                        result = calculate(left, value, operation);
                        if (UI.getInt("error", 0) == 0) {
                            repeatOperation = operation;
                            repeatValue = value;
                            value = result;
                            left = result;
                            operation = 0;
                            fresh = 1;
                            digits = 0;
                        }
                    } else {
                        if (repeatOperation != 0 && fresh == 1) {
                            value = calculate(value, repeatValue, repeatOperation);
                            fresh = 1;
                        }
                    }
                    break;

                case "percent":
                    if (operation != 0) {
                        temporary = safeMultiply(left, value);
                        if (UI.getInt("error", 0) == 0) {
                            value = temporary / 100;
                        }
                    } else {
                        value = value / 100;
                    }
                    fresh = 0;
                    break;

                case "sqrt":
                    if (value < 0) {
                        UI.setInt("error", 1);
                        UI.setText("status", "Square root needs a non-negative number");
                    } else {
                        value = integerSqrt(value);
                        fresh = 1;
                    }
                    break;

                case "square":
                    value = safeMultiply(value, value);
                    fresh = 1;
                    break;

                case "cube":
                    temporary = safeMultiply(value, value);
                    if (UI.getInt("error", 0) == 0) {
                        value = safeMultiply(temporary, value);
                    }
                    fresh = 1;
                    break;

                case "factorial":
                    value = factorial(value);
                    fresh = 1;
                    break;

                case "mc":
                    memory = 0;
                    UI.setInt("memory", 0);
                    UI.setText("memoryValue", 0);
                    UI.setText("status", "Memory cleared");
                    break;

                case "mr":
                    value = memory;
                    fresh = 1;
                    digits = 0;
                    UI.setText("status", "Memory recalled");
                    break;

                case "mplus":
                    memory = safeAdd(memory, value);
                    if (UI.getInt("error", 0) == 0) {
                        UI.setInt("memory", memory);
                        UI.setText("memoryValue", memory);
                        UI.setText("status", "Added to memory");
                    }
                    break;

                case "mminus":
                    memory = safeSubtract(memory, value);
                    if (UI.getInt("error", 0) == 0) {
                        UI.setInt("memory", memory);
                        UI.setText("memoryValue", memory);
                        UI.setText("status", "Subtracted from memory");
                    }
                    break;

                default:
                    break;
            }
        }

        if (UI.getInt("error", 0) != 0) {
            UI.setText("display", "Error");
            UI.setInt("value", 0);
            UI.setInt("left", 0);
            UI.setInt("operation", 0);
            UI.setInt("newEntry", 1);
            UI.setInt("digitCount", 0);
            return;
        }

        UI.setInt("value", value);
        UI.setInt("left", left);
        UI.setInt("operation", operation);
        UI.setInt("newEntry", fresh);
        UI.setInt("digitCount", digits);
        UI.setInt("repeatOperation", repeatOperation);
        UI.setInt("repeatValue", repeatValue);
        UI.setInt("memory", memory);
        UI.setText("display", value);

        if (digit >= 0) {
            UI.setText("status", "Ready");
        }
    }
}
