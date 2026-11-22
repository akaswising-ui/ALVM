package com.alvmstudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/** Native 320x480 logical-coordinate renderer for AL 2D primitives. */
public final class Game2DView extends View {
    public interface TouchHandler {
        void onTouch(String callback, String id, int x, int y);
    }

    private final JSONArray elements;
    private final TouchHandler touchHandler;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Bitmap> spriteCache = new HashMap<>();

    public Game2DView(Context context, JSONArray elements, TouchHandler touchHandler) {
        super(context);
        this.elements = elements;
        this.touchHandler = touchHandler;
        setBackgroundColor(Color.rgb(8, 12, 22));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        // Game2DView is hosted inside a ScrollView. Without this, the parent
        // steals vertical drags before the game receives the release event.
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
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
                    if (!callback.isEmpty() && !id.isEmpty()) {
                        touchHandler.onTouch(callback, id, x, y);
                    }
                    break;
                }
            }
            return true;
        } finally {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private boolean isSafeAssetPath(String assetPath) {
        if (assetPath == null || assetPath.isEmpty() || assetPath.length() > 240
                || assetPath.startsWith("/") || assetPath.contains("\\\\")) return false;
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
            // Missing optional sprites must not crash an AL application.
        }
        spriteCache.put(assetPath, bitmap);
        return bitmap;
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
                int x = item.optInt("x", 0);
                int y = item.optInt("y", 0);
                int w = item.optInt("w", 0);
                int h = item.optInt("h", 0);
                if ("sprite".equals(kind)) {
                    Bitmap bitmap = loadSprite(item.optString("text", ""));
                    if (bitmap != null && w > 0 && h > 0) {
                        paint.setAlpha(255);
                        paint.setFilterBitmap(true);
                        canvas.drawBitmap(bitmap, null, new android.graphics.Rect(x, y, x + w, y + h), paint);
                    } else {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(Color.rgb(190, 65, 105));
                        canvas.drawRect(x, y, x + Math.max(1, w), y + Math.max(1, h), paint);
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        canvas.drawLine(x, y, x + Math.max(1, w), y + Math.max(1, h), paint);
                        canvas.drawLine(x + Math.max(1, w), y, x, y + Math.max(1, h), paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    continue;
                }
                if (!kind.equals("rect") && !kind.equals("circle") && !kind.equals("line") && !kind.equals("drawText")) continue;
                int color = (int) item.optLong("color", 0xFFFFFFFFL);
                paint.setColor(color);
                paint.setStrokeWidth(3f);
                if (kind.equals("rect")) {
                    canvas.drawRect(x, y, x + w, y + h, paint);
                } else if (kind.equals("circle")) {
                    canvas.drawCircle(x, y, w, paint);
                } else if (kind.equals("line")) {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawLine(x, y, w, h, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else if (kind.equals("drawText")) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextSize(Math.max(6f, Math.min(96f, item.optInt("w", 18))));
                    paint.setTypeface(android.graphics.Typeface.DEFAULT);
                    canvas.drawText(item.optString("text", ""), x, y, paint);
                }
            }
        }
        canvas.restore();
    }
}