package com.alvmstudio;

import android.app.Activity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class CiRuntimeHost {
    private final Activity activity;
    private Python python;
    private PyObject manager;

    public CiRuntimeHost(Activity activity) {
        this.activity = activity;
    }

    private synchronized PyObject manager() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(activity.getApplicationContext()));
        }
        if (python == null) {
            python = Python.getInstance();
            manager = python.getModule("ci_runtime_manager");
        }
        return manager;
    }

    private String runtimeRoot() {
        return new File(activity.getFilesDir(), "ci-runtime").getAbsolutePath();
    }

    private JSONObject call(String method, Object... args) {
        try {
            String raw = manager().callAttr(method, args).toString();
            return new JSONObject(raw);
        } catch (Exception error) {
            JSONObject failure = new JSONObject();
            try {
                failure.put("ok", false);
                failure.put("error", "CI Runtime host error: " + error.getMessage());
            } catch (JSONException ignored) { }
            return failure;
        }
    }

    public JSONObject run(String roomPath, String entryName, String eventValue) {
        return call("run_room_json", roomPath, runtimeRoot(),
                entryName == null ? "main" : entryName,
                eventValue == null ? "" : eventValue);
    }

    public JSONObject status() {
        return call("status_json", runtimeRoot());
    }

    public JSONObject installUpdate(String packagePath) {
        return call("install_update", packagePath, runtimeRoot());
    }

    public JSONObject rollback() {
        return call("rollback", runtimeRoot());
    }
}
