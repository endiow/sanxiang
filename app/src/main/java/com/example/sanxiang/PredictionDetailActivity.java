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
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.highlight.Highlight;

import java.util.ArrayList;
import java.util.List;

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
        
        // 1. 数据准备
        process.append("1. 数据准备：\n");
        process.append("   - 收集最近30天的历史数据\n");
        process.append("   - 实际获得数据点数量：").append(historicalData.size()).append("\n");
        process.append("   - 数据时间范围：").append(historicalData.get(historicalData.size()-1).getDate())
              .append(" 至 ").append(historicalData.get(0).getDate()).append("\n\n");
        
        // 2. 数据预处理
        process.append("2. 数据预处理：\n");
        process.append("   - 数据完整性检查\n");
        process.append("   - 缺失值处理：使用0.0填充\n");
        process.append("   - 数据排序：按日期升序排列\n");
        
        // 计算历史数据的统计信息
        double[] statsA = calculateStats(historicalData, d -> d.getPhaseAPower());
        double[] statsB = calculateStats(historicalData, d -> d.getPhaseBPower());
        double[] statsC = calculateStats(historicalData, d -> d.getPhaseCPower());
        
        process.append(String.format("   - 统计信息：\n"));
        process.append(String.format("     A相：均值=%.2f, 标准差=%.2f\n", statsA[0], statsA[1]));
        process.append(String.format("     B相：均值=%.2f, 标准差=%.2f\n", statsB[0], statsB[1]));
        process.append(String.format("     C相：均值=%.2f, 标准差=%.2f\n\n", statsC[0], statsC[1]));
        
        // 3. 预测模型
        process.append("3. 预测模型：\n");
        String methodName = historicalData.size() >= 7 ? "Holt-Winters指数平滑" : "加权移动平均";
        process.append("   - 使用模型：").append(methodName).append("\n");
        
        if (historicalData.size() >= 7)
        {
            process.append("   - 模型参数：\n");
            process.append("     * 季节性：加法模型\n");
            process.append("     * 季节周期：7天\n");
            process.append("     * 趋势：加法模型（带阻尼）\n");
            process.append("     * Box-Cox变换：是\n");
            process.append("     * 偏差移除：是\n");
        }
        else
        {
            process.append("   - 由于数据量不足7天，使用加权移动平均\n");
            process.append("   - 权重：指数衰减\n");
        }
        process.append("\n");
        
        // 4. 预测结果
        process.append("4. 预测结果：\n");
        process.append(String.format("   A相：%.2f （95%%置信区间：%.2f - %.2f）\n", 
            predictedPhaseA, 
            Math.max(0, predictedPhaseA - 1.96 * statsA[1]),
            predictedPhaseA + 1.96 * statsA[1]));
        process.append(String.format("   B相：%.2f （95%%置信区间：%.2f - %.2f）\n", 
            predictedPhaseB,
            Math.max(0, predictedPhaseB - 1.96 * statsB[1]),
            predictedPhaseB + 1.96 * statsB[1]));
        process.append(String.format("   C相：%.2f （95%%置信区间：%.2f - %.2f）\n\n",
            predictedPhaseC,
            Math.max(0, predictedPhaseC - 1.96 * statsC[1]),
            predictedPhaseC + 1.96 * statsC[1]));
        
        // 5. 可靠性分析
        process.append("5. 预测可靠性分析：\n");
        double maxPower = Math.max(Math.max(predictedPhaseA, predictedPhaseB), predictedPhaseC);
        double minPower = Math.min(Math.min(predictedPhaseA, predictedPhaseB), predictedPhaseC);
        double avgPower = (predictedPhaseA + predictedPhaseB + predictedPhaseC) / 3;
        double variance = Math.sqrt(
            (Math.pow(predictedPhaseA - avgPower, 2) + 
             Math.pow(predictedPhaseB - avgPower, 2) + 
             Math.pow(predictedPhaseC - avgPower, 2)) / 3
        );
        double variationCoef = (variance / avgPower) * 100;
        
        process.append(String.format("   - 预测值范围：%.2f - %.2f\n", minPower, maxPower));
        process.append(String.format("   - 平均值：%.2f\n", avgPower));
        process.append(String.format("   - 标准差：%.2f\n", variance));
        process.append(String.format("   - 变异系数：%.2f%%\n", variationCoef));
        process.append("   - 置信水平：95%\n");
        process.append("   - 可信度评估：").append(getReliabilityLevel(variationCoef)).append("\n\n");
        
        // 6. 建议
        process.append("6. 预测建议：\n");
        appendRecommendations(process, variationCoef, statsA[0], statsB[0], statsC[0]);
        
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

    // 计算统计信息（均值和标准差）
    private double[] calculateStats(List<UserData> data, PowerExtractor extractor)
    {
        double sum = 0;
        double squareSum = 0;
        int count = data.size();
        
        for (UserData d : data)
        {
            double value = extractor.getPower(d);
            sum += value;
            squareSum += value * value;
        }
        
        double mean = sum / count;
        double variance = (squareSum / count) - (mean * mean);
        double stdDev = Math.sqrt(variance);
        
        return new double[]{mean, stdDev};
    }

    // 计算偏差百分比
    private double calculateDeviation(double value, double mean)
    {
        return ((value - mean) / mean) * 100;
    }

    // 获取可信度评估
    private String getReliabilityLevel(double variationCoef)
    {
        if (variationCoef < 10)
        {
            return "很高（变异系数<10%）";
        }
        else if (variationCoef < 20)
        {
            return "较高（变异系数<20%）";
        }
        else if (variationCoef < 30)
        {
            return "中等（变异系数<30%）";
        }
        else
        {
            return "较低（变异系数≥30%）";
        }
    }

    // 添加建议
    private void appendRecommendations(StringBuilder process, double variationCoef,
                                     double meanA, double meanB, double meanC)
    {
        // 基于变异系数的建议
        if (variationCoef >= 30)
        {
            process.append("   - 预测波动较大，建议：\n");
            process.append("     * 密切关注实际用电情况\n");
            process.append("     * 考虑增加检测频率\n");
            process.append("     * 分析用电模式变化原因\n");
        }
        
        // 基于相位均值对比的建议
        double maxMean = Math.max(Math.max(meanA, meanB), meanC);
        double minMean = Math.min(Math.min(meanA, meanB), meanC);
        if ((maxMean - minMean) / maxMean > 0.2)
        {
            process.append("   - 三相负载不平衡，建议：\n");
            process.append("     * 检查负载分布\n");
            process.append("     * 考虑进行相位调整\n");
            process.append("     * 评估设备运行状况\n");
        }
        
        // 基于预测值与历史均值对比的建议
        if (Math.abs(predictedPhaseA - meanA) / meanA > 0.2 ||
            Math.abs(predictedPhaseB - meanB) / meanB > 0.2 ||
            Math.abs(predictedPhaseC - meanC) / meanC > 0.2)
        {
            process.append("   - 预测值与历史均值差异较大，建议：\n");
            process.append("     * 分析用电模式变化\n");
            process.append("     * 检查是否有新增用电设备\n");
            process.append("     * 评估是否存在异常用电情况\n");
        }
    }

    // 用于提取电量数据的函数式接口
    @FunctionalInterface
    interface PowerExtractor
    {
        double getPower(UserData data);
    }
}