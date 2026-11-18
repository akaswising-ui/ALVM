package com.alvmstudio;

import android.app.Activity;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;

/**
 * Thin Android bridge to ALVM's bundled Rust CI Runtime.
 * The JNI symbol is implemented by the libalvm.so exported by ALVM Studio.
 */
public final class CiRuntimeHost {
    static {
        System.loadLibrary("alvm");
    }

    private final Activity activity;

    private static native String nativeRunRoomJson(
            String roomPath, String runtimeRoot, String abi, String entryName, String eventValue);

    public CiRuntimeHost(Activity activity) {
        this.activity = activity;
    }

    private String runtimeRoot() {
        return new File(activity.getFilesDir(), "ci-runtime").getAbsolutePath();
    }

    private String abi() {
        String[] supported = Build.SUPPORTED_ABIS;
        if (supported == null || supported.length == 0) return "unsupported";
        for (String value : supported) {
            if ("arm64-v8a".equals(value) || "armeabi-v7a".equals(value)) return value;
        }
        return supported[0];
    }

    public JSONObject run(String roomPath, String entryName, String eventValue) {
        try {
            String raw = nativeRunRoomJson(
                    roomPath,
                    runtimeRoot(),
                    abi(),
                    entryName == null ? "main" : entryName,
                    eventValue == null ? "" : eventValue);
            return new JSONObject(raw == null ? "" : raw);
        } catch (Throwable error) {
            JSONObject failed = new JSONObject();
            try {
                failed.put("ok", false);
                failed.put("error", "ALVM Runtime error: " + error.getMessage());
                failed.put("output", "");
            } catch (Exception ignored) { }
            return failed;
        }
    }
}
