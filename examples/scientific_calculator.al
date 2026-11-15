class Main {
    public static void main() {
        UI.screen("AL Scientific Calculator");
        UI.text("status", "Ready");
        UI.text("display", "0");
        UI.text("memory", "M: 0");

        UI.button("mc", "MC", "onKey");
        UI.button("mr", "MR", "onKey");
        UI.button("mPlus", "M+", "onKey");
        UI.button("mMinus", "M-", "onKey");

        UI.button("clearAll", "AC", "onKey");
        UI.button("clearEntry", "CE", "onKey");
        UI.button("backspace", "Backspace", "onKey");
        UI.button("divide", "÷", "onKey");

        UI.button("sqrt", "√x", "onKey");
        UI.button("square", "x²", "onKey");
        UI.button("power", "xʸ", "onKey");
        UI.button("multiply", "×", "onKey");

        UI.button("percent", "%", "onKey");
        UI.button("reciprocal", "1/x", "onKey");
        UI.button("factorial", "n!", "onKey");
        UI.button("minus", "−", "onKey");

        UI.button("7", "7", "onKey");
        UI.button("8", "8", "onKey");
        UI.button("9", "9", "onKey");
        UI.button("plus", "+", "onKey");

        UI.button("4", "4", "onKey");
        UI.button("5", "5", "onKey");
        UI.button("6", "6", "onKey");
        UI.button("equals", "=", "onKey");

        UI.button("1", "1", "onKey");
        UI.button("2", "2", "onKey");
        UI.button("3", "3", "onKey");
        UI.button("sign", "±", "onKey");

        UI.button("0", "0", "onKey");
        UI.button("decimal", ".", "onKey");
    }

    public static string digitText(int digit) {
        if (digit == 0) { return "0"; }
        if (digit == 1) { return "1"; }
        if (digit == 2) { return "2"; }
        if (digit == 3) { return "3"; }
        if (digit == 4) { return "4"; }
        if (digit == 5) { return "5"; }
        if (digit == 6) { return "6"; }
        if (digit == 7) { return "7"; }
        if (digit == 8) { return "8"; }
        if (digit == 9) { return "9"; }
        return "0";
    }

    public static string integerText(int number) {
        if (number < 10) {
            return digitText(number);
        }
        return integerText(number / 10) + digitText(number % 10);
    }

    public static string formatValue(int value) {
        int magnitude = AL.Math.abs(value);
        int whole = magnitude / 1000000;
        int fraction = magnitude % 1000000;
        string output = "";

        if (value < 0) {
            output = "-";
        }
        output = output + integerText(whole);

        if (fraction != 0) {
            int shortened = fraction;
            int places = 6;
            while (places > 0 && shortened % 10 == 0) {
                shortened = shortened / 10;
                places = places - 1;
            }

            int divisor = 100000;
            output = output + ".";
            for (int i = 0; i < places; i++) {
                int digit = (fraction / divisor) % 10;
                output = output + digitText(digit);
                divisor = divisor / 10;
            }
        }
        return output;
    }

    public static int multiplyFixed(int left, int right) {
        int sign = 1;
        if (left < 0) { sign = -sign; }
        if (right < 0) { sign = -sign; }

        int a = AL.Math.abs(left);
        int b = AL.Math.abs(right);
        int wholeA = a / 1000000;
        int wholeB = b / 1000000;
        int fractionA = a % 1000000;
        int fractionB = b % 1000000;

        int result = wholeA * wholeB * 1000000;
        result = result + wholeA * fractionB;
        result = result + wholeB * fractionA;
        result = result + (fractionA * fractionB) / 1000000;

        if (result > 9999999999) {
            return 99999999999999;
        }
        if (sign < 0) {
            return -result;
        }
        return result;
    }

    public static int divideFixed(int left, int right) {
        if (right == 0) {
            return 99999999999998;
        }

        int sign = 1;
        if (left < 0) { sign = -sign; }
        if (right < 0) { sign = -sign; }

        int a = AL.Math.abs(left);
        int b = AL.Math.abs(right);
        int quotient = a / b;
        int remainder = a % b;
        int result = quotient * 1000000 + (remainder * 1000000) / b;

        if (result > 9999999999) {
            return 99999999999999;
        }
        if (sign < 0) {
            return -result;
        }
        return result;
    }

    public static int squareRootFixed(int value) {
        if (value < 0) {
            return 99999999999997;
        }
        int number = value * 1000000;
        if (number == 0) {
            return 0;
        }

        int guess = number;
        while (guess > number / guess) {
            guess = (guess + number / guess) / 2;
        }
        return guess;
    }

    public static int powerFixed(int base, int exponentValue) {
        if (exponentValue % 1000000 != 0) {
            return 99999999999996;
        }

        int exponent = AL.Math.abs(exponentValue) / 1000000;
        if (exponent > 12) {
            return 99999999999999;
        }

        int result = 1000000;
        for (int i = 0; i < exponent; i++) {
            result = multiplyFixed(result, base);
            if (result == 99999999999999) {
                return result;
            }
        }

        if (exponentValue < 0) {
            return divideFixed(1000000, result);
        }
        return result;
    }

    public static int factorialFixed(int value) {
        if (value < 0 || value % 1000000 != 0) {
            return 99999999999996;
        }

        int number = value / 1000000;
        if (number > 7) {
            return 99999999999999;
        }

        int result = 1000000;
        for (int i = 2; i <= number; i++) {
            result = result * i;
        }
        return result;
    }

    public static int calculate(int left, int right, int operation) {
        if (operation == 1) {
            int result = left + right;
            if (AL.Math.abs(result) > 9999999999) {
                return 99999999999999;
            }
            return result;
        }
        if (operation == 2) {
            int result = left - right;
            if (AL.Math.abs(result) > 9999999999) {
                return 99999999999999;
            }
            return result;
        }
        if (operation == 3) {
            return multiplyFixed(left, right);
        }
        if (operation == 4) {
            return divideFixed(left, right);
        }
        if (operation == 5) {
            return powerFixed(left, right);
        }
        return right;
    }

    public static void updateMemoryDisplay(int memory) {
        UI.setText("memory", "M: " + formatValue(memory));
    }

    public static void showError(int errorCode) {
        if (errorCode == 99999999999998) {
            UI.setText("display", "Cannot divide by zero");
        } else if (errorCode == 99999999999997) {
            UI.setText("display", "Invalid square root");
        } else if (errorCode == 99999999999996) {
            UI.setText("display", "Invalid input");
        } else {
            UI.setText("display", "Overflow / limit");
        }

        UI.setText("status", "Error");
        UI.setInt("value", 0);
        UI.setInt("left", 0);
        UI.setInt("operation", 0);
        UI.setInt("newEntry", 1);
        UI.setInt("decimalMode", 0);
        UI.setInt("decimalPlaces", 0);
    }

    public static void onKey(string key) {
        int value = UI.getInt("value", 0);
        int left = UI.getInt("left", 0);
        int operation = UI.getInt("operation", 0);
        int newEntry = UI.getInt("newEntry", 0);
        int decimalMode = UI.getInt("decimalMode", 0);
        int decimalPlaces = UI.getInt("decimalPlaces", 0);
        int memory = UI.getInt("memoryValue", 0);

        if (key == "clearAll") {
            value = 0;
            left = 0;
            operation = 0;
            newEntry = 0;
            decimalMode = 0;
            decimalPlaces = 0;
            UI.setText("status", "Cleared");
        } else if (key == "clearEntry") {
            value = 0;
            newEntry = 0;
            decimalMode = 0;
            decimalPlaces = 0;
            UI.setText("status", "Entry cleared");
        } else if (key == "backspace") {
            int negative = 0;
            if (value < 0) { negative = 1; }
            int magnitude = AL.Math.abs(value);

            if (decimalMode == 1 && decimalPlaces > 0) {
                int place = 100000;
                for (int i = 1; i < decimalPlaces; i++) {
                    place = place / 10;
                }
                int fraction = magnitude % 1000000;
                int removed = (fraction / place) % 10;
                magnitude = magnitude - removed * place;
                decimalPlaces = decimalPlaces - 1;
            } else if (decimalMode == 1) {
                decimalMode = 0;
            } else {
                int whole = magnitude / 1000000;
                whole = whole / 10;
                magnitude = whole * 1000000;
            }

            value = magnitude;
            if (negative == 1) { value = -value; }
        } else if (key == "decimal") {
            if (newEntry == 1) {
                value = 0;
                decimalPlaces = 0;
                decimalMode = 0;
                newEntry = 0;
            }
            decimalMode = 1;
        } else if (key == "sign") {
            if (value < 0) {
                value = AL.Math.abs(value);
            } else if (value != 0) {
                value = -value;
            }
        } else if (key == "percent") {
            value = value / 100;
            newEntry = 0;
        } else if (key == "sqrt") {
            value = squareRootFixed(value);
            if (value > 99999999999995) {
                showError(value);
                return;
            }
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;
        } else if (key == "square") {
            value = multiplyFixed(value, value);
            if (value == 99999999999999) {
                showError(value);
                return;
            }
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;
        } else if (key == "reciprocal") {
            value = divideFixed(1000000, value);
            if (value >= 99999999999996) {
                showError(value);
                return;
            }
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;
        } else if (key == "factorial") {
            value = factorialFixed(value);
            if (value >= 99999999999996) {
                showError(value);
                return;
            }
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;
        } else if (key == "mc") {
            memory = 0;
            UI.setInt("memoryValue", 0);
            updateMemoryDisplay(memory);
            UI.setText("status", "Memory cleared");
        } else if (key == "mr") {
            value = memory;
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;
            UI.setText("status", "Memory recalled");
        } else if (key == "mPlus") {
            memory = memory + value;
            if (AL.Math.abs(memory) > 9999999999) {
                showError(99999999999999);
                return;
            }
            UI.setInt("memoryValue", memory);
            updateMemoryDisplay(memory);
            UI.setText("status", "Memory updated");
        } else if (key == "mMinus") {
            memory = memory - value;
            if (AL.Math.abs(memory) > 9999999999) {
                showError(99999999999999);
                return;
            }
            UI.setInt("memoryValue", memory);
            updateMemoryDisplay(memory);
            UI.setText("status", "Memory updated");
        } else if (key == "plus" || key == "minus" || key == "multiply" || key == "divide" || key == "power") {
            if (operation != 0 && newEntry == 0) {
                value = calculate(left, value, operation);
                if (value >= 99999999999996 || value <= -99999999999996) {
                    showError(value);
                    return;
                }
            }

            left = value;
            operation = 1;
            if (key == "minus") { operation = 2; }
            if (key == "multiply") { operation = 3; }
            if (key == "divide") { operation = 4; }
            if (key == "power") { operation = 5; }

            value = 0;
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;

            string status = formatValue(left);
            if (operation == 1) { status = status + " +"; }
            if (operation == 2) { status = status + " −"; }
            if (operation == 3) { status = status + " ×"; }
            if (operation == 4) { status = status + " ÷"; }
            if (operation == 5) { status = status + " ^"; }
            UI.setText("status", status);
        } else if (key == "equals") {
            if (operation != 0) {
                value = calculate(left, value, operation);
                if (value >= 99999999999996 || value <= -99999999999996) {
                    showError(value);
                    return;
                }
            }
            left = 0;
            operation = 0;
            newEntry = 1;
            decimalMode = 0;
            decimalPlaces = 0;
            UI.setText("status", "Result");
        } else {
            int digit = -1;
            if (key == "0") { digit = 0; }
            if (key == "1") { digit = 1; }
            if (key == "2") { digit = 2; }
            if (key == "3") { digit = 3; }
            if (key == "4") { digit = 4; }
            if (key == "5") { digit = 5; }
            if (key == "6") { digit = 6; }
            if (key == "7") { digit = 7; }
            if (key == "8") { digit = 8; }
            if (key == "9") { digit = 9; }

            if (digit >= 0) {
                if (newEntry == 1) {
                    value = 0;
                    decimalMode = 0;
                    decimalPlaces = 0;
                    newEntry = 0;
                }

                int negative = 0;
                if (value < 0) { negative = 1; }
                int magnitude = AL.Math.abs(value);

                if (decimalMode == 0) {
                    int whole = magnitude / 1000000;
                    if (whole > 999999) {
                        UI.setText("status", "Maximum input is 9999");
                    } else {
                        whole = whole * 10 + digit;
                        magnitude = whole * 1000000;
                    }
                } else if (decimalPlaces < 6) {
                    int place = 100000;
                    for (int i = 0; i < decimalPlaces; i++) {
                        place = place / 10;
                    }
                    magnitude = magnitude + digit * place;
                    decimalPlaces = decimalPlaces + 1;
                }

                value = magnitude;
                if (negative == 1) { value = -value; }
            }
        }

        UI.setInt("value", value);
        UI.setInt("left", left);
        UI.setInt("operation", operation);
        UI.setInt("newEntry", newEntry);
        UI.setInt("decimalMode", decimalMode);
        UI.setInt("decimalPlaces", decimalPlaces);
        UI.setText("display", formatValue(value));
    }
}
