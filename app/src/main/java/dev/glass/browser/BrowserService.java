package dev.glass.browser;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.hardware.display.DisplayManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class BrowserService extends Service {
    private WebView web;
    private LinearLayout controls;
    private EditText address;
    private TextView addressDisplay, status, nativeTest;
    private Button toggle, media, ink;
    private boolean enabled = true, dark = false;
    private int mediaMode = 0, opacity = 20;
    private String injection;
    private final String[] mediaNames = {"画像: 薄く", "画像: 枠", "画像: 非表示"};
    public static final String STOP = "dev.glass.browser.STOP";
    private Context ui;
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private GlassFrame frame;
    private boolean launchingFullscreen;
    private boolean attached, typing, failed;
    private int screenW, screenH;

    private SharedPreferences prefs() { return getSharedPreferences("MainActivity", 0); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        ResultReceiver receiver = intent == null ? null : intent.getParcelableExtra("result");
        try {
            startNotification();
            if (!Settings.canDrawOverlays(this)) throw new SecurityException("他のアプリの上に表示する許可が必要です");
            if (!attached) createOverlay();
            else { clamp(); setTyping(false); }
            if (receiver != null) receiver.send(1, Bundle.EMPTY);
        } catch (Exception e) {
            Bundle error = new Bundle(); error.putString("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            if (receiver != null) receiver.send(0, error);
            Toast.makeText(this, "透明ウィンドウを開始できませんでした", Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("overlay", "透明ウィンドウ", NotificationManager.IMPORTANCE_LOW));
        PendingIntent close = PendingIntent.getService(this, 1, new Intent(this, BrowserService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "overlay").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Glass Browser 表示中").setContentText("ウィンドウの × または「閉じる」で終了")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "閉じる", close).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
    }

    private void createOverlay() {
        Context base = this;
        if (Build.VERSION.SDK_INT >= 30) {
            DisplayManager dm = getSystemService(DisplayManager.class);
            base = createDisplayContext(dm.getDisplay(Display.DEFAULT_DISPLAY)).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        ui = new ContextThemeWrapper(base, android.R.style.Theme_Material_Light_NoActionBar);
        wm = (WindowManager)ui.getSystemService(WINDOW_SERVICE);

        enabled = prefs().getBoolean("enabled", true);
        dark = prefs().getBoolean("dark", false);
        mediaMode = prefs().getInt("media", 0);
        opacity = prefs().getInt("opacity", 20);
        injection = asset("overlay.js");

        frame = new GlassFrame(ui, chromeGestures());
        frame.setFocusCallbacks(() -> { if (attached && !typing) setTyping(true); }, () -> { if (attached && typing) setTyping(false); });

        root = new LinearLayout(ui);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setPadding(dp(8), dp(20), dp(8), dp(22));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // Modern Browser Header (matching the sleek pill browser UI on the left)
        LinearLayout header = new LinearLayout(ui);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), dp(4), dp(6), dp(4));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(0xEE161A22); // Elegant dark semi-translucent header
        headerBg.setCornerRadius(dp(14));
        header.setBackground(headerBg);

        // Home Button (🏠)
        TextView homeBtn = iconBtn("⌂", dp(34), v -> open("https://x.com/home"));
        homeBtn.setTextSize(18);
        header.addView(homeBtn);

        // Pill-shaped Address Bar Container
        LinearLayout pill = new LinearLayout(ui);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(4), dp(8), dp(4));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xFF242A35);
        pillBg.setCornerRadius(dp(18));
        pill.setBackground(pillBg);

        TextView lockIcon = new TextView(ui);
        lockIcon.setText("🔒 ");
        lockIcon.setTextSize(11);
        lockIcon.setTextColor(0xFF8E99A8);
        pill.addView(lockIcon);

        // Address display text (read-only view showing clean domain like x.com/home)
        addressDisplay = new TextView(ui);
        addressDisplay.setText("x.com/home");
        addressDisplay.setTextSize(13);
        addressDisplay.setTextColor(Color.WHITE);
        addressDisplay.setTypeface(Typeface.SANS_SERIF);
        addressDisplay.setSingleLine(true);
        addressDisplay.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pill.addView(addressDisplay, adParams);

        // Full editable text field (hidden until tapped)
        address = new EditText(ui);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0xFFAAAAAA);
        address.setHint("https://x.com");
        address.setTextSize(13);
        address.setInputType(17);
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setVisibility(View.GONE);
        address.setImeOptions(2);
        pill.addView(address, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        pill.setOnClickListener(v -> {
            if (address.getVisibility() == View.GONE) {
                addressDisplay.setVisibility(View.GONE);
                address.setVisibility(View.VISIBLE);
                address.requestFocus();
                setTyping(true);
                address.postDelayed(() -> {
                    if (attached) {
                        ((InputMethodManager)ui.getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 100);
            }
        });

        address.setOnEditorActionListener((v, action, event) -> {
            navigate();
            address.setVisibility(View.GONE);
            addressDisplay.setVisibility(View.VISIBLE);
            setTyping(false);
            return true;
        });

        header.addView(pill, new LinearLayout.LayoutParams(0, dp(38), 1));

        // Quick action: + (Toggle Transparency or new search)
        TextView addBtn = iconBtn("＋", dp(32), v -> {
            enabled = !enabled;
            apply();
            Toast.makeText(this, enabled ? "透過ON" : "通常表示", Toast.LENGTH_SHORT).show();
        });
        addBtn.setTextSize(15);
        header.addView(addBtn);

        // Menu button (︙) to toggle quick settings panel
        TextView menuBtn = iconBtn("⋮", dp(30), v -> {
            controls.setVisibility(controls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            setTyping(false);
        });
        menuBtn.setTextSize(16);
        header.addView(menuBtn);

        // Close button (×)
        TextView closeBtn = iconBtn("×", dp(30), v -> stopSelf());
        closeBtn.setTextSize(16);
        header.addView(closeBtn);

        root.addView(header);

        // Collapsible Controls Drawer
        controls = new LinearLayout(ui);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackground(new GradientDrawable() {{
            setColor(0xF01A202C);
            setCornerRadius(dp(12));
        }});
        controls.setPadding(dp(8), dp(6), dp(8), dp(8));
        controls.setVisibility(View.GONE);
        root.addView(controls);

        status = new TextView(ui);
        status.setTextSize(11);
        status.setTextColor(0xFF88A0C0);
        status.setGravity(Gravity.CENTER);
        status.setText("ドラッグで移動 • 四隅でリサイズ • 下スワイプで全画面");
        controls.addView(status);

        HorizontalScrollView scroll = new HorizontalScrollView(ui);
        LinearLayout actions = new LinearLayout(ui);
        scroll.addView(actions);
        controls.addView(scroll);

        actions.addView(button("全画面", v -> enterFullscreen()));
        actions.addView(button("戻る", v -> { if (web.canGoBack()) web.goBack(); }));
        actions.addView(button("X", v -> open("https://x.com")));
        actions.addView(button("デモ", v -> demo()));
        toggle = button("", v -> { enabled = !enabled; apply(); });
        actions.addView(toggle);
        media = button("", v -> { mediaMode = (mediaMode + 1) % 3; apply(); });
        actions.addView(media);
        ink = button("", v -> { dark = !dark; apply(); });
        actions.addView(ink);
        actions.addView(button("文字テスト", v -> {
            boolean show = nativeTest.getVisibility() != View.VISIBLE;
            nativeTest.setVisibility(show ? View.VISIBLE : View.GONE);
            web.setVisibility(show ? View.GONE : View.VISIBLE);
            status.setText(show ? "ネイティブ透過テスト" : "Web表示");
        }));
        actions.addView(button("再読込", v -> web.reload()));

        LinearLayout sliderRow = new LinearLayout(ui);
        TextView label = new TextView(ui);
        label.setTextColor(Color.WHITE);
        label.setText("画像の濃さ");
        label.setTextSize(12);
        label.setPadding(dp(4), 0, dp(4), 0);
        sliderRow.addView(label);
        SeekBar slider = new SeekBar(ui);
        slider.setMax(100);
        slider.setProgress(opacity);
        sliderRow.addView(slider, new LinearLayout.LayoutParams(0, dp(36), 1));
        controls.addView(sliderRow);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                opacity = p;
                if (user) apply();
            }
        });

        // Content Frame (Fully Transparent Background)
        FrameLayout content = new FrameLayout(ui);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        web = new WebView(ui);
        web.setBackgroundColor(Color.TRANSPARENT);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));

        nativeTest = new TextView(ui);
        nativeTest.setText("透明ウィンドウの確認\n\nこの文字の後ろに動画が見えれば、Android側の透過は成功です。\n\n見えない場合はWebページ以外の層が背景を塗っている可能性があります。\n\n右上の ⋮ で操作パネルを開閉できます。\n「文字テスト」をもう一度押すと戻ります。");
        nativeTest.setTextSize(18);
        nativeTest.setTextColor(Color.WHITE);
        nativeTest.setShadowLayer(3, 0, 1, Color.BLACK);
        nativeTest.setPadding(dp(16), dp(24), dp(16), 0);
        nativeTest.setVisibility(View.GONE);
        content.addView(nativeTest, new FrameLayout.LayoutParams(-1, -1));

        web.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                web.postDelayed(() -> {
                    if (attached && typing && web.getHitTestResult() != null && web.getHitTestResult().getType() == WebView.HitTestResult.EDIT_TEXT_TYPE) {
                        web.requestFocus();
                        ((InputMethodManager)ui.getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 120);
            }
            return false;
        });

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                if ("https".equals(scheme) || "http".equals(scheme)) return false;
                Toast.makeText(BrowserService.this, "Webページのみ開けます", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                failed = false;
                if (!url.startsWith("https://glass.invalid")) {
                    address.setText(url);
                    try {
                        Uri u = Uri.parse(url);
                        String host = u.getHost() == null ? url : u.getHost();
                        String path = u.getPath() == null ? "" : u.getPath();
                        addressDisplay.setText(host + path);
                    } catch (Exception e) {
                        addressDisplay.setText(url);
                    }
                }
            }

            @Override
            public void onPageCommitVisible(WebView v, String url) { apply(); }

            @Override
            public void onPageFinished(WebView v, String url) { apply(); }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    failed = true;
                    status.setText("読込失敗: " + e.getDescription());
                }
            }
        });

        updateBounds();
        params = new WindowManager.LayoutParams(
            Math.min(dp(350), screenW),
            Math.min(dp(510), screenH),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = dp(12);
        params.y = dp(64);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.setTitle("Glass Browser Overlay");

        int[] initial = OverlayGeometry.phoneSize(screenW, screenH, dp(320));
        boolean migrated = prefs().getBoolean("phoneShapeV5", false);
        params.width = migrated ? prefs().getInt("width", initial[0]) : initial[0];
        params.height = Math.round(params.width * OverlayGeometry.PHONE_RATIO);
        params.x = prefs().getInt("x", params.x);
        params.y = prefs().getInt("y", params.y);
        params.alpha = .98f;
        prefs().edit().putBoolean("phoneShapeV5", true).apply();

        clamp();
        wm.addView(frame, params);
        attached = true;

        apply();
        Bundle incoming = BrowserSession.take();
        if (incoming == null || web.restoreState(incoming) == null) demo();
    }

    private TextView iconBtn(String text, int widthDp, View.OnClickListener l) {
        TextView b = new TextView(ui);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(widthDp, dp(38)));
        return b;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private Button button(String title, View.OnClickListener listener) {
        Button b = new Button(ui);
        b.setText(title);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0x33445566);
        b.setOnClickListener(listener);
        return b;
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void navigate() {
        String u = address.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.contains(":")) u = "https://" + u;
        Uri parsed = Uri.parse(u);
        if (!"https".equals(parsed.getScheme()) || parsed.getHost() == null) {
            Toast.makeText(this, "https:// のURLを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        open(u);
    }

    private void open(String url) {
        nativeTest.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        web.loadUrl(url);
        setTyping(false);
    }

    private void demo() {
        nativeTest.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        address.setText("");
        addressDisplay.setText("glass://demo");
        web.loadDataWithBaseURL("https://glass.invalid/", asset("demo.html"), "text/html", "UTF-8", null);
    }

    private void apply() {
        toggle.setText(enabled ? "透過ON" : "透過OFF");
        media.setText(mediaNames[mediaMode]);
        ink.setText(dark ? "文字: 黒" : "文字: 白");
        nativeTest.setTextColor(dark ? Color.BLACK : Color.WHITE);
        nativeTest.setShadowLayer(3, 0, 1, dark ? Color.WHITE : Color.BLACK);

        prefs().edit().putBoolean("enabled", enabled).putBoolean("dark", dark).putInt("media", mediaMode).putInt("opacity", opacity).apply();
        try {
            JSONObject c = new JSONObject();
            c.put("enabled", enabled);
            c.put("dark", dark);
            c.put("mode", mediaMode);
            c.put("opacity", opacity / 100.0);
            web.evaluateJavascript(injection.replace("__CONFIG__", c.toString()), null);
        } catch (Exception e) {
            if (status != null) status.setText("表示調整に失敗");
        }
    }

    private void setTyping(boolean value) {
        typing = value;
        if (value) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            ((InputMethodManager)ui.getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(frame.getWindowToken(), 0);
            address.clearFocus();
            web.clearFocus();
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        updateWindow();
    }

    private void updateBounds() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics m = wm.getMaximumWindowMetrics();
            android.graphics.Insets i = m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            screenW = m.getBounds().width() - i.left - i.right;
            screenH = m.getBounds().height() - i.top - i.bottom;
        } else {
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(m);
            screenW = m.widthPixels;
            screenH = m.heightPixels;
        }
    }

    private void clamp() {
        updateBounds();
        int[] shape = OverlayGeometry.phoneSize(screenW, screenH, params.width);
        params.width = shape[0];
        params.height = shape[1];
        int[] r = OverlayGeometry.fit(params.x, params.y, params.width, params.height, screenW, screenH, Math.min(dp(220), params.width), Math.min(dp(460), params.height));
        params.x = r[0];
        params.y = r[1];
        params.width = r[2];
        params.height = r[3];
    }

    private void updateWindow() {
        if (!attached) return;
        try {
            wm.updateViewLayout(frame, params);
        } catch (RuntimeException e) {
            stopSelf();
        }
    }

    private GlassFrame.Gestures chromeGestures() {
        return new GlassFrame.Gestures() {
            int region, x, y, w, h;
            float sx, sy;

            public void start(int r, float px, float py) {
                region = r; sx = px; sy = py;
                x = params.x; y = params.y; w = params.width; h = params.height;
            }

            public void move(float px, float py) {
                if (launchingFullscreen || region == GlassFrame.BAR) return;
                int dx = Math.round(px - sx), dy = Math.round(py - sy);
                if (region == GlassFrame.MOVE) {
                    params.x = x + dx;
                    params.y = y + dy;
                    clamp();
                    updateWindow();
                    return;
                }
                int[] b = OverlayGeometry.resizePhone(region, x, y, w, h, dx, dy, screenW, screenH, dp(220));
                params.x = b[0]; params.y = b[1]; params.width = b[2]; params.height = b[3];
                updateWindow();
            }

            public void end(float px, float py, boolean cancel) {
                if (!cancel && region == GlassFrame.BAR) {
                    int action = OverlayGeometry.swipe(px - sx, py - sy, dp(48));
                    if (action < 0) { stopSelf(); return; }
                    if (action > 0) { enterFullscreen(); return; }
                }
                saveGeometry();
            }
        };
    }

    private void enterFullscreen() {
        if (launchingFullscreen) return;
        launchingFullscreen = true;
        saveGeometry();
        setTyping(false);
        BrowserSession.capture(web);
        ResultReceiver ack = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int code, Bundle result) {
                if (code == 1) stopSelf();
                else launchingFullscreen = false;
            }
        };
        try {
            Intent intent = new Intent(this, FullscreenActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("overlayAck", ack);
            startActivity(intent);
        } catch (RuntimeException e) {
            launchingFullscreen = false;
            Toast.makeText(this, "全画面を開けませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveGeometry() {
        if (attached) {
            prefs().edit().putInt("x", params.x).putInt("y", params.y).putInt("width", params.width).apply();
        }
    }

    @Override
    public void onDestroy() {
        if (attached) {
            saveGeometry();
            try { wm.removeView(frame); } catch (RuntimeException ignored) {}
            attached = false;
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
