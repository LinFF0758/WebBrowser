package com.webbrowser.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogViewerActivity extends AppCompatActivity {

    private TextView logText;
    private Button btnRefresh, btnExport, btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        logText = findViewById(R.id.log_text);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnExport = findViewById(R.id.btn_export);
        btnClear = findViewById(R.id.btn_clear);

        btnRefresh.setOnClickListener(v -> refreshLog());
        btnExport.setOnClickListener(v -> exportLog());
        btnClear.setOnClickListener(v -> clearLog());

        refreshLog();
    }

    private void refreshLog() {
        List<BatteryData> logs = BatteryLoggerService.readAllLogs(this);
        if (logs.isEmpty()) {
            logText.setText(R.string.no_data);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("===== 电池监控日志 =====\n");
        sb.append("时间 | 状态 | 电量 | 电压 | 温度\n");
        sb.append("----------------------------\n");

        int count = Math.min(logs.size(), 500); // 最多显示500条
        int start = Math.max(0, logs.size() - 500);
        for (int i = start; i < logs.size(); i++) {
            BatteryData data = logs.get(i);
            sb.append(sdf.format(new Date(data.timestamp)))
                    .append(" | ");
            sb.append(data.isCharging ? "⚡充" : "🔋放")
                    .append(" | ");
            sb.append(data.level).append("% | ");
            sb.append(String.format(Locale.getDefault(), "%.2fV", data.voltage / 1000.0f))
                    .append(" | ");
            sb.append(String.format(Locale.getDefault(), "%.1f℃", data.temperature))
                    .append("\n");
        }

        if (logs.size() > 500) {
            sb.append("\n... 仅显示最近500条记录，共 ").append(logs.size()).append(" 条");
        }

        logText.setText(sb.toString());

        // 滚动到底部
        final ScrollView scrollView = (ScrollView) logText.getParent();
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void exportLog() {
        String text = BatteryLoggerService.exportLogsAsText(this);
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show();
            return;
        }

        // 复制到剪贴板
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("battery_log", text);
        clipboard.setPrimaryClip(clip);

        // 也通过分享发送
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "导出电池日志"));

        Toast.makeText(this, "日志已复制到剪贴板并可分享", Toast.LENGTH_LONG).show();
    }

    private void clearLog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空所有电池日志吗？")
                .setPositiveButton("清空", (dialog, which) -> {
                    BatteryLoggerService.clearLogs(this);
                    logText.setText(R.string.no_data);
                    Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
