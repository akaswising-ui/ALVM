package com.alvmstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_WORKSPACE_TREE = 13;
    private static final int REQUEST_IMPORT_FILE = 14;
    private static final int REQUEST_WEB_FILE_CHOOSER = 15;
    private static final int REQUEST_RUNTIME_UPDATE = 16;
    private static final String PREFS = "alvm_workspace";
    private static final String PREF_TREE_URI = "workspace_tree_uri";

    static {
        System.loadLibrary("alvm");
    }

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> pendingFileCallback;
    private boolean saveAfterFolderPick = false;
    private boolean openAfterFolderPick = false;
    private CiRuntimeHost ciRuntime;
    private final ExecutorService runtimeUpdateExecutor = Executors.newSingleThreadExecutor();
    private String lastRuntimeUiJson = "";
    private String lastRuntimeVersion = "";

    private DocumentFile workspaceRoot;
    private DocumentFile appFolder;
    private DocumentFile projectsFolder;
    private DocumentFile dataFolder;
    private DocumentFile buildFolder;
    private DocumentFile currentDocument;

    private String currentFileName = "Main.al";
    private String pendingSource = "";
    private boolean compiled = false;
    private Dialog nativeUiDialog;

    private static native String nativeCompile(String source, String filesDir);
    private static native String nativeRun(String roomPath);
    private static native String nativeDisassemble(String roomPath);
    private static native String nativeGetUiJson();
    private static native String nativeRunUiEvent(String roomPath, String callback, String eventValue);

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
        ciRuntime = new CiRuntimeHost(this);
        getWindow().setStatusBarColor(Color.rgb(16, 22, 34));
        getWindow().setNavigationBarColor(Color.rgb(9, 13, 21));
        initWorkspaceFolders();

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

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
        settings.setAllowFileAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                boolean localAsset = "https".equalsIgnoreCase(uri.getScheme())
                        && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())
                        && uri.getPath() != null && uri.getPath().startsWith("/assets/");
                return !localAsset;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://appassets.androidplatform.net/assets/")) installNativeUiHooks();
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
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
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

    private String runCiRuntime(String entryName, String eventValue) {
        if (ciRuntime == null) ciRuntime = new CiRuntimeHost(this);
        JSONObject result = ciRuntime.run(roomPath(), entryName, eventValue);
        lastRuntimeVersion = result.optString("runtimeVersion", lastRuntimeVersion);
        JSONObject ui = result.optJSONObject("ui");
        if (ui != null) lastRuntimeUiJson = ui.toString();
        publishNativeUi();
        if (!result.optBoolean("ok", false)) {
            return "✕ " + result.optString("error", "CI Runtime execution failed.");
        }
        return result.optString("output", "");
    }

    private String runNative(String source, String fileName) {
        String result = compileNative(source, fileName);
        if (!compiled) return result;
        String output = runCiRuntime("main", "");
        if (output.startsWith("✕ ")) return output;
        return "✓ Program completed in Python CI Runtime v" + lastRuntimeVersion + ".\n\n"
                + (output.trim().isEmpty() ? "(program produced no console output)" : "AL OUTPUT  " + output.trim().replace("\n", "\nAL OUTPUT  "));
    }

    private String executeNativeRoom() {
        File room = new File(roomPath());
        if (!room.exists()) return "✕ No compiled .room file is loaded.";
        String output = runCiRuntime("main", "");
        if (output.startsWith("✕ ")) return output;
        return "✓ .room execution complete in Python CI Runtime v" + lastRuntimeVersion + ".\n\n"
                + (output.trim().isEmpty()
                ? "(program produced no console output)"
                : "AL OUTPUT  " + output.trim().replace("\n", "\nAL OUTPUT  "));
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
                .setPositiveButton("CI RUNTIME", (d, which) -> showCiRuntimeManager())
                .setNeutralButton("IMPORT FILE", (d, which) -> importExternalFile())
                .setNegativeButton("NEW .AL FILE", (d, which) -> createNewFile());
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

    private String currentCiRuntimeVersion() {
        JSONObject status = ciRuntime.status();
        String active = status.optString("active_version", "bundled");
        return "bundled".equals(active)
                ? status.optString("bundled_version", "1.0.0") : active;
    }

    private String installedAppVersion() {
        try {
            String value = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return value == null || value.trim().isEmpty() ? "unknown" : value;
        } catch (Exception error) {
            return "unknown";
        }
    }

    private void publishRuntimeVersion() {
        if (webView == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("version", currentCiRuntimeVersion());
            webView.evaluateJavascript(
                    "(function(p){if(window.__alvmSetRuntimeVersion)window.__alvmSetRuntimeVersion(p.version);})("
                            + payload.toString() + ");", null);
        } catch (Exception ignored) { }
    }

    private void showCiRuntimeManager() {
        JSONObject status = ciRuntime.status();
        String active = status.optString("active_version", "bundled");
        if ("bundled".equals(active)) active = status.optString("bundled_version", "1.0.0");
        StringBuilder previous = new StringBuilder();
        JSONArray versions = status.optJSONArray("previous_versions");
        if (versions != null) {
            for (int i = 0; i < versions.length(); i++) {
                if (i > 0) previous.append(", ");
                String version = versions.optString(i);
                previous.append("bundled".equals(version)
                        ? status.optString("bundled_version", "1.0.0") : version);
            }
        }
        String message = "App version: " + installedAppVersion()
                + "\nCI Runtime: " + active
                + "\n.room format: " + status.optInt("room_format", 9)
                + "\nRuntime history: " + (previous.length() == 0 ? "none" : previous);
        String[] actions = {
                "DOWNLOAD LATEST RUNTIME",
                "INSTALL FROM FILE",
                "ROLLBACK",
                "CLOSE"
        };
        new AlertDialog.Builder(this)
                .setTitle("CI Runtime Manager")
                .setMessage(message)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) downloadLatestCiRuntime();
                    else if (which == 1) requestCiRuntimeUpdate();
                    else if (which == 2) rollbackCiRuntime();
                })
                .show();
    }

    private void downloadLatestCiRuntime() {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("CI Runtime");
        progress.setMessage("Checking published Runtime versions...");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        runtimeUpdateExecutor.execute(() -> {
            File packageFile = null;
            try {
                CiRuntimeUpdater.Release latest = CiRuntimeUpdater.findLatest();
                if (latest == null) {
                    throw new IllegalStateException("No CI Runtime release has been published yet.");
                }
                String installed = currentCiRuntimeVersion();
                if (CiRuntimeUpdater.compareVersions(latest.version, installed) <= 0) {
                    runOnUiThread(() -> {
                        if (!isFinishing()) {
                            progress.dismiss();
                            new AlertDialog.Builder(this)
                                    .setTitle("CI Runtime")
                                    .setMessage("Installed: " + installed + "\nLatest: " + latest.version
                                            + "\n\nNo newer Runtime version is available.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                    return;
                }

                packageFile = new File(getCacheDir(), "ci-runtime-" + latest.version + ".ciruntime");
                CiRuntimeUpdater.download(latest, packageFile);
                JSONObject result = ciRuntime.installUpdate(packageFile.getAbsolutePath());
                if (!result.optBoolean("ok", false)) {
                    throw new IllegalStateException(result.optString("error", "Runtime installation failed"));
                }
                String activeVersion = result.optString("active_version", latest.version);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        progress.dismiss();
                        publishRuntimeVersion();
                        String message = "CI Runtime " + activeVersion + " downloaded and installed.\n"
                                + "The next .room execution will use this version.";
                        showOutputInWeb("✓ " + message, "SUCCESS");
                        new AlertDialog.Builder(this)
                                .setTitle("CI Runtime updated")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            } catch (Exception error) {
                String errorMessage = error.getMessage() == null ? "Unknown download error" : error.getMessage();
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        progress.dismiss();
                        showOutputInWeb("✕ CI Runtime download failed: " + errorMessage, "ERROR");
                        new AlertDialog.Builder(this)
                                .setTitle("CI Runtime download failed")
                                .setMessage(errorMessage)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            } finally {
                if (packageFile != null) packageFile.delete();
            }
        });
    }

    private void requestCiRuntimeUpdate() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_RUNTIME_UPDATE);
        } catch (Exception error) {
            showOutputInWeb("Could not open the runtime update picker: " + error.getMessage(), "ERROR");
        }
    }

    private void installCiRuntimeUpdate(Uri uri) {
        File packageFile = new File(getCacheDir(), "ci-runtime-update.ciruntime");
        try (InputStream input = getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(packageFile)) {
            if (input == null) throw new IllegalStateException("Cannot read runtime package");
            copy(input, output);
        } catch (Exception error) {
            showOutputInWeb("Runtime update import failed: " + error.getMessage(), "ERROR");
            return;
        }

        JSONObject result = ciRuntime.installUpdate(packageFile.getAbsolutePath());
        packageFile.delete();
        if (result.optBoolean("ok", false)) {
            lastRuntimeVersion = result.optString("active_version", "");
            publishRuntimeVersion();
            String message = "✓ " + result.optString("message", "CI Runtime updated")
                    + "\nActive version: " + lastRuntimeVersion;
            showOutputInWeb(message, "SUCCESS");
            new AlertDialog.Builder(this)
                    .setTitle("CI Runtime updated")
                    .setMessage(message + "\n\nThe new runtime will be used by the next .room execution.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            String message = "✕ Runtime update rejected: " + result.optString("error", "Unknown error");
            showOutputInWeb(message, "ERROR");
            new AlertDialog.Builder(this)
                    .setTitle("Runtime update rejected")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void rollbackCiRuntime() {
        JSONObject result = ciRuntime.rollback();
        if (result.optBoolean("ok", false)) {
            lastRuntimeVersion = result.optString("active_version", "");
            publishRuntimeVersion();
            showOutputInWeb("✓ CI Runtime rolled back to " + lastRuntimeVersion, "SUCCESS");
            new AlertDialog.Builder(this)
                    .setTitle("CI Runtime rollback")
                    .setMessage("Active version: " + lastRuntimeVersion
                            + "\nThe previous runtime will be used by the next .room execution.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            String message = "Rollback unavailable: " + result.optString("error", "No previous version");
            showOutputInWeb(message, "ERROR");
        }
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
            if (name.toLowerCase().endsWith(".ciruntime")) {
                installCiRuntimeUpdate(uri);
                return;
            }
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

    private void publishNativeUi() {
        final String json = lastRuntimeUiJson;
        if (json == null || json.trim().isEmpty()) return;
        runOnUiThread(() -> showNativeUi(json));
    }

    private void showNativeUi(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray elements = root.optJSONArray("elements");
            if (elements == null || elements.length() == 0) {
                if (nativeUiDialog != null && nativeUiDialog.isShowing()) nativeUiDialog.dismiss();
                nativeUiDialog = null;
                return;
            }
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(18), dp(16), dp(18), dp(18));
            content.setBackground(roundDrawable(Color.rgb(15, 23, 38), dp(20)));

            GridLayout buttonGrid = new GridLayout(this);
            buttonGrid.setColumnCount(4);
            buttonGrid.setUseDefaultMargins(true);

            TextView heading = new TextView(this);
            heading.setText(root.optString("title", "AL Application"));
            heading.setTextColor(Color.WHITE);
            heading.setTextSize(21);
            heading.setTypeface(null, android.graphics.Typeface.BOLD);
            heading.setPadding(0, 0, 0, dp(14));
            content.addView(heading, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            boolean hasGameCanvas = false;
            for (int index = 0; index < elements.length(); index++) {
                JSONObject item = elements.optJSONObject(index);
                if (item != null) {
                    String kind = item.optString("kind", "");
                    if ("rect".equals(kind) || "circle".equals(kind) || "line".equals(kind)) {
                        hasGameCanvas = true;
                        break;
                    }
                }
            }
            if (hasGameCanvas) {
                Game2DView gameView = new Game2DView(this, elements);
                LinearLayout.LayoutParams gameParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(480));
                gameParams.bottomMargin = dp(12);
                content.addView(gameView, gameParams);
            }

            for (int index = 0; index < elements.length(); index++) {
                JSONObject element = elements.optJSONObject(index);
                if (element == null) continue;
                String kind = element.optString("kind", "");
                String id = element.optString("id", "");
                String text = element.optString("text", "");
                if ("rect".equals(kind) || "circle".equals(kind) || "line".equals(kind)) {
                    continue;
                } else if ("text".equals(kind)) {
                    TextView label = new TextView(this);
                    label.setTag(id);
                    label.setText(text);
                    label.setTextColor(Color.rgb(235, 241, 251));
                    label.setTextSize(25);
                    label.setGravity(Gravity.CENTER);
                    label.setPadding(dp(8), dp(18), dp(8), dp(18));
                    content.addView(label, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else if ("button".equals(kind)) {
                    String callback = element.optString("callback", "");
                    Button button = new Button(this);
                    button.setText(text);
                    button.setAllCaps(false);
                    button.setTextColor(Color.WHITE);
                    button.setTextSize(18);
                    button.setBackground(roundDrawable(Color.rgb(55, 68, 103), dp(12)));
                    button.setMinHeight(dp(52));
                    button.setPadding(dp(2), dp(2), dp(2), dp(2));
                    button.setIncludeFontPadding(false);
                    GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                            GridLayout.spec(GridLayout.UNDEFINED),
                            GridLayout.spec(GridLayout.UNDEFINED, 1f));
                    params.width = 0;
                    params.height = dp(58);
                    params.setMargins(dp(4), dp(4), dp(4), dp(4));
                    params.setGravity(Gravity.FILL);
                    buttonGrid.addView(button, params);
                    button.setOnClickListener(view -> {
                        try {
                            String result = runCiRuntime(callback, id);
                            if (result != null && result.trim().length() > 0) {
                                showOutputInWeb(result, result.toLowerCase().contains("error")
                                        ? "RUNTIME ERROR" : "SUCCESS");
                            }
                            if (!lastRuntimeUiJson.isEmpty()) showNativeUi(lastRuntimeUiJson);
                        } catch (Exception error) {
                            showOutputInWeb("AL UI event error: " + error.getMessage(), "RUNTIME ERROR");
                        }
                    });
                }
            }

            if (buttonGrid.getChildCount() > 0) {
                LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gridParams.topMargin = dp(8);
                content.addView(buttonGrid, gridParams);
            }

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.addView(content);
            if (nativeUiDialog == null) nativeUiDialog = new Dialog(this);
            nativeUiDialog.setContentView(scroll);
            nativeUiDialog.setCancelable(true);
            if (!nativeUiDialog.isShowing()) nativeUiDialog.show();
            if (nativeUiDialog.getWindow() != null) {
                nativeUiDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int dialogWidth = Math.min(screenWidth - dp(28), dp(430));
                int dialogHeight = Math.min((int) (screenHeight * 0.78f), dp(620));
                nativeUiDialog.getWindow().setLayout(dialogWidth, dialogHeight);
            }
        } catch (Exception error) {
            showOutputInWeb("Could not render AL UI: " + error.getMessage(), "ERROR");
        }
    }

    private GradientDrawable roundDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.rgb(74, 88, 120));
        return drawable;
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
        public String getAppVersion() {
            try {
                String version = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;
                return version == null || version.trim().isEmpty() ? "unknown" : version;
            } catch (Exception error) {
                return "unknown";
            }
        }

        @JavascriptInterface
        public void openWorkspace() {
            runOnUiThread(() -> openFileManager());
        }

        @JavascriptInterface
        public void importFile() {
            runOnUiThread(() -> importExternalFile());
        }

        @JavascriptInterface
        public void showCiRuntimeManager() {
            runOnUiThread(() -> MainActivity.this.showCiRuntimeManager());
        }

        @JavascriptInterface
        public String getCiRuntimeVersion() {
            try { return currentCiRuntimeVersion(); }
            catch (Exception error) { return "unknown"; }
        }

        @JavascriptInterface
        public void createNewFile() {
            runOnUiThread(() -> MainActivity.this.createNewFile());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_RUNTIME_UPDATE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                installCiRuntimeUpdate(data.getData());
            } else {
                showOutputInWeb("CI Runtime update cancelled.", "READY");
            }
            return;
        }

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
        runtimeUpdateExecutor.shutdownNow();
        if (nativeUiDialog != null) { nativeUiDialog.dismiss(); nativeUiDialog = null; }
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
