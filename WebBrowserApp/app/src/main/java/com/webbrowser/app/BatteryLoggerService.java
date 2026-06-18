package com.webbrowser.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BatteryLoggerService extends Service {
    private static final String TAG = "BatteryLogger";
    private static final String CHANNEL_ID = "battery_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String LOG_FILE_NAME = "battery_log.csv";
    private static final long LOG_INTERVAL_MS = 60_000; // 每分钟记录一次

    private Handler handler;
    private Runnable logRunnable;
    private BatteryReceiver batteryReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        startLogging();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLogging();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("电池状态监控服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_notification))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startLogging() {
        // 注册电池状态广播接收器
        batteryReceiver = new BatteryReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);

        // 定时记录
        logRunnable = new Runnable() {
            @Override
            public void run() {
                logCurrentBatteryState();
                handler.postDelayed(this, LOG_INTERVAL_MS);
            }
        };
        handler.post(logRunnable);
    }

    private void stopLogging() {
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException ignored) {}
            batteryReceiver = null;
        }
        if (handler != null && logRunnable != null) {
            handler.removeCallbacks(logRunnable);
        }
    }

    private void logCurrentBatteryState() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) return;

        int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int voltage = 0;
        float temperature = 0f;

        // 获取充电状态 - 通过 sticky intent
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        boolean isCharging = false;
        String chargeStatusStr = "UNKNOWN";

        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL);

            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    chargeStatusStr = "CHARGING";
                    break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    chargeStatusStr = "DISCHARGING";
                    break;
                case BatteryManager.BATTERY_STATUS_FULL:
                    chargeStatusStr = "FULL";
                    break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                    chargeStatusStr = "NOT_CHARGING";
                    break;
            }

            voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            temperature = tempRaw / 10.0f;
        }

        BatteryData data = new BatteryData(
                System.currentTimeMillis(),
                level,
                voltage,
                temperature,
                isCharging,
                chargeStatusStr
        );

        writeLog(data);

        // 更新通知
        String content = "电量: " + level + "%  " + (isCharging ? "⚡充电中" : "🔋放电中");
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private synchronized void writeLog(BatteryData data) {
        File file = new File(getFilesDir(), LOG_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(file, true);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(data.toCsvLine());
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "写入日志失败", e);
        }
    }

    /**
     * 读取所有日志记录
     */
    public static List<BatteryData> readAllLogs(Context context) {
        List<BatteryData> list = new ArrayList<>();
        File file = new File(context.getFilesDir(), LOG_FILE_NAME);
        if (!file.exists()) return list;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                BatteryData data = BatteryData.fromCsvLine(line.trim());
                if (data != null) {
                    list.add(data);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取日志失败", e);
        }
        return list;
    }

    /**
     * 清空日志
     */
    public static void clearLogs(Context context) {
        File file = new File(context.getFilesDir(), LOG_FILE_NAME);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 导出日志为文本内容
     */
    public static String exportLogsAsText(Context context) {
        List<BatteryData> list = readAllLogs(context);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("时间,充电状态,电量(%),电压(mV),温度(℃),充电状态码\n");
        for (BatteryData data : list) {
            sb.append(sdf.format(new Date(data.timestamp)))
                    .append(",")
                    .append(data.isCharging ? "充电" : "放电")
                    .append(",")
                    .append(data.level)
                    .append(",")
                    .append(data.voltage)
                    .append(",")
                    .append(String.format(Locale.getDefault(), "%.1f", data.temperature))
                    .append(",")
                    .append(data.chargeStatus)
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * 电池状态变化接收器
     */
    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                // 电池状态变化时也记录一次
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int pct = (int) (level * 100.0 / scale);

                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL);

                String chargeStatusStr;
                switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING:
                        chargeStatusStr = "CHARGING";
                        break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING:
                        chargeStatusStr = "DISCHARGING";
                        break;
                    case BatteryManager.BATTERY_STATUS_FULL:
                        chargeStatusStr = "FULL";
                        break;
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                        chargeStatusStr = "NOT_CHARGING";
                        break;
                    default:
                        chargeStatusStr = "UNKNOWN";
                }

                int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float temperature = tempRaw / 10.0f;

                BatteryData data = new BatteryData(
                        System.currentTimeMillis(),
                        pct,
                        voltage,
                        temperature,
                        isCharging,
                        chargeStatusStr
                );

                writeLog(data);
            }
        }
    }
}
