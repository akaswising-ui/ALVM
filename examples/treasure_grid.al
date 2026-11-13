Room TreasureGrid {
    public static void main() {
        UI.game("TREASURE GRID");
        UI.text("help", "Find gems. Each trap removes 2 points.");
        UI.text("scoreLabel", "SCORE");
        UI.text("score", "0");
        UI.text("movesLabel", "TILES OPENED");
        UI.text("moves", "0");
        UI.text("status", "Tap any tile to begin!");

        UI.button("tile1", "1", "pickTile");
        UI.button("tile2", "2", "pickTile");
        UI.button("tile3", "3", "pickTile");
        UI.button("tile4", "4", "pickTile");
        UI.button("tile5", "5", "pickTile");
        UI.button("tile6", "6", "pickTile");
        UI.button("tile7", "7", "pickTile");
        UI.button("tile8", "8", "pickTile");
        UI.button("tile9", "9", "pickTile");
        UI.button("tile10", "10", "pickTile");
        UI.button("tile11", "11", "pickTile");
        UI.button("tile12", "12", "pickTile");
        UI.button("tile13", "13", "pickTile");
        UI.button("tile14", "14", "pickTile");
        UI.button("tile15", "15", "pickTile");
        UI.button("tile16", "16", "pickTile");
        UI.button("newGame", "NEW GAME", "restartGame");

        UI.setInt("score", 0);
        UI.setInt("moves", 0);
        UI.setInt("value1", 0);
        UI.setInt("value2", 3);
        UI.setInt("value3", 0);
        UI.setInt("value4", -1);
        UI.setInt("value5", 2);
        UI.setInt("value6", 0);
        UI.setInt("value7", 4);
        UI.setInt("value8", 0);
        UI.setInt("value9", -1);
        UI.setInt("value10", 2);
        UI.setInt("value11", 0);
        UI.setInt("value12", -1);
        UI.setInt("value13", 0);
        UI.setInt("value14", 3);
        UI.setInt("value15", 0);
        UI.setInt("value16", 5);
        UI.setInt("seen1", 0);
        UI.setInt("seen2", 0);
        UI.setInt("seen3", 0);
        UI.setInt("seen4", 0);
        UI.setInt("seen5", 0);
        UI.setInt("seen6", 0);
        UI.setInt("seen7", 0);
        UI.setInt("seen8", 0);
        UI.setInt("seen9", 0);
        UI.setInt("seen10", 0);
        UI.setInt("seen11", 0);
        UI.setInt("seen12", 0);
        UI.setInt("seen13", 0);
        UI.setInt("seen14", 0);
        UI.setInt("seen15", 0);
        UI.setInt("seen16", 0);
    }

    public static void reveal(string cell, int value) {
        int score = UI.getInt("score", 0);
        int moves = UI.getInt("moves", 0);
        moves = moves + 1;

        if (value > 0) {
            score = score + value;
            UI.setText(cell, "GEM!");
            UI.setText("status", "Treasure found! Keep going.");
        } else if (value < 0) {
            score = score - 2;
            UI.setText(cell, "TRAP");
            UI.setText("status", "Trap! You lost 2 points.");
        } else {
            UI.setText(cell, "EMPTY");
            UI.setText("status", "Empty tile. Try another one.");
        }

        UI.setInt("score", score);
        UI.setInt("moves", moves);
        UI.setText("score", score);
        UI.setText("moves", moves);

        if (moves == 16) {
            UI.setText("status", "Board cleared! Press NEW GAME to play again.");
        }
    }

    public static void pickTile(string tile) {
        switch (tile) {
            case "tile1":
                if (UI.getInt("seen1", 0) == 0) {
                    UI.setInt("seen1", 1);
                    reveal("tile1", UI.getInt("value1", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile2":
                if (UI.getInt("seen2", 0) == 0) {
                    UI.setInt("seen2", 1);
                    reveal("tile2", UI.getInt("value2", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile3":
                if (UI.getInt("seen3", 0) == 0) {
                    UI.setInt("seen3", 1);
                    reveal("tile3", UI.getInt("value3", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile4":
                if (UI.getInt("seen4", 0) == 0) {
                    UI.setInt("seen4", 1);
                    reveal("tile4", UI.getInt("value4", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile5":
                if (UI.getInt("seen5", 0) == 0) {
                    UI.setInt("seen5", 1);
                    reveal("tile5", UI.getInt("value5", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile6":
                if (UI.getInt("seen6", 0) == 0) {
                    UI.setInt("seen6", 1);
                    reveal("tile6", UI.getInt("value6", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile7":
                if (UI.getInt("seen7", 0) == 0) {
                    UI.setInt("seen7", 1);
                    reveal("tile7", UI.getInt("value7", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile8":
                if (UI.getInt("seen8", 0) == 0) {
                    UI.setInt("seen8", 1);
                    reveal("tile8", UI.getInt("value8", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile9":
                if (UI.getInt("seen9", 0) == 0) {
                    UI.setInt("seen9", 1);
                    reveal("tile9", UI.getInt("value9", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile10":
                if (UI.getInt("seen10", 0) == 0) {
                    UI.setInt("seen10", 1);
                    reveal("tile10", UI.getInt("value10", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile11":
                if (UI.getInt("seen11", 0) == 0) {
                    UI.setInt("seen11", 1);
                    reveal("tile11", UI.getInt("value11", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile12":
                if (UI.getInt("seen12", 0) == 0) {
                    UI.setInt("seen12", 1);
                    reveal("tile12", UI.getInt("value12", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile13":
                if (UI.getInt("seen13", 0) == 0) {
                    UI.setInt("seen13", 1);
                    reveal("tile13", UI.getInt("value13", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile14":
                if (UI.getInt("seen14", 0) == 0) {
                    UI.setInt("seen14", 1);
                    reveal("tile14", UI.getInt("value14", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile15":
                if (UI.getInt("seen15", 0) == 0) {
                    UI.setInt("seen15", 1);
                    reveal("tile15", UI.getInt("value15", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            case "tile16":
                if (UI.getInt("seen16", 0) == 0) {
                    UI.setInt("seen16", 1);
                    reveal("tile16", UI.getInt("value16", 0));
                } else {
                    UI.setText("status", "That tile is already open.");
                }
                break;
            default:
                UI.setText("status", "Invalid tile.");
                break;
        }
    }

    public static void restartGame(string event) {
        UI.setInt("score", 0);
        UI.setInt("moves", 0);
        UI.setText("score", 0);
        UI.setText("moves", 0);
        UI.setText("status", "New round! Find all the gems.");

        UI.setInt("seen1", 0);
        UI.setText("tile1", "1");
        UI.setInt("seen2", 0);
        UI.setText("tile2", "2");
        UI.setInt("seen3", 0);
        UI.setText("tile3", "3");
        UI.setInt("seen4", 0);
        UI.setText("tile4", "4");
        UI.setInt("seen5", 0);
        UI.setText("tile5", "5");
        UI.setInt("seen6", 0);
        UI.setText("tile6", "6");
        UI.setInt("seen7", 0);
        UI.setText("tile7", "7");
        UI.setInt("seen8", 0);
        UI.setText("tile8", "8");
        UI.setInt("seen9", 0);
        UI.setText("tile9", "9");
        UI.setInt("seen10", 0);
        UI.setText("tile10", "10");
        UI.setInt("seen11", 0);
        UI.setText("tile11", "11");
        UI.setInt("seen12", 0);
        UI.setText("tile12", "12");
        UI.setInt("seen13", 0);
        UI.setText("tile13", "13");
        UI.setInt("seen14", 0);
        UI.setText("tile14", "14");
        UI.setInt("seen15", 0);
        UI.setText("tile15", "15");
        UI.setInt("seen16", 0);
        UI.setText("tile16", "16");
    }
}
