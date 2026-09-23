package com.enes.akis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView web;
    private volatile boolean playing = false;
    // Buttons can arrive as key events and/or joystick axes; track both and send the combined state once.
    private final Map<String, Boolean> keyDown = new HashMap<>();
    private final Map<String, Boolean> axisDown = new HashMap<>();
    private final Map<String, Boolean> sent = new HashMap<>();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0F0E12);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setFocusable(false); // keep the WebView from stealing d-pad events for focus navigation
        web.setFocusableInTouchMode(false);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); }

    private class Bridge {
        @JavascriptInterface public void setPlaying(int p) { playing = p == 1; }
        @JavascriptInterface public void exit() { runOnUiThread(() -> finish()); }

        // Lists the songs packed into assets/music as a JSON array of file names.
        @JavascriptInterface public String musicList() {
            StringBuilder sb = new StringBuilder("[");
            try {
                String[] files = getAssets().list("music");
                if (files != null) {
                    Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
                    boolean first = true;
                    for (String f : files) {
                        String low = f.toLowerCase(Locale.ROOT);
                        if (!(low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".ogg") || low.endsWith(".opus")
                                || low.endsWith(".wav") || low.endsWith(".aac") || low.endsWith(".flac"))) continue;
                        if (!first) sb.append(',');
                        first = false;
                        sb.append('"').append(f.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                    }
                }
            } catch (IOException e) {
                // no music folder: the game falls back to its own music
            }
            return sb.append(']').toString();
        }
    }

    private static String keyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return "a";
            case KeyEvent.KEYCODE_BUTTON_B: return "b";
            case KeyEvent.KEYCODE_BUTTON_X: return "x";
            case KeyEvent.KEYCODE_BUTTON_Y: return "y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "lb";
            case KeyEvent.KEYCODE_BUTTON_R1: return "rb";
            case KeyEvent.KEYCODE_BUTTON_L2: return "lt";
            case KeyEvent.KEYCODE_BUTTON_R2: return "rt";
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_MENU: return "start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "back";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: return "a";
            default: return null;
        }
    }

    private static boolean isPad(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private void key(String name, boolean down) { keyDown.put(name, down); update(name); }
    private void axis(String name, boolean down) { axisDown.put(name, down); update(name); }

    private void update(String name) {
        boolean v = Boolean.TRUE.equals(keyDown.get(name)) || Boolean.TRUE.equals(axisDown.get(name));
        Boolean prev = sent.get(name);
        if (prev != null && prev.booleanValue() == v) return;
        sent.put(name, v);
        if (web != null) web.evaluateJavascript("window.padKey&&padKey('" + name + "'," + (v ? 1 : 0) + ")", null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        String name;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (isPad(e.getSource())) {
                name = "b"; // some controllers report B as BACK
            } else {
                // phone back button/gesture: let the game decide (pause, go back, or exit on the title screen)
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && web != null) {
                    web.evaluateJavascript("window.sysBack&&sysBack()", null);
                }
                return true;
            }
        } else {
            name = keyName(code);
        }
        if (name == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) { if (e.getRepeatCount() == 0) key(name, true); }
        else if (e.getAction() == KeyEvent.ACTION_UP) key(name, false);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (isPad(ev.getSource()) && ev.getAction() == MotionEvent.ACTION_MOVE) {
            float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X), hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
            float sx = ev.getAxisValue(MotionEvent.AXIS_X), sy = ev.getAxisValue(MotionEvent.AXIS_Y);
            float ax = Math.abs(sx), ay = Math.abs(sy);
            axis("left", hx < -0.5f || (sx < -0.5f && ax >= ay));
            axis("right", hx > 0.5f || (sx > 0.5f && ax >= ay));
            // stick up must be a clear, strong push so a diagonal never hard-drops by accident
            axis("up", hy < -0.5f || (sy < -0.85f && ay > ax * 1.5f));
            axis("down", hy > 0.5f || (sy > 0.6f && ay > ax));
            axis("rt", Math.max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS)) > 0.4f);
            axis("lt", Math.max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.4f);
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }
}
