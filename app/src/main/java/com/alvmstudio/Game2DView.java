package com.alvmstudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/** Native 320x480 logical-coordinate renderer for AL's 2D scene and projected 3D primitives. */
public final class Game2DView extends View {
    public interface TouchHandler {
        void onTouch(String callback, String id, int x, int y);
        default void onKey(String callback, String key) { }
    }

    private volatile JSONArray elements;
    private volatile JSONObject camera;
    private final TouchHandler touchHandler;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Bitmap> spriteCache = new HashMap<>();
    private final Map<String, Long> animationStarts = new HashMap<>();

    public Game2DView(Context context, JSONArray elements, JSONObject camera, TouchHandler touchHandler) {
        super(context);
        this.elements = elements;
        this.camera = camera == null ? new JSONObject() : camera;
        this.touchHandler = touchHandler;
        setBackgroundColor(Color.rgb(8, 12, 22));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    /** Refresh the existing native canvas without replacing its View or losing keyboard focus. */
    public void updateRuntimeUi(String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            JSONObject ui = new JSONObject(json);
            JSONObject nested = ui.optJSONObject("ui");
            if (nested != null) ui = nested;
            JSONArray next = ui.optJSONArray("elements");
            if (next != null) elements = next;
            JSONObject nextCamera = ui.optJSONObject("camera3D");
            if (nextCamera != null) camera = nextCamera;
            invalidate();
        } catch (Exception ignored) {
            // Invalid event output must not crash the game canvas.
        }
    }

    private JSONObject findElement(String id) {
        if (elements == null || id == null || id.isEmpty()) return null;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject item = elements.optJSONObject(i);
            if (item != null && id.equals(item.optString("id", ""))) return item;
        }
        return null;
    }

    private void applyParentTransforms(Canvas canvas, JSONObject item) {
        ArrayList<JSONObject> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String parent = item.optString("parent", "");
        for (int depth = 0; depth < 64 && !parent.isEmpty(); depth++) {
            if (!visited.add(parent)) break;
            JSONObject node = findElement(parent);
            if (node == null || !"node".equals(node.optString("kind", ""))) break;
            chain.add(node);
            parent = node.optString("parent", "");
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            JSONObject node = chain.get(i);
            canvas.translate(node.optInt("x", 0), node.optInt("y", 0));
            canvas.rotate((float) node.optDouble("rotation", 0.0));
            canvas.scale((float) node.optDouble("scaleX", 1.0), (float) node.optDouble("scaleY", 1.0));
        }
    }

    private void applyItemTransform(Canvas canvas, JSONObject item) {
        float x = item.optInt("x", 0);
        float y = item.optInt("y", 0);
        float rotation = (float) item.optDouble("rotation", 0.0);
        float sx = (float) item.optDouble("scaleX", 1.0);
        float sy = (float) item.optDouble("scaleY", 1.0);
        if (rotation == 0f && sx == 1f && sy == 1f) return;
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(sx, sy);
        canvas.translate(-x, -y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        if (action != MotionEvent.ACTION_UP) return true;

        try {
            if (touchHandler == null || elements == null) return true;
            final float scale = Math.min(getWidth() / 320f, getHeight() / 480f);
            if (scale <= 0f) return true;
            final float offsetX = (getWidth() - 320f * scale) / 2f;
            final float offsetY = (getHeight() - 480f * scale) / 2f;
            final int x = Math.round((event.getX() - offsetX) / scale);
            final int y = Math.round((event.getY() - offsetY) / scale);
            for (int index = elements.length() - 1; index >= 0; index--) {
                JSONObject item = elements.optJSONObject(index);
                if (item == null || !"touchArea".equals(item.optString("kind", ""))) continue;
                int left = item.optInt("x", 0);
                int top = item.optInt("y", 0);
                int right = left + item.optInt("w", 0);
                int bottom = top + item.optInt("h", 0);
                if (x >= left && x < right && y >= top && y < bottom) {
                    String callback = item.optString("callback", "");
                    String id = item.optString("id", "");
                    if (!callback.isEmpty() && !id.isEmpty()) touchHandler.onTouch(callback, id, x, y);
                    break;
                }
            }
            return true;
        } finally {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private String keyName(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_SPACE: return "SPACE";
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER: return "ENTER";
            case KeyEvent.KEYCODE_ESCAPE: return "ESCAPE";
            default:
                int unicode = event == null ? 0 : event.getUnicodeChar();
                if (unicode >= 'a' && unicode <= 'z') unicode -= ('a' - 'A');
                if (unicode >= 'A' && unicode <= 'Z') return String.valueOf((char) unicode);
                return null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String key = keyName(keyCode, event);
        if (key != null && touchHandler != null && elements != null) {
            boolean handled = false;
            for (int i = 0; i < elements.length(); i++) {
                JSONObject item = elements.optJSONObject(i);
                if (item == null || !"keyArea".equals(item.optString("kind", ""))) continue;
                String wanted = item.optString("text", "");
                if (wanted.equals("ANY") || wanted.equals(key)) {
                    String callback = item.optString("callback", "");
                    if (!callback.isEmpty()) {
                        touchHandler.onKey(callback, key);
                        handled = true;
                    }
                }
            }
            if (handled) return true;
        }
        return super.onKeyDown(keyCode, event);
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
        try (InputStream input = getContext().getAssets().open(assetPath)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) {
            // A missing optional asset becomes a visible placeholder, not an app crash.
        }
        if (!spriteCache.containsKey(assetPath) && spriteCache.size() >= 32) {
            String oldest = spriteCache.keySet().iterator().next();
            spriteCache.remove(oldest);
            animationStarts.remove(oldest);
            // Drop the cache reference and let Android reclaim the bitmap safely.
        }
        spriteCache.put(assetPath, bitmap);
        return bitmap;
    }

    private void drawSprite(Canvas canvas, JSONObject item, int x, int y, int w, int h) {
        String path = item.optString("text", "");
        Bitmap bitmap = loadSprite(path);
        if (bitmap == null || w <= 0 || h <= 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(190, 65, 105));
            canvas.drawRect(x, y, x + Math.max(1, w), y + Math.max(1, h), paint);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x, y, x + Math.max(1, w), y + Math.max(1, h), paint);
            canvas.drawLine(x + Math.max(1, w), y, x, y + Math.max(1, h), paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }

        int columns = Math.max(1, Math.min(64, item.optInt("columns", 1)));
        int rows = Math.max(1, Math.min(64, item.optInt("rows", 1)));
        int frameCount = Math.max(1, Math.min(columns * rows, item.optInt("frameCount", 1)));
        int frame = Math.max(0, Math.min(columns * rows - 1, item.optInt("frame", 0)));
        int fps = Math.max(0, Math.min(60, item.optInt("animationFps", 0)));
        if (fps > 0) {
            long now = SystemClock.uptimeMillis();
            Long started = animationStarts.get(item.optString("id", ""));
            if (started == null) {
                started = now;
                animationStarts.put(item.optString("id", ""), started);
            }
            long tick = Math.max(0L, (now - started) * fps / 1000L);
            boolean loop = item.optBoolean("animationLoop", true);
            frame = loop ? (int) (tick % frameCount) : (int) Math.min(frameCount - 1, tick);
            if (loop || tick < frameCount - 1) postInvalidateDelayed(Math.max(16, 1000 / fps));
        }

        int cellWidth = bitmap.getWidth() / columns;
        int cellHeight = bitmap.getHeight() / rows;
        if (cellWidth <= 0 || cellHeight <= 0) return;
        int sourceX = (frame % columns) * cellWidth;
        int sourceY = (frame / columns) * cellHeight;
        Rect source = new Rect(sourceX, sourceY, sourceX + cellWidth, sourceY + cellHeight);
        Rect destination = new Rect(x, y, x + w, y + h);
        paint.setAlpha(255);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, source, destination, paint);
    }

    private int shadeColor(int color, double factor) {
        int alpha = Color.alpha(color);
        int red = (int) Math.max(0, Math.min(255, Color.red(color) * factor));
        int green = (int) Math.max(0, Math.min(255, Color.green(color) * factor));
        int blue = (int) Math.max(0, Math.min(255, Color.blue(color) * factor));
        return Color.argb(alpha, red, green, blue);
    }

    private void drawCube3D(Canvas canvas, JSONObject item) {
        final double half = Math.max(1, item.optInt("w", 1)) * 0.5;
        final double objectScale = Math.max(0.05, Math.min(20.0, item.optDouble("scale3D", 1.0)));
        final double[] xs = {-half, half, half, -half, -half, half, half, -half};
        final double[] ys = {-half, -half, half, half, -half, -half, half, half};
        final double[] zs = {-half, -half, -half, -half, half, half, half, half};
        final double rx = Math.toRadians(item.optDouble("rotationX", 0.0));
        final double ry = Math.toRadians(item.optDouble("rotationY", 0.0));
        final double rz = Math.toRadians(item.optDouble("rotationZ", 0.0));
        final double crx = Math.cos(rx), srx = Math.sin(rx);
        final double cry = Math.cos(ry), sry = Math.sin(ry);
        final double crz = Math.cos(rz), srz = Math.sin(rz);
        final double camX = camera.optDouble("x", 0.0);
        final double camY = camera.optDouble("y", 0.0);
        final double camZ = camera.optDouble("z", -300.0);
        final double yaw = Math.toRadians(camera.optDouble("yaw", 0.0));
        final double pitch = Math.toRadians(camera.optDouble("pitch", 0.0));
        final double focal = Math.max(32.0, Math.min(2000.0, camera.optDouble("focal", 240.0)));
        final double cy = Math.cos(yaw), sy = Math.sin(yaw);
        final double cp = Math.cos(pitch), sp = Math.sin(pitch);
        float[] px = new float[8], py = new float[8];
        double[] depth = new double[8];
        for (int i = 0; i < 8; i++) {
            double lx = xs[i] * objectScale;
            double ly = ys[i] * objectScale;
            double lz = zs[i] * objectScale;
            double afterX = lx;
            double afterY = crx * ly - srx * lz;
            double afterZ = srx * ly + crx * lz;
            double afterY2 = cry * afterY + sry * afterZ;
            double afterZ2 = -sry * afterY + cry * afterZ;
            double objectX = crz * afterX - srz * afterY2;
            double objectY = srz * afterX + crz * afterY2;
            double objectZ = afterZ2;
            double dx = item.optInt("x", 0) + objectX - camX;
            double dy = item.optInt("y", 0) + objectY - camY;
            double dz = item.optInt("z", 0) + objectZ - camZ;
            double cameraX = cy * dx - sy * dz;
            double yawZ = sy * dx + cy * dz;
            double cameraY = cp * dy - sp * yawZ;
            double cameraZ = sp * dy + cp * yawZ;
            if (cameraZ <= 1.0) return;
            depth[i] = cameraZ;
            px[i] = (float) (160.0 + cameraX * focal / cameraZ);
            py[i] = (float) (240.0 - cameraY * focal / cameraZ);
        }

        final int[][] faces = {
                {4, 5, 6, 7}, {0, 1, 2, 3}, {0, 4, 7, 3},
                {1, 5, 6, 2}, {0, 1, 5, 4}, {3, 2, 6, 7}
        };
        final double[] faceLight = {0.70, 0.95, 0.60, 0.80, 1.00, 0.35};
        final int base = (int) item.optLong("color", 0xFFFFFFFFL);
        final double material = Math.max(0.0, Math.min(1.0, item.optDouble("light", 1.0)));
        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < faces.length; i++) order.add(i);
        Collections.sort(order, (a, b) -> {
            double da = 0, db = 0;
            for (int index : faces[a]) da += depth[index] * 0.25;
            for (int index : faces[b]) db += depth[index] * 0.25;
            return Double.compare(db, da);
        });

        for (int faceIndex : order) {
            int[] face = faces[faceIndex];
            Path path = new Path();
            path.moveTo(px[face[0]], py[face[0]]);
            for (int j = 1; j < face.length; j++) path.lineTo(px[face[j]], py[face[j]]);
            path.close();
            double intensity = 0.18 + (0.25 + 0.75 * faceLight[faceIndex]) * material;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(shadeColor(base, intensity));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.argb(210, 8, 12, 22));
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float scale = Math.min(getWidth() / 320f, getHeight() / 480f);
        final float offsetX = (getWidth() - 320f * scale) / 2f;
        final float offsetY = (getHeight() - 480f * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(8, 12, 22));
        canvas.drawRect(0, 0, 320, 480, paint);
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject item = elements.optJSONObject(i);
                if (item == null) continue;
                String kind = item.optString("kind", "");
                if ("node".equals(kind) || "touchArea".equals(kind) || "keyArea".equals(kind)
                        || "text".equals(kind) || "button".equals(kind)) continue;

                int save = canvas.save();
                applyParentTransforms(canvas, item);
                applyItemTransform(canvas, item);
                int x = item.optInt("x", 0);
                int y = item.optInt("y", 0);
                int w = item.optInt("w", 0);
                int h = item.optInt("h", 0);

                if ("sprite".equals(kind)) {
                    drawSprite(canvas, item, x, y, w, h);
                } else if ("cube3D".equals(kind)) {
                    drawCube3D(canvas, item);
                } else if ("rect".equals(kind) || "circle".equals(kind)
                        || "line".equals(kind) || "drawText".equals(kind)) {
                    int color = (int) item.optLong("color", 0xFFFFFFFFL);
                    paint.setColor(color);
                    paint.setStrokeWidth(3f);
                    if ("rect".equals(kind)) {
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawRect(x, y, x + w, y + h, paint);
                    } else if ("circle".equals(kind)) {
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(x, y, w, paint);
                    } else if ("line".equals(kind)) {
                        paint.setStyle(Paint.Style.STROKE);
                        canvas.drawLine(x, y, w, h, paint);
                        paint.setStyle(Paint.Style.FILL);
                    } else if ("drawText".equals(kind)) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setTextSize(Math.max(6f, Math.min(96f, w)));
                        paint.setTypeface(android.graphics.Typeface.DEFAULT);
                        canvas.drawText(item.optString("text", ""), x, y, paint);
                    }
                }
                canvas.restoreToCount(save);
            }
        }
        canvas.restore();
    }
}
