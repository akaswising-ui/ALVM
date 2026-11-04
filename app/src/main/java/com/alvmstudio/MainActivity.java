package com.alvmstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.DocumentsContract;
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

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_AL = 10;
    private static final int REQUEST_EXPORT_AL = 11;
    private static final int REQUEST_EXPORT_ROOM = 12;
    private static final int REQUEST_WORKSPACE_TREE = 13;
    private static final String PREFS = "alvm_workspace";
    private static final String PREF_TREE_URI = "workspace_tree_uri";

    static {
        System.loadLibrary("alvm");
    }

    private EditText editor;
    private TextView console;
    private TextView editorLabel;
    private boolean compiled = false;
    private boolean saveAfterFolderPick = false;

    private DocumentFile workspaceRoot;
    private DocumentFile appFolder;
    private DocumentFile projectsFolder;
    private DocumentFile dataFolder;
    private DocumentFile buildFolder;
    private DocumentFile currentDocument;
    private String currentFileName = "Main.al";

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
        initWorkspaceFolders();

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
                new String[]{"FILE MANAGER", "NEW .AL", "SAVE"},
                new View.OnClickListener[]{
                        view -> openFileManager(),
                        view -> createNewFile(),
                        view -> saveProjectFile()
                });

        addActionRow(root,
                new String[]{"OPEN .AL", "EXPORT .AL", "EXPORT .ROOM"},
                new View.OnClickListener[]{
                        view -> openAlFile(),
                        view -> exportAlFile(),
                        view -> exportRoomFile()
                });

        addActionRow(root,
                new String[]{"COMPILE", "RUN", "BYTECODE"},
                new View.OnClickListener[]{
                        view -> compileSource(),
                        view -> runProgram(),
                        view -> showBytecode()
                });

        editorLabel = new TextView(this);
        editorLabel.setText("SOURCE  •  " + currentFileName);
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
        console.setText("ALVM ready. Choose FILE MANAGER to grant access to a workspace folder.");
        outputScroll.addView(console);
        root.addView(outputScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(118)));

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
                    0, dp(43), 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(button, params);
        }
        parent.addView(row);
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
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

    private void updateEditorLabel() {
        if (editorLabel != null) editorLabel.setText("SOURCE  •  " + currentFileName);
    }

    // Android's Storage Access Framework grants access only to the folder the user selects.
    // No broad MANAGE_EXTERNAL_STORAGE permission is requested.
    private void requestWorkspaceFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_WORKSPACE_TREE);
    }

    private boolean initWorkspaceFolders() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedUri = prefs.getString(PREF_TREE_URI, null);
        if (savedUri == null) return false;

        try {
            Uri uri = Uri.parse(savedUri);
            workspaceRoot = DocumentFile.fromTreeUri(this, uri);
            if (workspaceRoot == null || !workspaceRoot.canRead() || !workspaceRoot.canWrite()) {
                clearWorkspaceReferences();
                return false;
            }

            appFolder = findOrCreateDirectory(workspaceRoot, "ALVM Studio");
            projectsFolder = findOrCreateDirectory(appFolder, "Projects");
            dataFolder = findOrCreateDirectory(appFolder, "Data");
            buildFolder = findOrCreateDirectory(appFolder, "Build");

            if (appFolder == null || projectsFolder == null || dataFolder == null || buildFolder == null) {
                clearWorkspaceReferences();
                return false;
            }
            return true;
        } catch (Exception error) {
            clearWorkspaceReferences();
            return false;
        }
    }

    private void clearWorkspaceReferences() {
        workspaceRoot = null;
        appFolder = null;
        projectsFolder = null;
        dataFolder = null;
        buildFolder = null;
    }

    private DocumentFile findOrCreateDirectory(DocumentFile parent, String name) {
        if (parent == null) return null;
        DocumentFile directory = parent.findFile(name);
        if (directory == null) directory = parent.createDirectory(name);
        return directory != null && directory.isDirectory() ? directory : null;
    }

    private void openFileManager() {
        if (!initWorkspaceFolders()) {
            setOutput("Select a storage folder. Android will ask you to allow ALVM Studio access.");
            requestWorkspaceFolder();
            return;
        }

        List<DocumentFile> files = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        addFilesFromFolder(projectsFolder, "Projects", files, labels);
        addFilesFromFolder(buildFolder, "Build", files, labels);

        if (files.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("ALVM Studio / File Manager")
                    .setMessage("Workspace is ready.\n\nProjects: .al source files\nBuild: .room bytecode files\nData: workspace.json")
                    .setPositiveButton("NEW .AL FILE", (dialog, which) -> createNewFile())
                    .setNeutralButton("CHANGE FOLDER", (dialog, which) -> requestWorkspaceFolder())
                    .setNegativeButton("CLOSE", null)
                    .show();
            setOutput("Workspace ready: ALVM Studio/Projects, Build and Data");
            return;
        }

        DocumentFile[] fileArray = files.toArray(new DocumentFile[0]);
        String[] labelArray = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("ALVM Studio / File Manager")
                .setItems(labelArray, (dialog, which) -> openWorkspaceFile(fileArray[which]))
                .setPositiveButton("NEW .AL FILE", (dialog, which) -> createNewFile())
                .setNeutralButton("CHANGE FOLDER", (dialog, which) -> requestWorkspaceFolder())
                .setNegativeButton("CLOSE", null)
                .show();
        setOutput("File Manager: " + files.size() + " file(s) in Projects and Build");
    }

    private void addFilesFromFolder(DocumentFile folder, String prefix,
                                    List<DocumentFile> files, List<String> labels) {
        if (folder == null) return;
        DocumentFile[] found = folder.listFiles();
        List<DocumentFile> sorted = new ArrayList<>();
        Collections.addAll(sorted, found);
        Collections.sort(sorted, Comparator.comparing(
                item -> item.getName() == null ? "" : item.getName().toLowerCase()));
        for (DocumentFile file : sorted) {
            if (file.isFile()) {
                String name = file.getName() == null ? "Unnamed file" : file.getName();
                files.add(file);
                labels.add(prefix + " / " + name);
            }
        }
    }

    private void openWorkspaceFile(DocumentFile file) {
        if (file == null || file.getName() == null) return;
        String name = file.getName();
        try {
            if (name.toLowerCase().endsWith(".al")) {
                try (InputStream input = getContentResolver().openInputStream(file.getUri())) {
                    if (input == null) throw new IllegalStateException("Cannot open source file");
                    editor.setText(new String(readAll(input), StandardCharsets.UTF_8));
                }
                currentDocument = file;
                currentFileName = name;
                compiled = false;
                updateEditorLabel();
                saveInternalQuietly();
                writeWorkspaceData();
                setOutput("Opened " + name);
            } else if (name.toLowerCase().endsWith(".room")) {
                try (InputStream input = getContentResolver().openInputStream(file.getUri());
                     OutputStream output = new FileOutputStream(roomPath())) {
                    if (input == null) throw new IllegalStateException("Cannot open bytecode file");
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                compiled = true;
                setOutput("Loaded " + name + ". Press RUN or BYTECODE.");
            } else {
                setOutput("Unsupported file type: " + name);
            }
        } catch (Exception error) {
            setOutput("Open error: " + error.getMessage());
        }
    }

    private void createNewFile() {
        if (!initWorkspaceFolders()) {
            setOutput("Choose a workspace folder before creating a project file.");
            requestWorkspaceFolder();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText("Main.al");
        nameInput.setSelection(nameInput.getText().length());
        int padding = dp(18);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(padding, dp(4), padding, 0);
        wrapper.addView(nameInput);

        new AlertDialog.Builder(this)
                .setTitle("Create AL source file")
                .setMessage("The file will be saved in ALVM Studio/Projects.")
                .setView(wrapper)
                .setPositiveButton("CREATE", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        setOutput("File name cannot be empty.");
                        return;
                    }
                    if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                        setOutput("Use a simple file name without folder separators.");
                        return;
                    }
                    if (!name.toLowerCase().endsWith(".al")) name += ".al";
                    DocumentFile existing = projectsFolder.findFile(name);
                    if (existing != null) {
                        setOutput("A file with that name already exists: " + name);
                        return;
                    }
                    DocumentFile created = projectsFolder.createFile("text/plain", name);
                    if (created == null) {
                        setOutput("Could not create " + name);
                        return;
                    }
                    currentDocument = created;
                    currentFileName = name;
                    editor.setText("");
                    compiled = false;
                    updateEditorLabel();
                    saveProjectFile();
                    setOutput("Created " + name + " in ALVM Studio/Projects");
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private boolean saveProjectFile() {
        if (!initWorkspaceFolders()) {
            saveAfterFolderPick = true;
            setOutput("Choose a folder and grant ALVM Studio access to save project files.");
            requestWorkspaceFolder();
            return false;
        }

        try {
            if (currentDocument == null || !currentDocument.canWrite()) {
                currentDocument = projectsFolder.findFile(currentFileName);
                if (currentDocument == null) {
                    currentDocument = projectsFolder.createFile("text/plain", currentFileName);
                }
            }
            if (currentDocument == null) throw new IllegalStateException("Could not create project file");

            try (OutputStream output = getContentResolver().openOutputStream(currentDocument.getUri(), "wt")) {
                if (output == null) throw new IllegalStateException("Storage provider did not open the file");
                output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            }
            saveInternalQuietly();
            writeWorkspaceData();
            setOutput("Saved: ALVM Studio/Projects/" + currentFileName);
            return true;
        } catch (Exception error) {
            setOutput("Save error: " + error.getMessage());
            return false;
        }
    }

    private void writeWorkspaceData() {
        if (dataFolder == null) return;
        try {
            JSONObject state = new JSONObject();
            state.put("app", "ALVM Studio");
            state.put("formatVersion", 1);
            state.put("activeSource", currentFileName);
            state.put("buildFile", buildFileName());
            state.put("savedAtEpochMillis", System.currentTimeMillis());

            DocumentFile metadata = dataFolder.findFile("workspace.json");
            if (metadata == null) metadata = dataFolder.createFile("application/json", "workspace.json");
            if (metadata == null) throw new IllegalStateException("Cannot create workspace.json");
            try (OutputStream output = getContentResolver().openOutputStream(metadata.getUri(), "wt")) {
                if (output == null) throw new IllegalStateException("Cannot write workspace.json");
                output.write(state.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            setOutput("Project saved, but Data/workspace.json update failed: " + error.getMessage());
        }
    }

    private String buildFileName() {
        String base = currentFileName.replaceFirst("(?i)\\.al$", "");
        return base + ".room";
    }

    private void saveBuildFile() {
        if (buildFolder == null) return;
        DocumentFile existing = buildFolder.findFile(buildFileName());
        if (existing != null) existing.delete();
        DocumentFile outputFile = buildFolder.createFile("application/octet-stream", buildFileName());
        if (outputFile == null) {
            setOutput("Compiled locally, but could not create Build/" + buildFileName());
            return;
        }

        try (InputStream input = new FileInputStream(roomPath());
             OutputStream output = getContentResolver().openOutputStream(outputFile.getUri(), "wt")) {
            if (output == null) throw new IllegalStateException("Cannot open build output");
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            writeWorkspaceData();
            setOutput("Compiled and saved: Build/" + buildFileName());
        } catch (Exception error) {
            setOutput("Compiled locally, but build file save failed: " + error.getMessage());
        }
    }

    private void openAlFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_AL);
    }

    private void exportAlFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, currentFileName);
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
        intent.putExtra(Intent.EXTRA_TITLE, buildFileName());
        startActivityForResult(intent, REQUEST_EXPORT_ROOM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_WORKSPACE_TREE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                setOutput("Folder access was not granted. Choose FILE MANAGER to try again.");
                return;
            }
            Uri treeUri = data.getData();
            int grantFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(treeUri, grantFlags);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_TREE_URI, treeUri.toString()).apply();

                if (!initWorkspaceFolders()) throw new IllegalStateException("Could not prepare workspace folders");
                setOutput("Folder permission saved. Created ALVM Studio/Projects, Build and Data.");
                if (saveAfterFolderPick) {
                    saveAfterFolderPick = false;
                    saveProjectFile();
                } else {
                    openFileManager();
                }
            } catch (Exception error) {
                setOutput("Folder setup error: " + error.getMessage());
            }
            return;
        }

        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_OPEN_AL) {
                int grantFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                try {
                    getContentResolver().takePersistableUriPermission(uri, grantFlags);
                } catch (Exception ignored) { }
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Cannot open selected file");
                    editor.setText(new String(readAll(input), StandardCharsets.UTF_8));
                }
                currentDocument = null;
                currentFileName = "Imported.al";
                compiled = false;
                updateEditorLabel();
                saveInternalQuietly();
                setOutput("Imported source. Use SAVE to put it into your workspace Projects folder.");
            } else if (requestCode == REQUEST_EXPORT_AL) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Cannot write selected file");
                    output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
                    setOutput("Exported " + currentFileName);
                }
            } else if (requestCode == REQUEST_EXPORT_ROOM) {
                try (InputStream input = new FileInputStream(roomPath());
                     OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Cannot write selected file");
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    setOutput("Exported " + buildFileName());
                }
            }
        } catch (Exception error) {
            setOutput("File error: " + error.getMessage());
        }
    }

    private void compileSource() {
        saveInternalQuietly();
        String result = nativeCompile(editor.getText().toString(), getFilesDir().getAbsolutePath());
        compiled = result.startsWith("Compiled ");
        setOutput(result);
        if (compiled && initWorkspaceFolders()) {
            if (currentDocument == null || !currentDocument.canWrite()) {
                currentDocument = projectsFolder.findFile(currentFileName);
            }
            if (currentDocument == null) {
                currentDocument = projectsFolder.createFile("text/plain", currentFileName);
            }
            try {
                if (currentDocument != null) {
                    try (OutputStream output = getContentResolver().openOutputStream(currentDocument.getUri(), "wt")) {
                        if (output != null) output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception error) {
                setOutput("Compiled, but source auto-save failed: " + error.getMessage());
            }
            saveBuildFile();
        }
    }

    private void runProgram() {
        if (!compiled || !new File(roomPath()).exists()) {
            setOutput("Compile the current source first, or open a .room file in FILE MANAGER.");
            return;
        }
        setOutput(nativeRun(roomPath()));
    }

    private void showBytecode() {
        if (!new File(roomPath()).exists()) {
            setOutput("Compile a source file first or open a .room file in FILE MANAGER.");
            return;
        }
        setOutput(nativeDisassemble(roomPath()));
    }

    private void saveInternalQuietly() {
        try (FileOutputStream output = new FileOutputStream(sourcePath())) {
            output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            Toast.makeText(this, "Could not save internal source: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
