package com.alvmstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/** Native 320x480 logical-coordinate renderer for AL 2D primitives. */
public final class Game2DView extends View {
    private final JSONArray elements;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Game2DView(Context context, JSONArray elements) {
        super(context);
        this.elements = elements;
        setBackgroundColor(Color.rgb(8, 12, 22));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
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