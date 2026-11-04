room Main {
    public static void main() {
        UI.screen("AL Calculator");
        UI.text("display", "0");

        UI.button("clear", "AC", "onKey");
        UI.button("7", "7", "onKey");
        UI.button("8", "8", "onKey");
        UI.button("9", "9", "onKey");
        UI.button("divide", "÷", "onKey");

        UI.button("4", "4", "onKey");
        UI.button("5", "5", "onKey");
        UI.button("6", "6", "onKey");
        UI.button("multiply", "×", "onKey");

        UI.button("1", "1", "onKey");
        UI.button("2", "2", "onKey");
        UI.button("3", "3", "onKey");
        UI.button("minus", "−", "onKey");

        UI.button("0", "0", "onKey");
        UI.button("equals", "=", "onKey");
        UI.button("plus", "+", "onKey");
    }

    void onKey(string key) {
    int value = UI.getInt("value", 0);
    boolean failed = false;

    switch (key) {
        case "clear":
            value = 0;
            UI.setInt("left", 0);
            UI.setInt("operation", 0);
            break;
        case "0": value = value * 10; break;
        case "1": value = value * 10 + 1; break;
        case "2": value = value * 10 + 2; break;
        case "3": value = value * 10 + 3; break;
        case "4": value = value * 10 + 4; break;
        case "5": value = value * 10 + 5; break;
        case "6": value = value * 10 + 6; break;
        case "7": value = value * 10 + 7; break;
        case "8": value = value * 10 + 8; break;
        case "9": value = value * 10 + 9; break;

        case "plus":
            UI.setInt("left", value);
            UI.setInt("operation", 1);
            value = 0;
            break;
        case "minus":
            UI.setInt("left", value);
            UI.setInt("operation", 2);
            value = 0;
            break;
        case "multiply":
            UI.setInt("left", value);
            UI.setInt("operation", 3);
            value = 0;
            break;
        case "divide":
            UI.setInt("left", value);
            UI.setInt("operation", 4);
            value = 0;
            break;

        case "equals":
            int left = UI.getInt("left", 0);
            int operation = UI.getInt("operation", 0);
            if (operation == 1) value = left + value;
            if (operation == 2) value = left - value;
            if (operation == 3) value = left * value;
            if (operation == 4 && value == 0) {
                UI.setText("display", "Cannot divide by zero");
                value = 0;
                failed = true;
            }
            if (operation == 4 && value != 0) value = left / value;
            UI.setInt("operation", 0);
            break;
        default:
            break;
    }

    if (failed) {
        UI.setInt("value", 0);
        return;
    }

    UI.setInt("value", value);
    UI.setText("display", value);
}

}
