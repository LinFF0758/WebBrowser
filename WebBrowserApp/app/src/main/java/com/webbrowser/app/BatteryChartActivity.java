package com.webbrowser.app;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BatteryChartActivity extends AppCompatActivity {

    private LineChart lineChart;
    private TextView chartInfo;
    private Button btnRefresh, btnResetZoom;

    private List<BatteryData> allData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_chart);

        lineChart = findViewById(R.id.line_chart);
        chartInfo = findViewById(R.id.chart_info);
        btnRefresh = findViewById(R.id.btn_refresh_chart);
        btnResetZoom = findViewById(R.id.btn_reset_zoom);

        setupChart();
        loadData();

        btnRefresh.setOnClickListener(v -> loadData());
        btnResetZoom.setOnClickListener(v -> lineChart.fitScreen());
    }

    private void setupChart() {
        // 基础设置
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDoubleTapToZoomEnabled(true);
        lineChart.setHighlightPerDragEnabled(true);
        lineChart.setDrawBorders(false);
        lineChart.setNoDataText(getString(R.string.no_data));
        lineChart.setNoDataTextColor(Color.GRAY);
        lineChart.setNoDataTextAlignment(Paint.Align.CENTER);

        // 只允许X轴缩放（时间轴），固定Y轴
        lineChart.setScaleXEnabled(true);
        lineChart.setScaleYEnabled(false);

        // X轴配置
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridLineWidth(0.5f);
        xAxis.setGridColor(Color.LTGRAY);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setTextSize(10f);
        xAxis.setAvoidFirstLastClipping(true);

        // 左Y轴 - 电量百分比
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setLabelCount(6, true);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setTextSize(11f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int) value + "%";
            }
        });

        // 添加充电阈值线
        LimitLine fullLine = new LimitLine(100f, "满电");
        fullLine.setLineColor(Color.GREEN);
        fullLine.setLineWidth(1f);
        fullLine.enableDashedLine(10f, 5f, 0f);
        fullLine.setTextSize(10f);
        leftAxis.addLimitLine(fullLine);

        LimitLine lowLine = new LimitLine(15f, "低电量");
        lowLine.setLineColor(Color.RED);
        lowLine.setLineWidth(1f);
        lowLine.enableDashedLine(10f, 5f, 0f);
        lowLine.setTextSize(10f);
        leftAxis.addLimitLine(lowLine);

        // 右Y轴 - 隐藏
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 图例
        Legend legend = lineChart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(11f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        // 标注点击
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                if (index >= 0 && index < allData.size()) {
                    BatteryData data = allData.get(index);
                    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
                    String info = sdf.format(new Date(data.timestamp))
                            + "\n电量: " + data.level + "%"
                            + "  状态: " + (data.isCharging ? "充电中" : "放电中")
                            + "\n电压: " + String.format(Locale.getDefault(), "%.2f", data.voltage / 1000.0f) + "V"
                            + "  温度: " + String.format(Locale.getDefault(), "%.1f", data.temperature) + "℃";
                    chartInfo.setText(info);
                }
            }

            @Override
            public void onNothingSelected() {
                updateInfoText();
            }
        });
    }

    private void loadData() {
        allData = BatteryLoggerService.readAllLogs(this);
        if (allData.isEmpty()) {
            lineChart.clear();
            lineChart.invalidate();
            chartInfo.setText(R.string.no_data);
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show();
            return;
        }

        updateChart();
        updateInfoText();
    }

    private void updateChart() {
        if (allData.isEmpty()) return;

        // 分别创建充电和放电的数据集
        List<Entry> allEntries = new ArrayList<>();
        List<Entry> chargingEntries = new ArrayList<>();
        List<Entry> dischargingEntries = new ArrayList<>();

        for (int i = 0; i < allData.size(); i++) {
            BatteryData data = allData.get(i);
            allEntries.add(new Entry(i, data.level));
            if (data.isCharging) {
                chargingEntries.add(new Entry(i, data.level));
                dischargingEntries.add(new Entry(i, 0)); // 占位
            } else {
                chargingEntries.add(new Entry(i, 0)); // 占位
                dischargingEntries.add(new Entry(i, data.level));
            }
        }

        // 主曲线 - 深蓝色
        LineDataSet mainSet = new LineDataSet(allEntries, "电量");
        mainSet.setColor(Color.rgb(33, 150, 243));
        mainSet.setCircleColor(Color.rgb(33, 150, 243));
        mainSet.setLineWidth(2f);
        mainSet.setCircleRadius(3f);
        mainSet.setDrawCircleHole(false);
        mainSet.setDrawValues(false);
        mainSet.setMode(LineDataSet.Mode.LINEAR);
        mainSet.setDrawFilled(true);
        mainSet.setFillColor(Color.argb(30, 33, 150, 243));
        mainSet.setHighLightColor(Color.rgb(244, 67, 54));
        mainSet.setHighlightLineWidth(1.5f);
        mainSet.enableDashedHighlightLine(5f, 5f, 0f);

        // 放电段 - 橙色标记
        LineDataSet dischargingSet = new LineDataSet(dischargingEntries, "放电");
        dischargingSet.setColor(Color.rgb(255, 152, 0));
        dischargingSet.setCircleColor(Color.rgb(255, 152, 0));
        dischargingSet.setLineWidth(0f);
        dischargingSet.setCircleRadius(4f);
        dischargingSet.setDrawCircleHole(true);
        dischargingSet.setCircleHoleRadius(2f);
        dischargingSet.setCircleHoleColor(Color.WHITE);
        dischargingSet.setDrawValues(false);
        dischargingSet.setMode(LineDataSet.Mode.LINEAR);
        dischargingSet.setHighlightEnabled(false);

        // 充电段 - 绿色标记
        LineDataSet chargingSet = new LineDataSet(chargingEntries, "充电");
        chargingSet.setColor(Color.rgb(76, 175, 80));
        chargingSet.setCircleColor(Color.rgb(76, 175, 80));
        chargingSet.setLineWidth(0f);
        chargingSet.setCircleRadius(4f);
        chargingSet.setDrawCircleHole(true);
        chargingSet.setCircleHoleRadius(2f);
        chargingSet.setCircleHoleColor(Color.WHITE);
        chargingSet.setDrawValues(false);
        chargingSet.setMode(LineDataSet.Mode.LINEAR);
        chargingSet.setHighlightEnabled(false);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dischargingSet);
        dataSets.add(chargingSet);
        dataSets.add(mainSet);

        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);

        // 设置X轴标签格式化器 - 时间戳转可读时间
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat hourMin = new SimpleDateFormat("HH:mm", Locale.getDefault());
            private final SimpleDateFormat monthDay = new SimpleDateFormat("MM/dd", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index < 0 || index >= allData.size()) return "";
                BatteryData data = allData.get(index);
                // 根据数据跨度决定显示格式
                if (allData.size() > 1) {
                    long span = allData.get(allData.size() - 1).timestamp - allData.get(0).timestamp;
                    if (span > 24 * 3600 * 1000) {
                        return monthDay.format(new Date(data.timestamp));
                    }
                }
                return hourMin.format(new Date(data.timestamp));
            }
        });

        // 自动适配显示
        lineChart.fitScreen();
        lineChart.invalidate();
    }

    private void updateInfoText() {
        if (allData.isEmpty()) {
            chartInfo.setText(R.string.no_data);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        BatteryData first = allData.get(0);
        BatteryData last = allData.get(allData.size() - 1);

        // 统计充电段
        int chargeCount = 0;
        boolean wasCharging = false;
        for (BatteryData d : allData) {
            if (d.isCharging && !wasCharging) {
                chargeCount++;
            }
            wasCharging = d.isCharging;
        }

        String info = "数据范围: " + sdf.format(new Date(first.timestamp))
                + " ~ " + sdf.format(new Date(last.timestamp))
                + "\n记录数: " + allData.size()
                + "  电量范围: " + getMinLevel() + "% ~ " + getMaxLevel() + "%"
                + "  充电次数: " + chargeCount
                + "\n当前: " + last.level + "%  "
                + (last.isCharging ? "⚡充电中" : "🔋放电中");
        chartInfo.setText(info);
    }

    private int getMinLevel() {
        int min = 100;
        for (BatteryData d : allData) {
            if (d.level < min) min = d.level;
        }
        return min;
    }

    private int getMaxLevel() {
        int max = 0;
        for (BatteryData d : allData) {
            if (d.level > max) max = d.level;
        }
        return max;
    }
}
