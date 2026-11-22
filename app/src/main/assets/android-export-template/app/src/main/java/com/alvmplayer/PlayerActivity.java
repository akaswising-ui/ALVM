package com.alvmplayer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alvmstudio.CiRuntimeHost;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Standalone Android host for one compiled AL .room program.
 * It executes verified .room bytecode using the shared Rust CI Runtime.
 */
public final class PlayerActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CiRuntimeHost runtime;
    private File roomFile;
    private LinearLayout root;
    private LinearLayout content;
    private TextView outputView;
    private String frameCallback = "";
    private int frameRate = 0;
    private Runnable frameTask;
    private boolean paused = true;
    private JSONArray currentElements = new JSONArray();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(17, 24, 39));
        getWindow().setNavigationBarColor(Color.rgb(12, 17, 28));
        try {
            roomFile = new File(getFilesDir(), "main.room");
            copyBundledRoom();
            runtime = new CiRuntimeHost(this);
            buildShell();
            runEvent("main", "");
        } catch (Exception error) {
            showFatal("Could not start this AL application: " + error.getMessage());
        }
    }

    private void copyBundledRoom() throws Exception {
        try (InputStream input = getAssets().open("main.room");
             FileOutputStream output = new FileOutputStream(roomFile)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        if (roomFile.length() < 8) {
            throw new IllegalStateException("The embedded .room bytecode is empty or truncated.");
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(Color.rgb(10, 15, 25));

        TextView appTitle = new TextView(this);
        appTitle.setText(getString(com.alvmplayer.R.string.app_name));
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(20);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appTitle.setPadding(0, 0, 0, dp(8));
        root.addView(appTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        outputView = new TextView(this);
        outputView.setTextColor(Color.rgb(176, 194, 220));
        outputView.setTextSize(13);
        outputView.setPadding(dp(10), dp(8), dp(10), dp(8));
        outputView.setBackgroundColor(Color.rgb(20, 29, 45));
        outputView.setVisibility(View.GONE);
        root.addView(outputView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView languageBrand = new TextView(this);
        languageBrand.setText(getString(R.string.made_with_al));
        languageBrand.setTextColor(Color.rgb(126, 154, 198));
        languageBrand.setTextSize(11);
        languageBrand.setGravity(Gravity.CENTER);
        languageBrand.setLetterSpacing(0.08f);
        languageBrand.setPadding(0, dp(10), 0, dp(2));
        languageBrand.setContentDescription(getString(R.string.al_language_description));
        root.addView(languageBrand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void showFatal(String message) {
        if (root == null) buildShell();
        outputView.setVisibility(View.VISIBLE);
        outputView.setText(message);
        content.removeAllViews();
    }

    private void runEvent(String entryName, String eventValue) {
        if (runtime == null || roomFile == null) return;
        JSONObject result = runtime.run(roomFile.getAbsolutePath(), entryName, eventValue);
        applyResult(result, true);
    }

    private void applyResult(JSONObject result, boolean updateLoop) {
        if (result == null || !result.optBoolean("ok", false)) {
            stopFrameLoop();
            String error = result == null ? "Runtime returned no result."
                    : result.optString("error", "AL runtime execution failed.");
            outputView.setVisibility(View.VISIBLE);
            outputView.setText("Runtime error: " + error);
            content.removeAllViews();
            return;
        }

        String output = result.optString("output", "");
        if (!output.trim().isEmpty()) {
            outputView.setVisibility(View.VISIBLE);
            outputView.setText(output);
        } else if (outputView.getText().length() == 0) {
            outputView.setVisibility(View.GONE);
        }

        JSONObject ui = result.optJSONObject("ui");
        if (ui != null) renderUi(ui);
        if (updateLoop && ui != null) configureFrameLoop(ui);
    }

    private void renderUi(JSONObject ui) {
        content.removeAllViews();
        currentElements = ui.optJSONArray("elements");
        if (currentElements == null) currentElements = new JSONArray();

        if (!ui.optBoolean("visible", false)) {
            TextView message = makeText("This AL program did not open a window.");
            content.addView(message);
            return;
        }

        TextView heading = makeText(ui.optString("title", getString(R.string.app_name)));
        heading.setTextSize(21);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading);

        boolean hasScene = false;
        for (int i = 0; i < currentElements.length(); i++) {
            JSONObject item = currentElements.optJSONObject(i);
            if (item == null) continue;
            String kind = item.optString("kind", "");
            if ("rect".equals(kind) || "circle".equals(kind)
                    || "line".equals(kind) || "drawText".equals(kind)
                    || "sprite".equals(kind) || "touchArea".equals(kind)) {
                hasScene = true;
                break;
            }
        }
        if (hasScene) {
            SceneView scene = new SceneView(currentElements);
            LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(480));
            sceneParams.bottomMargin = dp(10);
            content.addView(scene, sceneParams);
        }

        for (int i = 0; i < currentElements.length(); i++) {
            JSONObject item = currentElements.optJSONObject(i);
            if (item == null) continue;
            String kind = item.optString("kind", "");
            String id = item.optString("id", "");
            String value = item.optString("text", "");
            if ("text".equals(kind)) {
                TextView label = makeText(value);
                label.setTag(id);
                content.addView(label);
            } else if ("button".equals(kind)) {
                Button button = new Button(this);
                button.setText(value);
                button.setAllCaps(false);
                button.setTextSize(17);
                button.setTextColor(Color.WHITE);
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        Color.rgb(55, 72, 110)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
                params.setMargins(0, dp(4), 0, dp(4));
                content.addView(button, params);
                String callback = item.optString("callback", "");
                button.setOnClickListener(view -> {
                    if (!callback.isEmpty()) runEvent(callback, id);
                });
            }
        }
    }

    private TextView makeText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.rgb(235, 241, 251));
        text.setTextSize(19);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(8), dp(12), dp(8), dp(12));
        return text;
    }

    private void configureFrameLoop(JSONObject ui) {
        String callback = ui.optString("frameCallback", "");
        int fps = ui.optInt("frameRate", 0);
        if (callback.trim().isEmpty() || fps < 1 || fps > 60) {
            stopFrameLoop();
            return;
        }
        if (callback.equals(frameCallback) && fps == frameRate && frameTask != null) return;

        stopFrameLoop();
        frameCallback = callback;
        frameRate = fps;
        frameTask = new Runnable() {
            @Override
            public void run() {
                if (paused || isFinishing() || !callback.equals(frameCallback)) return;
                JSONObject frame = runtime.run(roomFile.getAbsolutePath(), callback, "frame");
                applyResult(frame, true);
                if (frameTask == this && !paused) {
                    handler.postDelayed(this, Math.max(16, 1000 / frameRate));
                }
            }
        };
        if (!paused) handler.postDelayed(frameTask, Math.max(16, 1000 / frameRate));
    }

    private void stopFrameLoop() {
        if (frameTask != null) handler.removeCallbacks(frameTask);
        frameTask = null;
        frameCallback = "";
        frameRate = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        if (frameTask != null) handler.postDelayed(frameTask, Math.max(16, 1000 / Math.max(1, frameRate)));
    }

    @Override
    protected void onPause() {
        paused = true;
        if (frameTask != null) handler.removeCallbacks(frameTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopFrameLoop();
        super.onDestroy();
    }

    private final class SceneView extends View {
        private final JSONArray elements;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<String, Bitmap> spriteCache = new HashMap<>();

        SceneView(JSONArray elements) {
            super(PlayerActivity.this);
            this.elements = elements;
            setBackgroundColor(Color.rgb(8, 12, 22));
        }

        private boolean isSafeAssetPath(String assetPath) {
            if (assetPath == null || assetPath.isEmpty() || assetPath.length() > 240
                    || assetPath.startsWith("/") || assetPath.indexOf((char) 92) >= 0) return false;
            String[] parts = assetPath.split("/", -1);
            for (String part : parts) {
                if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
                for (int i = 0; i < part.length(); i++) {
                    char ch = part.charAt(i);
                    if (!((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                            || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.')) return false;
                }
            }
            return true;
        }

        private Bitmap loadSprite(String assetPath) {
            if (!isSafeAssetPath(assetPath)) return null;
            if (spriteCache.containsKey(assetPath)) return spriteCache.get(assetPath);
            Bitmap bitmap = null;
            try (InputStream input = PlayerActivity.this.getAssets().open(assetPath)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeStream(input, null, options);
            } catch (Exception ignored) {
                // Missing optional sprites are drawn as a visible placeholder.
            }
            spriteCache.put(assetPath, bitmap);
            return bitmap;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 320f, getHeight() / 480f);
            if (scale <= 0f) return;
            float offsetX = (getWidth() - 320f * scale) / 2f;
            float offsetY = (getHeight() - 480f * scale) / 2f;
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(scale, scale);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(8, 12, 22));
            canvas.drawRect(0, 0, 320, 480, paint);
            for (int i = 0; i < elements.length(); i++) {
                JSONObject item = elements.optJSONObject(i);
                if (item == null) continue;
                String kind = item.optString("kind", "");
                int x = item.optInt("x", 0);
                int y = item.optInt("y", 0);
                int w = item.optInt("w", 0);
                int h = item.optInt("h", 0);
                paint.setColor((int) item.optLong("color", 0xFFFFFFFFL));
                paint.setStrokeWidth(3f);
                if ("sprite".equals(kind)) {
                    Bitmap bitmap = loadSprite(item.optString("text", ""));
                    if (bitmap != null && w > 0 && h > 0) {
                        paint.setAlpha(255);
                        paint.setFilterBitmap(true);
                        canvas.drawBitmap(bitmap, null, new android.graphics.Rect(x, y, x + w, y + h), paint);
                    } else {
                        paint.setColor(Color.rgb(190, 65, 105));
                        canvas.drawRect(x, y, x + Math.max(1, w), y + Math.max(1, h), paint);
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        canvas.drawLine(x, y, x + Math.max(1, w), y + Math.max(1, h), paint);
                        canvas.drawLine(x + Math.max(1, w), y, x, y + Math.max(1, h), paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                } else if ("drawText".equals(kind)) {
                    paint.setTextSize(Math.max(6f, Math.min(96f, w)));
                    canvas.drawText(item.optString("text", ""), x, y, paint);
                } else if ("rect".equals(kind)) {
                    canvas.drawRect(x, y, x + w, y + h, paint);
                } else if ("circle".equals(kind)) {
                    canvas.drawCircle(x, y, w, paint);
                } else if ("line".equals(kind)) {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawLine(x, y, w, h, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float scale = Math.min(getWidth() / 320f, getHeight() / 480f);
            if (scale <= 0f) return true;
            float offsetX = (getWidth() - 320f * scale) / 2f;
            float offsetY = (getHeight() - 480f * scale) / 2f;
            int x = Math.round((event.getX() - offsetX) / scale);
            int y = Math.round((event.getY() - offsetY) / scale);
            for (int index = elements.length() - 1; index >= 0; index--) {
                JSONObject item = elements.optJSONObject(index);
                if (item == null || !"touchArea".equals(item.optString("kind", ""))) continue;
                int left = item.optInt("x", 0);
                int top = item.optInt("y", 0);
                if (x >= left && x < left + item.optInt("w", 0)
                        && y >= top && y < top + item.optInt("h", 0)) {
                    String callback = item.optString("callback", "");
                    String id = item.optString("id", "");
                    if (!callback.isEmpty() && !id.isEmpty()) {
                        runEvent(callback, "@touch|" + id + "|" + x + "|" + y);
                    }
                    return true;
                }
            }
            return true;
        }
    }
}
