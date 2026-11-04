package com.alvmstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private static final int REQUEST_WORKSPACE_TREE = 13;
    private static final int REQUEST_IMPORT_FILE = 14;
    private static final int REQUEST_WEB_FILE_CHOOSER = 15;
    private static final String PREFS = "alvm_workspace";
    private static final String PREF_TREE_URI = "workspace_tree_uri";

    static {
        System.loadLibrary("alvm");
    }

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private boolean saveAfterFolderPick = false;
    private boolean openAfterFolderPick = false;

    private DocumentFile workspaceRoot;
    private DocumentFile appFolder;
    private DocumentFile projectsFolder;
    private DocumentFile dataFolder;
    private DocumentFile buildFolder;
    private DocumentFile currentDocument;

    private String currentFileName = "Main.al";
    private String pendingSource = "";
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
        getWindow().setStatusBarColor(Color.rgb(16, 22, 34));
        getWindow().setNavigationBarColor(Color.rgb(9, 13, 21));
        initWorkspaceFolders();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(9, 13, 21));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(true);
        settings.setBlockNetworkLoads(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // The app shell is bundled locally. Block navigation to remote pages.
                return !"file".equalsIgnoreCase(request.getUrl().getScheme());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("file:///android_asset/")) installNativeUiHooks();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showOutputInWeb("Cannot load the ALVM Studio interface bundled in this APK.", "ERROR");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                              FileChooserParams params) {
                if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQUEST_WEB_FILE_CHOOSER);
                    return true;
                } catch (Exception error) {
                    pendingFileCallback = null;
                    return false;
                }
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void installNativeUiHooks() {
        String script = "(function(){"
                + "if(window.__alvmNativeHooksInstalled)return;"
                + "window.__alvmNativeHooksInstalled=true;"
                + "window.__alvmShowOutput=function(message,status){"
                + "var area=document.querySelector('.console-output');"
                + "if(area){area.replaceChildren();String(message||'(no output)').split('\\n').forEach(function(line){"
                + "var row=document.createElement('div');row.textContent=line||' ';"
                + "if(line.indexOf('✕')===0||line.toLowerCase().indexOf('error')>=0)row.className='error-line';"
                + "else if(line.indexOf('✓')===0)row.className='success-line';"
                + "else if(line.indexOf('AL OUTPUT')===0)row.className='program-output';"
                + "area.appendChild(row);});}"
                + "var badge=document.querySelector('.console-title .status');"
                + "if(badge){badge.textContent=status||'READY';badge.className='status'+(status==='SUCCESS'?' success':((status||'').indexOf('ERROR')>=0?' error':''));}"
                + "};"
                + "document.addEventListener('click',function(event){"
                + "var button=event.target&&event.target.closest?event.target.closest('button'):null;"
                + "if(!button)return;"
                + "var sourceNode=document.querySelector('.editor-area textarea');"
                + "var source=sourceNode?sourceNode.value:'';"
                + "var fileNode=document.querySelector('.file-item.active span');"
                + "var fileName=fileNode?fileNode.textContent:'Main.al';"
                + "if(button.matches('.run-button')){"
                + "if(/\\bChess\\s*\\.\\s*start\\s*\\(|\\bGame\\s*\\./.test(source))return;"
                + "event.preventDefault();event.stopImmediatePropagation();"
                + "var result=Android.runSource(source,fileName);"
                + "window.__alvmShowOutput(result,result.indexOf('✕')>=0||result.indexOf('error')>=0?'RUNTIME ERROR':'SUCCESS');return;}"
                + "if(button.matches('.compile-button')){"
                + "if(/\\bChess\\s*\\.\\s*start\\s*\\(|\\bGame\\s*\\./.test(source))return;"
                + "event.preventDefault();event.stopImmediatePropagation();"
                + "var result=Android.compileSource(source,fileName);"
                + "window.__alvmShowOutput(result,result.indexOf('✕')>=0||result.indexOf('error')>=0?'COMPILE ERROR':'COMPILED');return;}"
                + "if(button.matches('.execute-button')){"
                + "event.preventDefault();event.stopImmediatePropagation();"
                + "var result=Android.executeRoom();"
                + "window.__alvmShowOutput(result,result.indexOf('✕')>=0||result.indexOf('error')>=0?'RUNTIME ERROR':'SUCCESS');return;}"
                + "if(button.closest('.file-actions')){"
                + "var label=(button.innerText||button.textContent||'').toLowerCase();"
                + "if(label.indexOf('save')>=0){event.preventDefault();event.stopImmediatePropagation();"
                + "var saved=Android.saveSource(source,fileName);window.__alvmShowOutput(saved,saved.indexOf('error')>=0?'ERROR':'READY');return;}"
                + "if(label.indexOf('open')>=0){event.preventDefault();event.stopImmediatePropagation();Android.openWorkspace();return;}}"
                + "if(button.classList.contains('side-button')){"
                + "var label=(button.innerText||button.textContent||'').toLowerCase();"
                + "if(label.indexOf('new file')>=0){event.preventDefault();event.stopImmediatePropagation();Android.createNewFile();return;}"
                + "if(label.indexOf('open file')>=0){event.preventDefault();event.stopImmediatePropagation();Android.openWorkspace();return;}}"
                + "},true);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void showOutputInWeb(String message, String status) {
        if (webView == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("message", message == null ? "" : message);
            payload.put("status", status == null ? "READY" : status);
            webView.evaluateJavascript(
                    "(function(p){if(window.__alvmShowOutput)window.__alvmShowOutput(p.message,p.status);})("
                            + payload.toString() + ");", null);
        } catch (Exception ignored) { }
    }

    private void loadAlIntoWebApp(String source, String name) {
        if (webView == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("source", source);
            payload.put("name", name);
            webView.evaluateJavascript(
                    "(function(p){var input=document.querySelector('input[type=file]');"
                            + "if(!input||!window.DataTransfer||!window.File)return;"
                            + "var transfer=new DataTransfer();"
                            + "transfer.items.add(new File([p.source],p.name,{type:'text/plain'}));"
                            + "input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));"
                            + "})((" + payload.toString() + "));", null);
        } catch (Exception error) {
            showOutputInWeb("Could not load file into editor: " + error.getMessage(), "ERROR");
        }
    }

    private String normalizeForNativeAl(String source) {
        return source.replaceAll("\\bRoom\\b", "room")
                .replaceAll("\\bString\\b", "string");
    }

    private String compileNative(String source, String fileName) {
        currentFileName = normalizeAlFileName(fileName);
        pendingSource = source;
        saveInternal(source);
        String result = nativeCompile(normalizeForNativeAl(source), getFilesDir().getAbsolutePath());
        compiled = result != null && result.startsWith("Compiled ");
        if (!compiled) return "✕ " + result;

        if (initWorkspaceFolders()) {
            saveProjectSourceNow(source);
            saveBuildFile();
        }
        return "✓ AL compilation successful\n" + result
                + (initWorkspaceFolders() ? "\nSaved: Projects/" + currentFileName
                + "\nBytecode: Build/" + buildFileName()
                : "\nChoose FILE MANAGER and allow a folder to save files permanently.");
    }

    private String runNative(String source, String fileName) {
        String result = compileNative(source, fileName);
        if (!compiled) return result;
        String output = nativeRun(roomPath());
        if (output == null) output = "";
        return "✓ Program completed in native ALVM.\n\n"
                + (output.trim().isEmpty() ? "(program produced no console output)" : "AL OUTPUT  " + output.trim().replace("\n", "\nAL OUTPUT  "));
    }

    private String executeNativeRoom() {
        File room = new File(roomPath());
        if (!room.exists()) return "✕ No compiled .room file is loaded.";
        try {
            String output = nativeRun(roomPath());
            return "✓ .room execution complete.\n\n"
                    + (output == null || output.trim().isEmpty()
                    ? "(program produced no console output)"
                    : "AL OUTPUT  " + output.trim().replace("\n", "\nAL OUTPUT  "));
        } catch (Exception error) {
            return "✕ ALVM runtime error: " + error.getMessage();
        }
    }

    private String normalizeAlFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "Main.al";
        String clean = name.trim();
        if (!clean.toLowerCase().endsWith(".al")) clean = clean.replaceFirst("(?i)\\.room$", "") + ".al";
        return clean;
    }

    private String buildFileName() {
        return currentFileName.replaceFirst("(?i)\\.al$", "") + ".room";
    }

    private void saveInternal(String source) {
        try (FileOutputStream output = new FileOutputStream(sourcePath())) {
            output.write(source.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    // The user chooses the workspace folder once. Access is persisted by Android's Storage Access Framework.
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
            openAfterFolderPick = true;
            showOutputInWeb("Choose a storage folder. Android will ask you to allow ALVM Studio access.", "READY");
            requestWorkspaceFolder();
            return;
        }

        List<DocumentFile> files = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        addFilesFromFolder(projectsFolder, "Projects", files, labels, ".al");
        addFilesFromFolder(buildFolder, "Build", files, labels, ".room");

        String[] options = new String[files.size()];
        for (int i = 0; i < files.size(); i++) options[i] = labels.get(i);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("ALVM Studio  /  File Manager")
                .setPositiveButton("NEW .AL FILE", (d, which) -> createNewFile())
                .setNeutralButton("IMPORT FILE", (d, which) -> importExternalFile())
                .setNegativeButton("CLOSE", null);
        if (options.length > 0) {
            DocumentFile[] fileArray = files.toArray(new DocumentFile[0]);
            dialog.setItems(options, (d, which) -> openWorkspaceFile(fileArray[which]));
        } else {
            dialog.setMessage("Workspace is ready.\n\nProjects  /  .al source files\nBuild  /  .room bytecode\nData  /  workspace.json");
        }
        dialog.show();
    }

    private void addFilesFromFolder(DocumentFile folder, String prefix,
                                    List<DocumentFile> files, List<String> labels, String suffix) {
        if (folder == null) return;
        DocumentFile[] found = folder.listFiles();
        List<DocumentFile> sorted = new ArrayList<>();
        Collections.addAll(sorted, found);
        Collections.sort(sorted, Comparator.comparing(
                (DocumentFile item) -> item.getName() == null ? "" : item.getName().toLowerCase()));
        for (DocumentFile file : sorted) {
            String name = file.getName();
            if (file.isFile() && name != null && name.toLowerCase().endsWith(suffix)) {
                files.add(file);
                labels.add(prefix + "  /  " + name);
            }
        }
    }

    private void openWorkspaceFile(DocumentFile file) {
        if (file == null || file.getName() == null) return;
        String name = file.getName();
        try {
            if (name.toLowerCase().endsWith(".al")) {
                String source;
                try (InputStream input = getContentResolver().openInputStream(file.getUri())) {
                    if (input == null) throw new IllegalStateException("Cannot open source file");
                    source = new String(readAll(input), StandardCharsets.UTF_8);
                }
                currentDocument = file;
                currentFileName = name;
                compiled = false;
                saveInternal(source);
                loadAlIntoWebApp(source, name);
                writeWorkspaceData();
                showOutputInWeb("Opened source file: " + name, "READY");
            } else if (name.toLowerCase().endsWith(".room")) {
                try (InputStream input = getContentResolver().openInputStream(file.getUri());
                     OutputStream output = new FileOutputStream(roomPath())) {
                    if (input == null) throw new IllegalStateException("Cannot open bytecode file");
                    copy(input, output);
                }
                compiled = true;
                showOutputInWeb("Loaded bytecode: " + name + "\nPress Execute .room to run it.", "LOADED");
            } else {
                showOutputInWeb("Unsupported file type: " + name, "ERROR");
            }
        } catch (Exception error) {
            showOutputInWeb("Open error: " + error.getMessage(), "ERROR");
        }
    }

    private void createNewFile() {
        if (!initWorkspaceFolders()) {
            openAfterFolderPick = true;
            requestWorkspaceFolder();
            return;
        }
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText("Main.al");
        nameInput.setSelection(nameInput.getText().length());
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(18), dp(4), dp(18), 0);
        wrapper.addView(nameInput);
        new AlertDialog.Builder(this)
                .setTitle("Create AL source file")
                .setMessage("Saved in ALVM Studio/Projects.")
                .setView(wrapper)
                .setPositiveButton("CREATE", (dialog, which) -> {
                    String name = normalizeAlFileName(nameInput.getText().toString());
                    if (name.contains("/") || name.contains("\\\\") || name.equals(".al")) {
                        showOutputInWeb("Use a simple file name.", "ERROR");
                        return;
                    }
                    if (projectsFolder.findFile(name) != null) {
                        showOutputInWeb("File already exists: " + name, "ERROR");
                        return;
                    }
                    DocumentFile created = projectsFolder.createFile("text/plain", name);
                    if (created == null) {
                        showOutputInWeb("Could not create " + name, "ERROR");
                        return;
                    }
                    currentDocument = created;
                    currentFileName = name;
                    String source = "room Main {\n    public static void main() {\n        AL.print(42);\n    }\n}\n";
                    saveProjectSourceNow(source);
                    loadAlIntoWebApp(source, name);
                    showOutputInWeb("Created " + name + " in Projects.", "READY");
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private boolean saveProjectSourceNow(String source) {
        if (!initWorkspaceFolders()) return false;
        try {
            if (currentDocument == null || !currentDocument.canWrite()) {
                currentDocument = projectsFolder.findFile(currentFileName);
            }
            if (currentDocument == null) currentDocument = projectsFolder.createFile("text/plain", currentFileName);
            if (currentDocument == null) throw new IllegalStateException("Could not create source file");
            try (OutputStream output = getContentResolver().openOutputStream(currentDocument.getUri(), "wt")) {
                if (output == null) throw new IllegalStateException("Cannot write project file");
                output.write(source.getBytes(StandardCharsets.UTF_8));
            }
            saveInternal(source);
            writeWorkspaceData();
            return true;
        } catch (Exception error) {
            showOutputInWeb("Save error: " + error.getMessage(), "ERROR");
            return false;
        }
    }

    private String saveProjectSource(String source, String name) {
        currentFileName = normalizeAlFileName(name);
        pendingSource = source == null ? "" : source;
        saveInternal(pendingSource);
        if (!initWorkspaceFolders()) {
            saveAfterFolderPick = true;
            runOnUiThread(() -> {
                showOutputInWeb("Choose a workspace folder and allow access to save your project.", "READY");
                requestWorkspaceFolder();
            });
            return "Folder permission required. Choose a folder in the Android picker.";
        }
        return saveProjectSourceNow(pendingSource)
                ? "✓ Saved: ALVM Studio/Projects/" + currentFileName
                : "✕ Could not save the project file.";
    }

    private void saveBuildFile() {
        if (!initWorkspaceFolders()) return;
        DocumentFile existing = buildFolder.findFile(buildFileName());
        if (existing != null) existing.delete();
        DocumentFile outputFile = buildFolder.createFile("application/octet-stream", buildFileName());
        if (outputFile == null) return;
        try (InputStream input = new FileInputStream(roomPath());
             OutputStream output = getContentResolver().openOutputStream(outputFile.getUri(), "wt")) {
            if (output == null) throw new IllegalStateException("Cannot write .room file");
            copy(input, output);
            writeWorkspaceData();
        } catch (Exception error) {
            showOutputInWeb("Bytecode saved locally, but Build export failed: " + error.getMessage(), "ERROR");
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
            if (metadata == null) return;
            try (OutputStream output = getContentResolver().openOutputStream(metadata.getUri(), "wt")) {
                if (output != null) output.write(state.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    private void importExternalFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_FILE);
    }

    private void requestWebFileChooser() {
        importExternalFile();
    }

    private byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private void showOpenImport(Uri uri) {
        try {
            String name = DocumentsContract.getDocumentId(uri);
            int colon = name.lastIndexOf(':');
            if (colon >= 0) name = name.substring(colon + 1);
            if (name.isEmpty()) name = "Imported.al";
            if (name.toLowerCase().endsWith(".room")) {
                try (InputStream input = getContentResolver().openInputStream(uri);
                     OutputStream output = new FileOutputStream(roomPath())) {
                    if (input == null) throw new IllegalStateException("Cannot read file");
                    copy(input, output);
                }
                currentFileName = name.replaceFirst("(?i)\\.room$", ".al");
                compiled = true;
                showOutputInWeb("Imported bytecode: " + name + "\nPress Execute .room to run it.", "LOADED");
            } else {
                String source;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Cannot read file");
                    source = new String(readAll(input), StandardCharsets.UTF_8);
                }
                currentFileName = normalizeAlFileName(name);
                currentDocument = null;
                compiled = false;
                saveInternal(source);
                loadAlIntoWebApp(source, currentFileName);
                showOutputInWeb("Imported source file: " + currentFileName, "READY");
            }
        } catch (Exception error) {
            showOutputInWeb("Import error: " + error.getMessage(), "ERROR");
        }
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public String runSource(String source, String name) {
            try {
                return runNative(source == null ? "" : source, name);
            } catch (Exception error) {
                return "✕ ALVM error: " + error.getMessage();
            }
        }

        @JavascriptInterface
        public String compileSource(String source, String name) {
            try {
                return compileNative(source == null ? "" : source, name);
            } catch (Exception error) {
                return "✕ AL compiler error: " + error.getMessage();
            }
        }

        @JavascriptInterface
        public String executeRoom() {
            return executeNativeRoom();
        }

        @JavascriptInterface
        public String saveSource(String source, String name) {
            return saveProjectSource(source == null ? "" : source, name);
        }

        @JavascriptInterface
        public void openWorkspace() {
            runOnUiThread(() -> openFileManager());
        }

        @JavascriptInterface
        public void createNewFile() {
            runOnUiThread(() -> MainActivity.this.createNewFile());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_WEB_FILE_CHOOSER && pendingFileCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                    ? new Uri[]{data.getData()} : null;
            pendingFileCallback.onReceiveValue(result);
            pendingFileCallback = null;
            if (result != null && result.length > 0) showOpenImport(result[0]);
            return;
        }

        if (requestCode == REQUEST_WORKSPACE_TREE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                showOutputInWeb("Folder access was not granted. Choose Open again to retry.", "ERROR");
                return;
            }
            Uri treeUri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(treeUri, flags);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_TREE_URI, treeUri.toString()).apply();
                currentDocument = null;
                if (!initWorkspaceFolders()) throw new IllegalStateException("Could not create workspace folders");
                showOutputInWeb("Folder permission saved. ALVM Studio/Projects, Build and Data are ready.", "READY");
                if (saveAfterFolderPick) {
                    saveAfterFolderPick = false;
                    saveProjectSourceNow(pendingSource);
                    showOutputInWeb("Saved: ALVM Studio/Projects/" + currentFileName, "READY");
                }
                if (openAfterFolderPick) {
                    openAfterFolderPick = false;
                    openFileManager();
                }
            } catch (Exception error) {
                showOutputInWeb("Folder setup error: " + error.getMessage(), "ERROR");
            }
            return;
        }

        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_IMPORT_FILE) {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try { getContentResolver().takePersistableUriPermission(data.getData(), flags); }
            catch (Exception ignored) { }
            showOpenImport(data.getData());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
