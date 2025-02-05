package com.example.sanxiang;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.highlight.Highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PredictionDetailActivity extends AppCompatActivity 
{   
    private DatabaseHelper dbHelper;
    private LineChart lineChart;
    private TextView tvUserInfo;
    private TextView tvPredictionProcess;
    private String userId;
    private String userName;
    private String routeName;
    private String routeNumber;
    private String phase;
    private double predictedPhaseA;
    private double predictedPhaseB;
    private double predictedPhaseC;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction_detail);

        // 初始化视图
        lineChart = findViewById(R.id.lineChart);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        tvPredictionProcess = findViewById(R.id.tvPredictionProcess);
        dbHelper = new DatabaseHelper(this);

        // 获取传递的用户信息和预测结果
        userId = getIntent().getStringExtra("userId");
        userName = getIntent().getStringExtra("userName");
        routeName = getIntent().getStringExtra("routeName");
        routeNumber = getIntent().getStringExtra("routeNumber");
        phase = getIntent().getStringExtra("phase");
        predictedPhaseA = getIntent().getDoubleExtra("predictedPhaseAPower", 0.0);
        predictedPhaseB = getIntent().getDoubleExtra("predictedPhaseBPower", 0.0);
        predictedPhaseC = getIntent().getDoubleExtra("predictedPhaseCPower", 0.0);

        // 设置用户信息
        String userInfo = String.format(
            "用户编号：%s\n" +
            "用户名称：%s\n" +
            "回路编号：%s\n" +
            "线路名称：%s",
            userId, userName, routeNumber, routeName
        );
        tvUserInfo.setText(userInfo);

        // 设置图表
        setupChart();
        
        // 加载历史数据
        loadHistoricalData();

        // 设置悬浮按钮点击事件
        FloatingActionButton fabShowProcess = findViewById(R.id.fabShowProcess);
        fabShowProcess.setOnClickListener(v -> showPredictionProcess());
    }

    private void setupChart() 
    {
        // 配置图表
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(false);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setDrawGridBackground(false);

        // 调整边距
        lineChart.setExtraBottomOffset(20f);
        lineChart.setExtraLeftOffset(10f);
        lineChart.setExtraRightOffset(25f);
        lineChart.setExtraTopOffset(10f);

        // 配置X轴
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-30);
        xAxis.setTextSize(11f);
        xAxis.setYOffset(5f);

        // 配置Y轴
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextSize(12f);
        lineChart.getAxisRight().setEnabled(false);

        // 配置图例
        lineChart.getLegend().setTextSize(12f);
        lineChart.getLegend().setFormSize(12f);
    }

    private void loadHistoricalData() 
    {
        // 获取30天的历史数据
        List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 30);
        if (historicalData.isEmpty()) 
        {
            return;
        }

        // 准备图表数据
        List<Entry> entriesA = new ArrayList<>();
        List<Entry> entriesB = new ArrayList<>();
        List<Entry> entriesC = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        // 注意：数据库返回的是按日期降序排序的，我们需要反转顺序来显示
        for (int i = historicalData.size() - 1; i >= 0; i--) 
        {
            UserData data = historicalData.get(i);
            int position = historicalData.size() - 1 - i;  // 转换索引
            entriesA.add(new Entry(position, (float) data.getPhaseAPower()));
            entriesB.add(new Entry(position, (float) data.getPhaseBPower()));
            entriesC.add(new Entry(position, (float) data.getPhaseCPower()));
            dates.add(data.getDate());
        }

        // 添加预测点
        int lastPosition = dates.size();
        entriesA.add(new Entry(lastPosition, (float) predictedPhaseA));
        entriesB.add(new Entry(lastPosition, (float) predictedPhaseB));
        entriesC.add(new Entry(lastPosition, (float) predictedPhaseC));
        dates.add("预测值");

        // 创建数据集
        LineDataSet setA = new LineDataSet(entriesA, "A相");
        setA.setColor(Color.RED);
        setA.setCircleColor(Color.RED);

        LineDataSet setB = new LineDataSet(entriesB, "B相");
        setB.setColor(Color.GREEN);
        setB.setCircleColor(Color.GREEN);

        LineDataSet setC = new LineDataSet(entriesC, "C相");
        setC.setColor(Color.BLUE);
        setC.setCircleColor(Color.BLUE);

        // 配置数据集
        for (LineDataSet set : new LineDataSet[]{setA, setB, setC}) 
        {
            set.setDrawCircles(true);
            set.setCircleRadius(4f);
            set.setDrawValues(false);
            set.setLineWidth(2f);
            set.setMode(LineDataSet.Mode.LINEAR);
        }

        // 设置X轴标签
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));

        // 设置点击监听
        final List<String> finalDates = dates;
        final List<UserData> finalHistoricalData = historicalData;
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener()
        {
            @Override
            public void onValueSelected(Entry e, Highlight h)
            {
                int index = (int) e.getX();
                updatePowerInfo(index, finalDates, finalHistoricalData);
            }

            @Override
            public void onNothingSelected()
            {
                // 当没有选中点时，显示预测结果
                updatePowerInfo(finalDates.size() - 1, finalDates, finalHistoricalData);
            }
        });

        // 更新图表
        LineData lineData = new LineData(setA, setB, setC);
        lineChart.setData(lineData);
        lineChart.invalidate();

        // 初始显示预测结果
        updatePowerInfo(dates.size() - 1, dates, historicalData);
    }

    //显示预测结果及历史记录
    private void updatePowerInfo(int index, List<String> dates, List<UserData> historicalData)
    {
        try
        {
            if (index < dates.size() - 1)  // 历史数据
            {
                UserData data = historicalData.get(historicalData.size() - 1 - index);
                String powerInfo = String.format(
                    "日期：%s\n" +
                    "相位：%s\n" +
                    "A相电量：%.2f\n" +
                    "B相电量：%.2f\n" +
                    "C相电量：%.2f",
                    data.getDate(),
                    data.getPhase(),
                    data.getPhaseAPower(),
                    data.getPhaseBPower(),
                    data.getPhaseCPower()
                );
                tvPredictionProcess.setText(powerInfo);
            }
            else  // 预测值
            {
                String powerInfo = String.format(
                    "预测结果：\n" +
                    "相位：%s\n" +
                    "A相电量：%.2f\n" +
                    "B相电量：%.2f\n" +
                    "C相电量：%.2f",
                    phase,
                    predictedPhaseA,
                    predictedPhaseB,
                    predictedPhaseC
                );
                tvPredictionProcess.setText(powerInfo);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "获取电量数据出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //显示具体预测过程
    private void showPredictionProcess()
    {
        // 创建对话框显示预测过程
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("预测过程详情");
        
        // 获取历史数据
        List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 30);
        if (historicalData.isEmpty())
        {
            Toast.makeText(this, "没有历史数据", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 构建预测过程说明
        StringBuilder process = new StringBuilder();
        process.append("1. 数据准备：\n");
        process.append("   - 收集最近30天的历史数据\n");
        process.append("   - 数据点数量：").append(historicalData.size()).append("\n\n");
        
        process.append("2. 数据预处理：\n");
        process.append("   - 移除异常值\n");
        process.append("   - 数据归一化\n\n");
        
        process.append("3. 模型训练：\n");
        process.append("   - 使用改进的Holt-Winters算法\n");
        process.append("   - 考虑季节性(周期：7天)\n");
        process.append("   - 加入自适应权重\n\n");
        
        process.append("4. 预测结果：\n");
        process.append(String.format("   A相：%.2f\n", predictedPhaseA));
        process.append(String.format("   B相：%.2f\n", predictedPhaseB));
        process.append(String.format("   C相：%.2f\n", predictedPhaseC));
        
        // 创建带滚动条的文本视图
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(process.toString());
        textView.setPadding(30, 20, 30, 20);
        textView.setTextSize(14);
        scrollView.addView(textView);
        
        builder.setView(scrollView)
               .setPositiveButton("确定", null)
               .show();
    }
}