package com.webbrowser.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlInput;
    private ProgressBar progressBar;
    private Button btnGo, btnLocal, btnSettings;

    private PowerManager.WakeLock wakeLock;

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

        // 禁止休眠
        keepScreenOn();

        // 初始化视图
        initViews();

        // 设置WebView
        setupWebView();

        // 设置按钮事件
        setupButtons();

        // 启动电池监控服务
        startBatteryServiceIfPermitted();

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
    private void keepScreenOn() {
        // 方法1: Window flag
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 方法2: WakeLock 更强制
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ON_AFTER_RELEASE,
                    "WebBrowser::WakeLock");
            wakeLock.acquire();
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        urlInput = findViewById(R.id.url_input);
        progressBar = findViewById(R.id.progress_bar);
        btnGo = findViewById(R.id.btn_go);
        btnLocal = findViewById(R.id.btn_local);
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
        // 暂停WebView（可选）
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        // 恢复全屏
        setFullScreen();
    }

    @Override
    protected void onDestroy() {
        // 释放WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        // 清理WebView
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
