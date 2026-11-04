package com.alvmstudio;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_AL = 10;
    private static final int REQUEST_EXPORT_AL = 11;
    private static final int REQUEST_EXPORT_ROOM = 12;

    static {
        System.loadLibrary("alvm");
    }

    private EditText editor;
    private TextView console;
    private boolean compiled = false;

    private static native String nativeCompile(String source, String filesDir);
    private static native String nativeRun(String roomPath);
    private static native String nativeDisassemble(String roomPath);

    private String roomPath() {
        return new File(getFilesDir(), "main.room").getAbsolutePath();
    }

    private String sourcePath() {
        return new File(getFilesDir(), "main.al").getAbsolutePath();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
        root.setBackgroundColor(Color.rgb(17, 20, 27));

        TextView heading = new TextView(this);
        heading.setText("ALVM STUDIO  •  ANDROID");
        heading.setTextColor(Color.rgb(235, 239, 247));
        heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(dp(2), dp(6), dp(2), dp(10));
        root.addView(heading);

        addActionRow(root,
                new String[]{"OPEN .AL", "SAVE", "EXPORT .AL"},
                new View.OnClickListener[]{
                        view -> openAlFile(),
                        view -> saveInternal(),
                        view -> exportAlFile()
                });

        addActionRow(root,
                new String[]{"COMPILE", "RUN", "BYTECODE"},
                new View.OnClickListener[]{
                        view -> compileSource(),
                        view -> runProgram(),
                        view -> showBytecode()
                });

        TextView editorLabel = new TextView(this);
        editorLabel.setText("SOURCE  •  main.al");
        editorLabel.setTextColor(Color.rgb(151, 170, 197));
        editorLabel.setTextSize(12);
        editorLabel.setPadding(dp(2), dp(10), dp(2), dp(5));
        root.addView(editorLabel);

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(13);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(Color.rgb(226, 232, 242));
        editor.setHintTextColor(Color.rgb(111, 125, 146));
        editor.setHint("Write AL code here...");
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setBackgroundColor(Color.rgb(28, 34, 45));
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(true);
        editor.setMinLines(10);
        editor.setText(loadInitialSource());
        root.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                compiled = false;
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        TextView outputLabel = new TextView(this);
        outputLabel.setText("OUTPUT / ERRORS");
        outputLabel.setTextColor(Color.rgb(151, 170, 197));
        outputLabel.setTextSize(12);
        outputLabel.setPadding(dp(2), dp(10), dp(2), dp(5));
        root.addView(outputLabel);

        ScrollView outputScroll = new ScrollView(this);
        outputScroll.setBackgroundColor(Color.rgb(8, 11, 16));
        console = new TextView(this);
        console.setTextColor(Color.rgb(169, 220, 181));
        console.setTextSize(12);
        console.setTypeface(Typeface.MONOSPACE);
        console.setPadding(dp(10), dp(10), dp(10), dp(10));
        console.setText("ALVM ready. Open or write a .al source file, then Compile.");
        outputScroll.addView(console);
        root.addView(outputScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(142)));

        Button exportRoom = makeButton("EXPORT .ROOM", view -> exportRoomFile());
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(exportRoom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        root.addView(bottom);

        setContentView(root);
    }

    private String sampleSource() {
        return "room Main {\n" +
                "    public static void main() {\n" +
                "        int a = 10;\n" +
                "        int b = 20;\n" +
                "        int result = a + b;\n" +
                "        AL.print(result);\n" +
                "    }\n" +
                "}\n";
    }

    private String loadInitialSource() {
        File file = new File(sourcePath());
        if (!file.exists()) return sampleSource();
        try (InputStream input = new FileInputStream(file)) {
            return new String(readAll(input), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return sampleSource();
        }
    }

    private byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private void addActionRow(LinearLayout parent, String[] labels,
                              View.OnClickListener[] listeners) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < labels.length; index++) {
            Button button = makeButton(labels[index], listeners[index]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(44), 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(button, params);
        }
        parent.addView(row);
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.rgb(48, 91, 151)));
        button.setOnClickListener(listener);
        return button;
    }

    private void setOutput(String value) {
        console.setText(value == null || value.isEmpty() ? "(no output)" : value);
    }

    private void saveInternal() {
        try (FileOutputStream output = new FileOutputStream(sourcePath())) {
            output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            setOutput("Saved locally: main.al");
        } catch (Exception error) {
            setOutput("Save error: " + error.getMessage());
        }
    }

    private void compileSource() {
        saveInternalQuietly();
        String result = nativeCompile(editor.getText().toString(), getFilesDir().getAbsolutePath());
        compiled = result.startsWith("Compiled ");
        setOutput(result);
    }

    private void runProgram() {
        if (!compiled || !new File(roomPath()).exists()) {
            setOutput("Compile the current source first.");
            return;
        }
        setOutput(nativeRun(roomPath()));
    }

    private void showBytecode() {
        if (!new File(roomPath()).exists()) {
            setOutput("Compile a source file first. No .room bytecode exists yet.");
            return;
        }
        setOutput(nativeDisassemble(roomPath()));
    }

    private void saveInternalQuietly() {
        try (FileOutputStream output = new FileOutputStream(sourcePath())) {
            output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            Toast.makeText(this, "Could not save source: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openAlFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_OPEN_AL);
    }

    private void exportAlFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "Main.al");
        startActivityForResult(intent, REQUEST_EXPORT_AL);
    }

    private void exportRoomFile() {
        File room = new File(roomPath());
        if (!room.exists()) {
            setOutput("Compile first. There is no .room file to export.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "Main.room");
        startActivityForResult(intent, REQUEST_EXPORT_ROOM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_OPEN_AL) {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Cannot open selected file");
                    editor.setText(new String(readAll(input), StandardCharsets.UTF_8));
                    saveInternalQuietly();
                    setOutput("Opened AL source file.");
                }
            } else if (requestCode == REQUEST_EXPORT_AL) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Cannot write selected file");
                    output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
                    setOutput("Exported Main.al");
                }
            } else if (requestCode == REQUEST_EXPORT_ROOM) {
                try (InputStream input = new FileInputStream(roomPath());
                     OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Cannot write selected file");
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    setOutput("Exported Main.room bytecode.");
                }
            }
        } catch (Exception error) {
            setOutput("File error: " + error.getMessage());
        }
    }
}
