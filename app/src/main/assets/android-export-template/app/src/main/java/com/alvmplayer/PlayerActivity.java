package com.alvmplayer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
    private JSONObject currentCamera3D = new JSONObject();
    private SceneView sceneView;

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
        SceneView reusableScene = sceneView;
        content.removeAllViews();
        currentElements = ui.optJSONArray("elements");
        if (currentElements == null) currentElements = new JSONArray();
        currentCamera3D = ui.optJSONObject("camera3D");
        if (currentCamera3D == null) currentCamera3D = new JSONObject();
        sceneView = null;

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
                    || "sprite".equals(kind) || "touchArea".equals(kind)
                    || "cube3D".equals(kind) || "node".equals(kind) || "keyArea".equals(kind)) {
                hasScene = true;
                break;
            }
        }
        if (hasScene) {
            sceneView = reusableScene == null ? new SceneView(currentElements, currentCamera3D) : reusableScene;
            sceneView.updateScene(currentElements, currentCamera3D);
            LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(480));
            sceneParams.bottomMargin = dp(10);
            content.addView(sceneView, sceneParams);
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
        private volatile JSONArray elements;
        private volatile JSONObject camera;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<String, Bitmap> spriteCache = new HashMap<>();
        private final Map<String, Long> animationStarts = new HashMap<>();

        SceneView(JSONArray elements, JSONObject camera) {
            super(PlayerActivity.this);
            this.elements = elements;
            this.camera = camera == null ? new JSONObject() : camera;
            setBackgroundColor(Color.rgb(8, 12, 22));
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }

        void updateScene(JSONArray nextElements, JSONObject nextCamera) {
            if (nextElements != null) elements = nextElements;
            if (nextCamera != null) camera = nextCamera;
            invalidate();
        }

        private JSONObject findElement(String id) {
            JSONArray current = elements;
            if (current == null || id == null || id.isEmpty()) return null;
            for (int i = 0; i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item != null && id.equals(item.optString("id", ""))) return item;
            }
            return null;
        }

        private void applyParentTransforms(Canvas canvas, JSONObject item) {
            ArrayList<JSONObject> parents = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            String parent = item.optString("parent", "");
            for (int depth = 0; depth < 64 && !parent.isEmpty(); depth++) {
                if (!visited.add(parent)) break;
                JSONObject node = findElement(parent);
                if (node == null || !"node".equals(node.optString("kind", ""))) break;
                parents.add(node);
                parent = node.optString("parent", "");
            }
            for (int i = parents.size() - 1; i >= 0; i--) {
                JSONObject node = parents.get(i);
                canvas.translate(node.optInt("x", 0), node.optInt("y", 0));
                canvas.rotate((float) node.optDouble("rotation", 0));
                canvas.scale((float) node.optDouble("scaleX", 1), (float) node.optDouble("scaleY", 1));
            }
        }

        private void applyItemTransform(Canvas canvas, JSONObject item) {
            float x = item.optInt("x", 0), y = item.optInt("y", 0);
            float rotation = (float) item.optDouble("rotation", 0);
            float sx = (float) item.optDouble("scaleX", 1), sy = (float) item.optDouble("scaleY", 1);
            if (rotation == 0 && sx == 1 && sy == 1) return;
            canvas.translate(x, y);
            canvas.rotate(rotation);
            canvas.scale(sx, sy);
            canvas.translate(-x, -y);
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

        private void drawSprite(Canvas canvas, JSONObject item, int x, int y, int w, int h) {
            Bitmap bitmap = loadSprite(item.optString("text", ""));
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
            int frames = Math.max(1, Math.min(columns * rows, item.optInt("frameCount", 1)));
            int frame = Math.max(0, Math.min(columns * rows - 1, item.optInt("frame", 0)));
            int fps = Math.max(0, Math.min(60, item.optInt("animationFps", 0)));
            if (fps > 0) {
                String id = item.optString("id", "");
                long now = SystemClock.uptimeMillis();
                Long started = animationStarts.get(id);
                if (started == null) { started = now; animationStarts.put(id, started); }
                long tick = Math.max(0, (now - started) * fps / 1000L);
                boolean loop = item.optBoolean("animationLoop", true);
                frame = loop ? (int) (tick % frames) : (int) Math.min(frames - 1, tick);
                if (loop || tick < frames - 1) postInvalidateDelayed(Math.max(16, 1000 / fps));
            }
            int cellWidth = bitmap.getWidth() / columns, cellHeight = bitmap.getHeight() / rows;
            if (cellWidth <= 0 || cellHeight <= 0) return;
            int sx = (frame % columns) * cellWidth, sy = (frame / columns) * cellHeight;
            paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(bitmap, new Rect(sx, sy, sx + cellWidth, sy + cellHeight),
                    new Rect(x, y, x + w, y + h), paint);
        }

        private int shade(int color, double factor) {
            return Color.argb(Color.alpha(color),
                    (int) Math.max(0, Math.min(255, Color.red(color) * factor)),
                    (int) Math.max(0, Math.min(255, Color.green(color) * factor)),
                    (int) Math.max(0, Math.min(255, Color.blue(color) * factor)));
        }

        private void drawCube(Canvas canvas, JSONObject item) {
            double half = Math.max(1, item.optInt("w", 1)) * 0.5;
            double size = Math.max(0.05, Math.min(20, item.optDouble("scale3D", 1))) ;
            double[] xs = {-half,half,half,-half,-half,half,half,-half};
            double[] ys = {-half,-half,half,half,-half,-half,half,half};
            double[] zs = {-half,-half,-half,-half,half,half,half,half};
            double rx=Math.toRadians(item.optDouble("rotationX",0));
            double ry=Math.toRadians(item.optDouble("rotationY",0));
            double rz=Math.toRadians(item.optDouble("rotationZ",0));
            double crx=Math.cos(rx),srx=Math.sin(rx),cry=Math.cos(ry),sry=Math.sin(ry),crz=Math.cos(rz),srz=Math.sin(rz);
            double camX=camera.optDouble("x",0),camY=camera.optDouble("y",0),camZ=camera.optDouble("z",-300);
            double yaw=Math.toRadians(camera.optDouble("yaw",0)),pitch=Math.toRadians(camera.optDouble("pitch",0));
            double focal=Math.max(32,Math.min(2000,camera.optDouble("focal",240)));
            double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
            float[] px=new float[8],py=new float[8];double[] depth=new double[8];
            for(int i=0;i<8;i++){
                double lx=xs[i]*size,ly=ys[i]*size,lz=zs[i]*size;
                double ax=lx,ay=crx*ly-srx*lz,az=srx*ly+crx*lz;
                double ay2=cry*ay+sry*az,az2=-sry*ay+cry*az;
                double ox=crz*ax-srz*ay2,oy=srz*ax+crz*ay2,oz=az2;
                double dx=item.optInt("x",0)+ox-camX,dy=item.optInt("y",0)+oy-camY,dz=item.optInt("z",0)+oz-camZ;
                double cx=cy*dx-sy*dz,yawZ=sy*dx+cy*dz,cyy=cp*dy-sp*yawZ,cz=sp*dy+cp*yawZ;
                if(cz<=1)return;depth[i]=cz;px[i]=(float)(160+cx*focal/cz);py[i]=(float)(240-cyy*focal/cz);
            }
            final int[][] faces={{4,5,6,7},{0,1,2,3},{0,4,7,3},{1,5,6,2},{0,1,5,4},{3,2,6,7}};
            final double[] lights={0.70,0.95,0.60,0.80,1.00,0.35};
            ArrayList<Integer> order=new ArrayList<>();
            for(int i=0;i<faces.length;i++)order.add(i);
            Collections.sort(order,(a,b)->{
                double da=0,db=0;for(int index:faces[a])da+=depth[index]*0.25;for(int index:faces[b])db+=depth[index]*0.25;
                return Double.compare(db,da);
            });
            int base=(int)item.optLong("color",0xFFFFFFFFL);
            double material=Math.max(0,Math.min(1,item.optDouble("light",1)));
            for(int faceIndex:order){
                int[] face=faces[faceIndex];Path path=new Path();path.moveTo(px[face[0]],py[face[0]]);
                for(int i=1;i<4;i++)path.lineTo(px[face[i]],py[face[i]]);path.close();
                double intensity=0.18+(0.25+0.75*lights[faceIndex])*material;
                paint.setStyle(Paint.Style.FILL);paint.setColor(shade(base,intensity));canvas.drawPath(path,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(Color.argb(210,8,12,22));canvas.drawPath(path,paint);
            }
            paint.setStyle(Paint.Style.FILL);
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
            JSONArray current = elements;
            for (int i = 0; current != null && i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                String kind = item.optString("kind", "");
                if ("node".equals(kind) || "touchArea".equals(kind) || "keyArea".equals(kind)
                        || "text".equals(kind) || "button".equals(kind)) continue;
                int save = canvas.save();
                applyParentTransforms(canvas,item);
                applyItemTransform(canvas,item);
                int x=item.optInt("x",0),y=item.optInt("y",0),w=item.optInt("w",0),h=item.optInt("h",0);
                int color=(int)item.optLong("color",0xFFFFFFFFL);
                paint.setColor(color);paint.setStrokeWidth(3f);
                if ("sprite".equals(kind)) drawSprite(canvas,item,x,y,w,h);
                else if ("cube3D".equals(kind)) drawCube(canvas,item);
                else if ("drawText".equals(kind)) {
                    paint.setStyle(Paint.Style.FILL);paint.setTextSize(Math.max(6,Math.min(96,w)));
                    canvas.drawText(item.optString("text",""),x,y,paint);
                } else if ("rect".equals(kind)) canvas.drawRect(x,y,x+w,y+h,paint);
                else if ("circle".equals(kind)) canvas.drawCircle(x,y,w,paint);
                else if ("line".equals(kind)) {
                    paint.setStyle(Paint.Style.STROKE);canvas.drawLine(x,y,w,h,paint);paint.setStyle(Paint.Style.FILL);
                }
                canvas.restoreToCount(save);
            }
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
            if(event.getActionMasked()!=MotionEvent.ACTION_UP)return true;
            float scale=Math.min(getWidth()/320f,getHeight()/480f);if(scale<=0)return true;
            float ox=(getWidth()-320f*scale)/2f,oy=(getHeight()-480f*scale)/2f;
            int x=Math.round((event.getX()-ox)/scale),y=Math.round((event.getY()-oy)/scale);
            JSONArray current=elements;
            for(int index=current.length()-1;index>=0;index--){
                JSONObject item=current.optJSONObject(index);
                if(item==null||!"touchArea".equals(item.optString("kind","")))continue;
                int left=item.optInt("x",0),top=item.optInt("y",0);
                if(x>=left&&x<left+item.optInt("w",0)&&y>=top&&y<top+item.optInt("h",0)){
                    String callback=item.optString("callback",""),id=item.optString("id","");
                    if(!callback.isEmpty()&&!id.isEmpty())runEvent(callback,"@touch|"+id+"|"+x+"|"+y);
                    return true;
                }
            }
            return true;
        }

        private String keyName(int code, KeyEvent event) {
            switch(code){
                case KeyEvent.KEYCODE_DPAD_LEFT:return "LEFT";
                case KeyEvent.KEYCODE_DPAD_RIGHT:return "RIGHT";
                case KeyEvent.KEYCODE_DPAD_UP:return "UP";
                case KeyEvent.KEYCODE_DPAD_DOWN:return "DOWN";
                case KeyEvent.KEYCODE_SPACE:return "SPACE";
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:return "ENTER";
                case KeyEvent.KEYCODE_ESCAPE:return "ESCAPE";
                default:
                    int value=event==null?0:event.getUnicodeChar();
                    if(value>='a'&&value<='z')value-=('a'-'A');
                    return value>='A'&&value<='Z'?String.valueOf((char)value):null;
            }
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            String key=keyName(keyCode,event);
            if(key!=null){
                JSONArray current=elements;
                for(int i=0;current!=null&&i<current.length();i++){
                    JSONObject item=current.optJSONObject(i);
                    if(item==null||!"keyArea".equals(item.optString("kind","")))continue;
                    String wanted=item.optString("text",""),callback=item.optString("callback","");
                    if((wanted.equals("ANY")||wanted.equals(key))&&!callback.isEmpty()){
                        runEvent(callback,key);return true;
                    }
                }
            }
            return super.onKeyDown(keyCode,event);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            requestFocus();
        }
    }}
