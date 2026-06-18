package com.webbrowser.app;

/**
 * 电池数据模型
 */
public class BatteryData {
    public long timestamp;
    public int level;
    public int voltage;
    public float temperature;
    public boolean isCharging;
    public String chargeStatus;

    public BatteryData(long timestamp, int level, int voltage, float temperature,
                       boolean isCharging, String chargeStatus) {
        this.timestamp = timestamp;
        this.level = level;
        this.voltage = voltage;
        this.temperature = temperature;
        this.isCharging = isCharging;
        this.chargeStatus = chargeStatus;
    }

    /**
     * 转换为CSV行格式
     */
    public String toCsvLine() {
        return timestamp + "," + isCharging + "," + level + ","
                + voltage + "," + temperature + "," + chargeStatus;
    }

    /**
     * 从CSV行解析
     */
    public static BatteryData fromCsvLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) return null;
        try {
            long ts = Long.parseLong(parts[0]);
            boolean charging = Boolean.parseBoolean(parts[1]);
            int level = Integer.parseInt(parts[2]);
            int voltage = Integer.parseInt(parts[3]);
            float temp = Float.parseFloat(parts[4]);
            String status = parts[5];
            return new BatteryData(ts, level, voltage, temp, charging, status);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
