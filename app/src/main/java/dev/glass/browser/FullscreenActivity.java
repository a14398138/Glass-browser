package dev.glass.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class FullscreenActivity extends Activity {
    private WebView web;
    private LinearLayout controls;
    private EditText address;
    private TextView addressDisplay, status, nativeTest;
    private Button toggle, media, ink;
    private boolean enabled = true, dark = false;
    private int mediaMode = 0, opacity = 20;
    private String injection;
    private final String[] mediaNames = {"画像: 薄く", "画像: 枠", "画像: 非表示"};

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        enabled = getSharedPreferences("MainActivity", 0).getBoolean("enabled", true);
        dark = getSharedPreferences("MainActivity", 0).getBoolean("dark", false);
        mediaMode = getSharedPreferences("MainActivity", 0).getInt("media", 0);
        opacity = getSharedPreferences("MainActivity", 0).getInt("opacity", 20);
        injection = asset("overlay.js");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F141C);
        setContentView(root);

        // Modern Browser Header (matching the sleek pill browser UI on the left)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackgroundColor(0xFF161A22);

        // Home Button (🏠)
        TextView homeBtn = iconBtn("⌂", dp(36), v -> open("https://x.com/home"));
        homeBtn.setTextSize(20);
        header.addView(homeBtn);

        // Pill-shaped Address Bar Container
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(12), dp(6), dp(10), dp(6));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xFF242A35);
        pillBg.setCornerRadius(dp(20));
        pill.setBackground(pillBg);

        TextView lockIcon = new TextView(this);
        lockIcon.setText("🔒 ");
        lockIcon.setTextSize(12);
        lockIcon.setTextColor(0xFF8E99A8);
        pill.addView(lockIcon);

        addressDisplay = new TextView(this);
        addressDisplay.setText("x.com/home");
        addressDisplay.setTextSize(14);
        addressDisplay.setTextColor(Color.WHITE);
        addressDisplay.setTypeface(Typeface.SANS_SERIF);
        addressDisplay.setSingleLine(true);
        addressDisplay.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.addView(addressDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0xFFAAAAAA);
        address.setHint("https://x.com");
        address.setTextSize(14);
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
                address.postDelayed(() -> {
                    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
                }, 100);
            }
        });

        address.setOnEditorActionListener((v, action, event) -> {
            navigate();
            address.setVisibility(View.GONE);
            addressDisplay.setVisibility(View.VISIBLE);
            return true;
        });

        header.addView(pill, new LinearLayout.LayoutParams(0, dp(42), 1));

        // Quick action: + (Toggle Transparency)
        TextView addBtn = iconBtn("＋", dp(34), v -> {
            enabled = !enabled;
            apply();
            Toast.makeText(this, enabled ? "透過ON" : "通常表示", Toast.LENGTH_SHORT).show();
        });
        addBtn.setTextSize(16);
        header.addView(addBtn);

        // Menu button (︙)
        TextView menuBtn = iconBtn("⋮", dp(32), v -> {
            controls.setVisibility(controls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        menuBtn.setTextSize(18);
        header.addView(menuBtn);

        root.addView(header);

        // Collapsible Controls Drawer
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundColor(0xFF1E2530);
        controls.setPadding(dp(8), dp(6), dp(8), dp(8));
        controls.setVisibility(View.GONE);
        root.addView(controls);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(0xFF88A0C0);
        status.setGravity(Gravity.CENTER);
        status.setText("全画面ブラウザ表示中");
        controls.addView(status);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout actions = new LinearLayout(this);
        scroll.addView(actions);
        controls.addView(scroll);

        actions.addView(button("小窓に戻す", v -> returnToOverlay()));
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

        LinearLayout sliderRow = new LinearLayout(this);
        TextView label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setText("画像の濃さ");
        label.setPadding(dp(8), 0, 0, 0);
        sliderRow.addView(label);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(opacity);
        sliderRow.addView(slider, new LinearLayout.LayoutParams(0, dp(40), 1));
        controls.addView(sliderRow);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                opacity = p;
                if (user) apply();
            }
        });

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));

        nativeTest = new TextView(this);
        nativeTest.setText("透明ウィンドウの確認\n\nこの文字の後ろに動画が見えれば、Android側の透過は成功です。\n\n右上の ⋮ で操作パネルを開閉できます。\n「文字テスト」をもう一度押すと戻ります。");
        nativeTest.setTextSize(20);
        nativeTest.setTextColor(Color.WHITE);
        nativeTest.setShadowLayer(3, 0, 1, Color.BLACK);
        nativeTest.setPadding(dp(16), dp(24), dp(16), 0);
        nativeTest.setVisibility(View.GONE);
        content.addView(nativeTest, new FrameLayout.LayoutParams(-1, -1));

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
                Toast.makeText(FullscreenActivity.this, "Webページのみ開けます", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
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
                if (r.isForMainFrame()) status.setText("読込失敗: " + e.getDescription());
            }
        });

        Bundle incoming = saved == null ? BrowserSession.take() : saved.getBundle("webState");
        if (incoming == null || web.restoreState(incoming) == null) demo();

        root.post(() -> {
            ResultReceiver ack = getIntent().getParcelableExtra("overlayAck");
            if (ack != null) {
                ack.send(1, Bundle.EMPTY);
                getIntent().removeExtra("overlayAck");
            }
        });
    }

    private TextView iconBtn(String text, int widthDp, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(widthDp, dp(42)));
        return b;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private Button button(String title, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xCC27303A);
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
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        web.requestFocus();
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

        getSharedPreferences("MainActivity", 0).edit().putBoolean("enabled", enabled).putBoolean("dark", dark).putInt("media", mediaMode).putInt("opacity", opacity).apply();
        try {
            JSONObject c = new JSONObject();
            c.put("enabled", enabled);
            c.put("dark", dark);
            c.put("mode", mediaMode);
            c.put("opacity", opacity / 100.0);
            web.evaluateJavascript(injection.replace("__CONFIG__", c.toString()), null);
        } catch (Exception e) {
            status.setText("表示調整に失敗");
        }
    }

    private boolean returning;
    private void returnToOverlay() {
        if (returning) return;
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "他のアプリの上に表示する権限を許可してください", Toast.LENGTH_LONG).show();
            return;
        }
        returning = true;
        BrowserSession.capture(web);
        ResultReceiver result = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                returning = false;
                if (code == 1) finishAndRemoveTask();
                else Toast.makeText(FullscreenActivity.this, "小窓を開始できませんでした", Toast.LENGTH_LONG).show();
            }
        };
        try {
            startForegroundService(new android.content.Intent(this, BrowserService.class).putExtra("result", result));
        } catch (RuntimeException e) {
            returning = false;
            Toast.makeText(this, "小窓を開始できませんでした", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        Bundle b = new Bundle();
        web.saveState(b);
        out.putBundle("webState", b);
    }

    @Override
    public void onBackPressed() {
        if (nativeTest.getVisibility() == View.VISIBLE) {
            nativeTest.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
