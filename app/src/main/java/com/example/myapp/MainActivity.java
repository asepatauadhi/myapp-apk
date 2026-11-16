package com.example.myapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Message;
import android.os.Vibrator;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.widget.EditText;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.json.JSONObject;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String APP_NAME = "My App";
    private static final String START_URL = "https://youtube.com";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final boolean FULLSCREEN = false;
    private static final boolean KEEP_ON = false;
    private static final boolean SHOW_PROGRESS = true;
    private static final boolean SPLASH = true;
    private static final boolean PULL_REFRESH = true;
    private static final boolean EXTERNAL_LINKS = false;
    private static final boolean DESKTOP_MODE = false;
    private static final boolean JS = true;
    private static final boolean DOM = true;
    private static final boolean ZOOM = false;
    private static final boolean FILE_ACCESS = false;
    private static final boolean STATUS_LIGHT = true;
    private static final int THEME_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_COLOR = 0xFF007AFF;
    private static final int SPLASH_COLOR = 0xFFFFFFFF;
    private static final int SPLASH_TEXT = 0xFF1C1C1E;
    private static final String[] PERMS = new String[]{};
    private static final int REQ_FILE = 1;
    private static final int REQ_PERM = 2;
    private static final boolean BRIDGE = true;
    private static final boolean DBL_BACK = true;
    private static final boolean LONG_PRESS = true;
    private static final boolean CACHE = true;
    private static final boolean DARK = false;
    private static final boolean SECURE = false;
    private static final boolean DEEP = false;
    private static final boolean NOTIF = false;
    private static final boolean STORE = true;
    private static final boolean NEW_EXT = false;
    private static final String CUSTOM_UA = "";

    private WebView web;
    private SwipeRefreshLayout swipe;
    private ProgressBar bar;
    private View splash;
    private boolean splashGone = false;
    private ValueCallback<Uri[]> fileCb;
    private WebViewAssetLoader assetLoader;
    private FrameLayout rootView;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ConnectivityManager.NetworkCallback netCb;
    private volatile boolean trusted = true;
    private long lastBack = 0;
    private String errorUrl = null;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (KEEP_ON) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(THEME_COLOR);
        if (SECURE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        FrameLayout root = new FrameLayout(this);
        rootView = root;
        web = new WebView(this);
        swipe = new SwipeRefreshLayout(this);
        swipe.addView(web, new ViewGroup.LayoutParams(-1, -1));
        swipe.setEnabled(PULL_REFRESH);
        swipe.setOnRefreshListener(() -> web.reload());
        root.addView(swipe, new FrameLayout.LayoutParams(-1, -1));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(PROGRESS_COLOR));
        bar.setVisibility(View.GONE);
        root.addView(bar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));

        if (SPLASH) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackgroundColor(SPLASH_COLOR);
            box.setClickable(true);
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.mipmap.ic_launcher);
            box.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));
            TextView t = new TextView(this);
            t.setText(APP_NAME);
            t.setTextColor(SPLASH_TEXT);
            t.setTextSize(22);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setPadding(0, dp(16), 0, 0);
            box.addView(t);
            splash = box;
            root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
            splash.postDelayed(this::hideSplash, 8000);
        }
        setContentView(root);

        setupWebView();
        requestRuntimePerms();

        if (saved != null) web.restoreState(saved);
        else web.loadUrl(deepUrl(getIntent()));
        watchNetwork();
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(JS);
        s.setDomStorageEnabled(DOM);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(ZOOM);
        s.setBuiltInZoomControls(ZOOM);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(FILE_ACCESS);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (DESKTOP_MODE) s.setUserAgentString(DESKTOP_UA);
        else if (!CUSTOM_UA.isEmpty()) s.setUserAgentString(CUSTOM_UA);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        applyCache();
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        if (LONG_PRESS) web.setOnLongClickListener(lv -> {
            WebView.HitTestResult h = web.getHitTestResult();
            final String ex = h.getExtra();
            int t = h.getType();
            if (ex == null) return false;
            final boolean img = t == WebView.HitTestResult.IMAGE_TYPE || t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
            boolean lnk = t == WebView.HitTestResult.SRC_ANCHOR_TYPE;
            if (!img && !lnk) return false;
            String[] items = img ? new String[]{"Simpan gambar", "Bagikan tautan", "Salin tautan"}
                    : new String[]{"Buka di browser", "Bagikan tautan", "Salin tautan"};
            new AlertDialog.Builder(this).setItems(items, (d, i) -> {
                if (i == 0) {
                    if (img) download(ex, web.getSettings().getUserAgentString(), null, null);
                    else viewExternal(Uri.parse(ex));
                } else if (i == 1) shareText(ex);
                else copyText(ex);
            }).show();
            return true;
        });

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return assetLoader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handleUrl(r.getUrl());
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                trusted = isInternal(Uri.parse(url == null ? "" : url));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                swipe.setRefreshing(false);
                hideSplash();
                if (url != null && !url.startsWith("about:") && !url.startsWith("data:")) errorUrl = null;
                String js = "";
                if (BRIDGE) js += "window.print=function(){AndroidApp.print()};";
                if (DARK) js += "(function(){if(document.getElementById('w2a-dark'))return;var s=document.createElement('style');s.id='w2a-dark';"
                        + "s.textContent='html{filter:invert(1) hue-rotate(180deg);background:#fff}img,video,canvas,picture{filter:invert(1) hue-rotate(180deg)}';"
                        + "document.head.appendChild(s)})();";
                if (!js.isEmpty()) v.evaluateJavascript(js, null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    showError(v, r.getUrl().toString());
                    hideSplash();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (!SHOW_PROGRESS) return;
                bar.setProgress(p);
                bar.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }

            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setTitle(APP_NAME).setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setTitle(APP_NAME).setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String msg, String def, JsPromptResult r) {
                final EditText box = new EditText(MainActivity.this);
                box.setText(def);
                new AlertDialog.Builder(MainActivity.this).setTitle(APP_NAME).setMessage(msg).setView(box)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm(box.getText().toString()))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView w, String url, Bitmap f) {
                        if (url == null || url.equals("about:blank")) return;
                        w.stopLoading();
                        openPopup(Uri.parse(url));
                        w.post(w::destroy);
                    }
                });
                WebView.WebViewTransport tr = (WebView.WebViewTransport) msg.obj;
                tr.setWebView(tmp);
                msg.sendToTarget();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCb = cb;
                view.setBackgroundColor(Color.BLACK);
                rootView.addView(view, new FrameLayout.LayoutParams(-1, -1));
                swipe.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                hideCustom();
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> download(url, ua, cd, mime));
    }

    private boolean handleUrl(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("file")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }
        if (EXTERNAL_LINKS && scheme.startsWith("http")) {
            String host = u.getHost() == null ? "" : u.getHost();
            String base = Uri.parse(START_URL).getHost();
            boolean internal = host.equals("appassets.androidplatform.net")
                    || (base != null && (host.equals(base) || host.endsWith("." + base)));
            if (!internal) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        }
        return false;
    }

    private boolean isInternal(Uri u) {
        String host = u.getHost() == null ? "" : u.getHost();
        String base = Uri.parse(START_URL).getHost();
        return host.equals("appassets.androidplatform.net")
                || (base != null && (host.equals(base) || host.endsWith("." + base)));
    }

    private boolean online() {
        try {
            ConnectivityManager c = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo n = c.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private void applyCache() {
        if (web == null) return;
        web.getSettings().setCacheMode(CACHE && !online() ? WebSettings.LOAD_CACHE_ELSE_NETWORK : WebSettings.LOAD_DEFAULT);
    }

    private void watchNetwork() {
        try {
            ConnectivityManager c = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    runOnUiThread(() -> {
                        applyCache();
                        if (errorUrl != null && web != null) {
                            String u = errorUrl;
                            errorUrl = null;
                            web.loadUrl(u);
                        }
                    });
                }

                @Override
                public void onLost(Network n) {
                    runOnUiThread(() -> applyCache());
                }
            };
            c.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), netCb);
        } catch (Exception ignored) {
        }
    }

    private String deepUrl(Intent i) {
        if (DEEP && i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            String sc = i.getData().getScheme();
            if ("http".equals(sc) || "https".equals(sc)) return i.getData().toString();
        }
        return START_URL;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (DEEP && web != null && Intent.ACTION_VIEW.equals(i.getAction())) web.loadUrl(deepUrl(i));
    }

    private void viewExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) {
        }
    }

    private void openPopup(Uri u) {
        if (NEW_EXT && !isInternal(u)) viewExternal(u);
        else web.loadUrl(u.toString());
    }

    private void shareText(String t) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, t);
        startActivity(Intent.createChooser(i, null));
    }

    private void copyText(String t) {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("", t));
        toastUi("Disalin");
    }

    private void toastUi(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    private void hideCustom() {
        if (customView == null) return;
        rootView.removeView(customView);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
        swipe.setVisibility(View.VISIBLE);
        applyUi();
    }

    private void saveBytes(byte[] data, String mime, String name) {
        try {
            if (name == null || name.isEmpty()) name = "file";
            name = name.replace('/', '_');
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream o = getContentResolver().openOutputStream(u);
                o.write(data);
                o.close();
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                FileOutputStream o = new FileOutputStream(new File(dir, name));
                o.write(data);
                o.close();
            }
            toastUi("Tersimpan di Unduhan: " + name);
        } catch (Exception e) {
            toastUi("Gagal menyimpan file");
        }
    }

    private void fetchBlob(String url, String mime, String name) {
        String js = "(function(){var x=new XMLHttpRequest();x.open('GET'," + JSONObject.quote(url) + ",true);x.responseType='blob';"
                + "x.onload=function(){var r=new FileReader();r.onloadend=function(){AndroidApp.saveFile(r.result.split(',')[1],"
                + JSONObject.quote(mime == null ? "" : mime) + "||x.response.type," + JSONObject.quote(name)
                + ")};r.readAsDataURL(x.response)};x.send()})()";
        web.evaluateJavascript(js, null);
    }

    private void download(String url, String ua, String cd, String mime) {
        String name = URLUtil.guessFileName(url, cd, mime);
        if (url.startsWith("blob:")) {
            fetchBlob(url, mime, name);
            return;
        }
        if (url.startsWith("data:")) {
            try {
                int i = url.indexOf(',');
                String head = url.substring(5, i);
                byte[] d = head.contains(";base64") ? Base64.decode(url.substring(i + 1), Base64.DEFAULT)
                        : Uri.decode(url.substring(i + 1)).getBytes("UTF-8");
                saveBytes(d, head.split(";")[0], name);
            } catch (Exception e) {
                toastUi("Unduhan tidak didukung");
            }
            return;
        }
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (mime != null) r.setMimeType(mime);
            String ck = CookieManager.getInstance().getCookie(url);
            if (ck != null) r.addRequestHeader("cookie", ck);
            if (ua != null) r.addRequestHeader("User-Agent", ua);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            toastUi("Mengunduh " + name);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                toastUi("Unduhan tidak didukung");
            }
        }
    }

    private class Bridge {
        private boolean ok() {
            return BRIDGE && trusted;
        }

        @JavascriptInterface
        public void saveFile(String b64, String mime, String name) {
            try {
                saveBytes(Base64.decode(b64, Base64.DEFAULT), mime, name);
            } catch (Exception e) {
                toastUi("Gagal menyimpan file");
            }
        }

        @JavascriptInterface
        public void toast(String m) {
            if (ok()) toastUi(m);
        }

        @JavascriptInterface
        public void share(String t) {
            if (ok()) runOnUiThread(() -> shareText(t));
        }

        @JavascriptInterface
        public void copy(String t) {
            if (ok()) runOnUiThread(() -> copyText(t));
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            if (!ok()) return;
            Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vb != null) vb.vibrate(Math.min(Math.max(ms, 1), 2000));
        }

        @JavascriptInterface
        public void openUrl(String u) {
            if (ok()) runOnUiThread(() -> viewExternal(Uri.parse(u)));
        }

        @JavascriptInterface
        public void exit() {
            if (ok()) runOnUiThread(() -> finishAffinity());
        }

        @JavascriptInterface
        public void print() {
            if (!ok()) return;
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                pm.print(APP_NAME, web.createPrintDocumentAdapter(APP_NAME), new PrintAttributes.Builder().build());
            });
        }

        @JavascriptInterface
        public String info() {
            if (!ok()) return "{}";
            try {
                JSONObject o = new JSONObject();
                o.put("app", APP_NAME);
                o.put("package", getPackageName());
                o.put("version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                o.put("android", Build.VERSION.SDK_INT);
                o.put("online", online());
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void notification(String title, String text) {
            if (!ok() || !NOTIF) return;
            runOnUiThread(() -> {
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    Notification.Builder b;
                    if (Build.VERSION.SDK_INT >= 26) {
                        nm.createNotificationChannel(new NotificationChannel("w2a", APP_NAME, NotificationManager.IMPORTANCE_DEFAULT));
                        b = new Notification.Builder(MainActivity.this, "w2a");
                    } else {
                        b = new Notification.Builder(MainActivity.this);
                    }
                    b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(text).setAutoCancel(true)
                            .setContentIntent(PendingIntent.getActivity(MainActivity.this, 0,
                                    new Intent(MainActivity.this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
                    nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void showError(WebView v, String failed) {
        errorUrl = failed;
        String h = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;"
                + "font-family:sans-serif;background:#f2f2f7;color:#1c1c1e;text-align:center'>"
                + "<div style='font-size:48px'>&#128246;</div><h3>Tidak dapat terhubung</h3>"
                + "<p style='color:#8e8e93;margin:0 24px 20px'>Periksa koneksi internet lalu coba lagi.</p>"
                + "<a href='" + TextUtils.htmlEncode(failed) + "' style='background:#007aff;color:#fff;padding:12px 28px;"
                + "border-radius:12px;text-decoration:none;font-weight:600'>Coba lagi</a></body></html>";
        v.loadDataWithBaseURL(null, h, "text/html", "UTF-8", null);
    }

    private void hideSplash() {
        if (splash == null || splashGone) return;
        splashGone = true;
        splash.animate().alpha(0f).setDuration(250).withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }

    private void requestRuntimePerms() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> need = new ArrayList<>();
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (STORE && Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (NOTIF && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    private void applyUi() {
        if (customView != null) return;
        int f = 0;
        if (FULLSCREEN) {
            f = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        } else if (Build.VERSION.SDK_INT >= 23 && STATUS_LIGHT) {
            f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(f);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyUi();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        applyCache();
    }

    @Override
    protected void onDestroy() {
        try {
            if (netCb != null) ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb);
        } catch (Exception ignored) {
        }
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustom();
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        if (DBL_BACK && System.currentTimeMillis() - lastBack > 2000) {
            lastBack = System.currentTimeMillis();
            Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }
}
