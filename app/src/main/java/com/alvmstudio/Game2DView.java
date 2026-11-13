package com.alvmstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;

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

    public Game2DView(Context context, JSONArray elements, TouchHandler touchHandler) {
        super(context);
        this.elements = elements;
        this.touchHandler = touchHandler;
        setBackgroundColor(Color.rgb(8, 12, 22));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || touchHandler == null || elements == null) {
            return true;
        }
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
                return true;
            }
        }
        return true;
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
                if (!kind.equals("rect") && !kind.equals("circle") && !kind.equals("line")) continue;
                int x = item.optInt("x", 0);
                int y = item.optInt("y", 0);
                int w = item.optInt("w", 0);
                int h = item.optInt("h", 0);
                int color = (int) item.optLong("color", 0xFFFFFFFFL);
                paint.setColor(color);
                paint.setStrokeWidth(3f);
                if (kind.equals("rect")) {
                    canvas.drawRect(x, y, x + w, y + h, paint);
                } else if (kind.equals("circle")) {
                    canvas.drawCircle(x, y, w, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawLine(x, y, w, h, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
        canvas.restore();
    }
}