package com.alvmstudio;

import android.app.Activity;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class CiRuntimeHost {
    private static final String BUNDLED_VERSION = "2.0.0";

    static {
        System.loadLibrary("alvm");
    }

    private final Activity activity;

    private static native String nativeRunRoomJson(
            String roomPath, String runtimeRoot, String abi, String entryName, String eventValue);
    private static native String nativeStatusJson(String runtimeRoot, String abi);
    private static native String nativeInstallUpdateJson(String packagePath, String runtimeRoot, String abi);
    private static native String nativeRollbackJson(String runtimeRoot, String abi);

    public CiRuntimeHost(Activity activity) {
        this.activity = activity;
    }

    private String runtimeRoot() {
        return new File(activity.getFilesDir(), "ci-runtime").getAbsolutePath();
    }

    private String abi() {
        String[] supported = Build.SUPPORTED_ABIS;
        if (supported == null || supported.length == 0) {
            return "unsupported";
        }
        for (String value : supported) {
            if ("arm64-v8a".equals(value) || "armeabi-v7a".equals(value)) {
                return value;
            }
        }
        return supported[0];
    }

    private JSONObject parse(String raw, String fallbackMessage) {
        try {
            return new JSONObject(raw == null ? "" : raw);
        } catch (Exception error) {
            JSONObject failure = new JSONObject();
            try {
                failure.put("ok", false);
                failure.put("error", fallbackMessage + ": " + error.getMessage());
                failure.put("runtimeVersion", BUNDLED_VERSION);
            } catch (JSONException ignored) { }
            return failure;
        }
    }

    public JSONObject run(String roomPath, String entryName, String eventValue) {
        try {
            String raw = nativeRunRoomJson(
                    roomPath,
                    runtimeRoot(),
                    abi(),
                    entryName == null ? "main" : entryName,
                    eventValue == null ? "" : eventValue);
            return parse(raw, "Rust CI Runtime returned invalid JSON");
        } catch (Throwable error) {
            return failure("Rust CI Runtime host error: " + error.getMessage());
        }
    }

    public JSONObject status() {
        try {
            return parse(nativeStatusJson(runtimeRoot(), abi()),
                    "Rust CI Runtime status could not be read");
        } catch (Throwable error) {
            return failure("Rust CI Runtime status error: " + error.getMessage());
        }
    }

    public JSONObject installUpdate(String packagePath) {
        try {
            return parse(nativeInstallUpdateJson(packagePath, runtimeRoot(), abi()),
                    "Rust CI Runtime update returned invalid JSON");
        } catch (Throwable error) {
            return failure("Rust CI Runtime install error: " + error.getMessage());
        }
    }

    public JSONObject rollback() {
        try {
            return parse(nativeRollbackJson(runtimeRoot(), abi()),
                    "Rust CI Runtime rollback returned invalid JSON");
        } catch (Throwable error) {
            return failure("Rust CI Runtime rollback error: " + error.getMessage());
        }
    }

    private JSONObject failure(String message) {
        JSONObject failure = new JSONObject();
        try {
            failure.put("ok", false);
            failure.put("error", message == null ? "Unknown Rust CI Runtime error" : message);
            failure.put("runtimeVersion", BUNDLED_VERSION);
            JSONObject ui = new JSONObject();
            ui.put("title", "AL Application");
            ui.put("elements", new org.json.JSONArray());
            failure.put("ui", ui);
        } catch (JSONException ignored) { }
        return failure;
    }
}
