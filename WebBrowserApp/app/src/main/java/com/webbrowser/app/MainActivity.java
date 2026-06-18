package com.webbrowser.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlInput;
    private ProgressBar progressBar;
    private Button btnGo, btnLocal, btnHistory, btnSettings;

    private PowerManager.WakeLock wakeLock;

    private static final String PREFS_NAME = "browser_prefs";
    private static final String KEY_HISTORY = "url_history";
    private static final String KEY_HTML_COPIED = "html_copied";
    private static final int MAX_HISTORY = 20;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                webView.loadUrl(uri.toString());
                                urlInput.setText(uri.toString());
                            }
                        }
                    });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // 无论是否授权，都启动服务
                        startBatteryService();
                    });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置全屏模式
        setFullScreen();

        // 禁止休眠 - 初始化WakeLock对象（在onResume中acquire）
        initWakeLock();

        // 初始化视图
        initViews();

        // 设置WebView
        setupWebView();

        // 设置按钮事件
        setupButtons();

        // 启动电池监控服务
        startBatteryServiceIfPermitted();

        // 首次启动：拷贝内置HTML到下载目录
        copyHtmlToDownloads();

        // 默认加载
        webView.loadUrl("https://www.google.com");
        urlInput.setText("https://www.google.com");
    }

    private void setFullScreen() {
        // 隐藏状态栏和导航栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        // 移除标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 添加全屏标志
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @SuppressLint("WakelockTimeout")
    private void initWakeLock() {
        // Window flag: Activity可见时自动保持亮屏，不可见时自动失效
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // WakeLock: 更强制，需手动管理生命周期
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ON_AFTER_RELEASE,
                    "WebBrowser::WakeLock");
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        urlInput = findViewById(R.id.url_input);
        progressBar = findViewById(R.id.progress_bar);
        btnGo = findViewById(R.id.btn_go);
        btnLocal = findViewById(R.id.btn_local);
        btnHistory = findViewById(R.id.btn_history);
        btnSettings = findViewById(R.id.btn_settings);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setMixedContentMode(
                android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.getSettings().setSafeBrowsingEnabled(false);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                urlInput.setText(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                urlInput.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                urlInput.setText(url);
                addToHistory(url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupButtons() {
        // 前往按钮
        btnGo.setOnClickListener(v -> navigateToUrl());

        // URL输入框的键盘事件
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateToUrl();
                return true;
            }
            return false;
        });

        // 本地文件按钮
        btnLocal.setOnClickListener(v -> openLocalFile());

        // 历史记录按钮
        btnHistory.setOnClickListener(v -> showHistoryDialog());

        // 设置按钮
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void navigateToUrl() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) return;

        // 如果不是以http/https开头，尝试搜索
        if (!url.startsWith("http://") && !url.startsWith("https://")
                && !url.startsWith("file://") && !url.startsWith("content://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://www.google.com/search?q=" + Uri.encode(url);
            }
        }

        webView.loadUrl(url);
        addToHistory(url);
    }

    private void openLocalFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        String[] mimeTypes = {"text/html", "application/xhtml+xml", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void showSettingsDialog() {
        String[] items = {
                getString(R.string.view_log),
                getString(R.string.view_chart)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, LogViewerActivity.class));
                    } else if (which == 1) {
                        startActivity(new Intent(this, BatteryChartActivity.class));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startBatteryServiceIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(
                        android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        startBatteryService();
    }

    private void startBatteryService() {
        Intent serviceIntent = new Intent(this, BatteryLoggerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // ==================== HTML 拷贝到下载目录 ====================

    private void copyHtmlToDownloads() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_HTML_COPIED, false)) return;

        String fileName = "DimOrderTest2.html";
        File downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null) return;
        if (!downloadsDir.exists()) downloadsDir.mkdirs();

        File destFile = new File(downloadsDir, fileName);
        try (InputStream in = getAssets().open(fileName);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            prefs.edit().putBoolean(KEY_HTML_COPIED, true).apply();
            Toast.makeText(this, fileName + " " + getString(R.string.html_copied), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            // 文件可能已在assets中不存在，静默失败
        }
    }

    // ==================== URL 历史记录 ====================

    private void addToHistory(String url) {
        if (url == null || url.isEmpty() || url.startsWith("data:") || url.startsWith("about:")) return;

        List<String> history = getHistory();
        // 去重：移除已存在的相同URL
        history.remove(url);
        // 插入到最前面
        history.add(0, url);
        // 保留最近20条
        if (history.size() > MAX_HISTORY) {
            history = history.subList(0, MAX_HISTORY);
        }
        saveHistory(history);
    }

    private List<String> getHistory() {
        List<String> list = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    private void saveHistory(List<String> history) {
        JSONArray arr = new JSONArray();
        for (String url : history) {
            arr.put(url);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void showHistoryDialog() {
        List<String> history = getHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, R.string.history_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            String url = history.get(i);
            // 截断显示（太长影响UI）
            items[i] = url.length() > 50 ? url.substring(0, 47) + "..." : url;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.history_title)
                .setItems(items, (dialog, which) -> {
                    String url = history.get(which);
                    urlInput.setText(url);
                    webView.loadUrl(url);
                })
                .setPositiveButton("清空历史", (dialog, which) -> {
                    saveHistory(new ArrayList<>());
                    Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ==================== 生命周期 ====================

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        // 切后台时释放WakeLock，允许系统休眠
        releaseWakeLock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        // 回到前台时重新持有WakeLock
        acquireWakeLock();
        // 恢复全屏
        setFullScreen();
    }

    @Override
    protected void onDestroy() {
        // 释放WakeLock（兜底）
        releaseWakeLock();
        wakeLock = null;
        // 清理WebView
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
