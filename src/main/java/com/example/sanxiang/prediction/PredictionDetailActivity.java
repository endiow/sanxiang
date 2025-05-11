package com.example.sanxiang.prediction;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sanxiang.R;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.prediction.model.PredictionResult;
import com.example.sanxiang.db.DatabaseHelper;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
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
import java.util.Collections;
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
    private List<String> dates = new ArrayList<>(); // 日期列表

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
        lineChart.setDragEnabled(true);    // 允许拖动
        lineChart.setScaleEnabled(false);  // 禁止缩放
        lineChart.setPinchZoom(false);     // 禁止双指缩放
        lineChart.setDrawGridBackground(false);
        lineChart.setMaxVisibleValueCount(7); // 最大显示7个数据点
        lineChart.setKeepPositionOnRotation(true); // 旋转时保持位置
        
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
        xAxis.setLabelCount(7, true); // 设置X轴标签数为7

        // 配置Y轴
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextSize(12f);
        lineChart.getAxisRight().setEnabled(false);

        // 禁用默认图例
        lineChart.getLegend().setEnabled(false);
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
        dates = new ArrayList<>();
        
        // 反转列表以按时间顺序显示
        Collections.reverse(historicalData);

        // 判断是否为动力用户
        boolean isPowerUser = dbHelper.isPowerUser(userId);

        if (isPowerUser)
        {
            // 动力用户处理逻辑
            setupPowerUserChart(historicalData);
        }
        else
        {
            // 非动力用户：使用单线条变色显示
            List<Entry> entries = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            
            // 当前颜色状态
            int currentColor = Color.GRAY;
            switch (phase.toUpperCase())
            {
                case "A":
                    currentColor = Color.RED;
                    break;
                case "B":
                    currentColor = Color.GREEN;
                    break;
                case "C":
                    currentColor = Color.BLUE;
                    break;
            }

            for (int i = 0; i < historicalData.size(); i++)
            {
                UserData data = historicalData.get(i);
                dates.add(data.getDate());
                
                // 根据相位选择对应的电量值
                float powerValue;
                switch (data.getPhase().toUpperCase())
                {
                    case "A":
                        powerValue = (float) data.getPhaseAPower();
                        break;
                    case "B":
                        powerValue = (float) data.getPhaseBPower();
                        break;
                    case "C":
                        powerValue = (float) data.getPhaseCPower();
                        break;
                    default:
                        powerValue = 0f;
                        break;
                }
                entries.add(new Entry(i, powerValue));
                
                // 查询是否有相位调整记录
                Map<String, Object> adjustment = dbHelper.getUserPhaseAdjustment(userId, data.getDate());
                if (adjustment != null)
                {
                    String newPhase = (String) adjustment.get("newPhase");
                    if (newPhase != null)
                    {
                        switch (newPhase.toUpperCase())
                        {
                            case "A":
                                currentColor = Color.RED;
                                break;
                            case "B":
                                currentColor = Color.GREEN;
                                break;
                            case "C":
                                currentColor = Color.BLUE;
                                break;
                        }
                    }
                }
                colors.add(currentColor);
            }

            // 创建历史数据集
            LineDataSet dataSet = new LineDataSet(entries, "实际值");
            dataSet.setDrawCircles(true);
            dataSet.setCircleRadius(4f);
            dataSet.setDrawValues(false);
            dataSet.setLineWidth(2f);
            dataSet.setMode(LineDataSet.Mode.LINEAR);
            dataSet.setColors(colors);
            dataSet.setCircleColors(colors);

            // 创建预测数据点（虚线）
            List<Entry> predictionEntries = new ArrayList<>();
            
            if (!entries.isEmpty()) {
                // 获取最后一个历史点
                int lastIndex = entries.size() - 1;
                float lastX = entries.get(lastIndex).getX();
                float lastY = entries.get(lastIndex).getY();
                
                // 添加历史最后一个点作为预测线的起点
                predictionEntries.add(new Entry(lastX, lastY));
                
                // 获取预测值
                float predictedValue;
                switch (phase.toUpperCase())
                {
                    case "A":
                        predictedValue = (float) predictedPhaseA;
                        break;
                    case "B":
                        predictedValue = (float) predictedPhaseB;
                        break;
                    case "C":
                        predictedValue = (float) predictedPhaseC;
                        break;
                    default:
                        predictedValue = 0f;
                        break;
                }
                
                // 添加预测点
                predictionEntries.add(new Entry(lastX + 1, predictedValue));
                
                // 添加预测日期标签
                dates.add("预测值");
            }
            
            // 创建预测数据集（虚线）
            LineDataSet predictionSet = new LineDataSet(predictionEntries, "预测值");
            configurePredictionDataSet(predictionSet, currentColor);

            // 禁用默认图例
            lineChart.getLegend().setEnabled(false);

            // 更新图表
            LineData lineData = new LineData(dataSet, predictionSet);
            lineChart.setData(lineData);
        }

        // 设置X轴标签
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dates));
        xAxis.setLabelCount(7); // 设置标签数量为7
        xAxis.setGranularity(1f); // 确保值之间的最小距离
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelRotationAngle(-45); // 旋转角度，避免重叠
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(11f);
        
        // 调整边距，确保标签有足够空间
        lineChart.setExtraBottomOffset(30f);
        
        if (dates.size() > 0) 
        {
            // 设置图表可见范围为7天
            lineChart.setVisibleXRangeMaximum(7);
            
            // 默认显示最近的7天数据
            if (dates.size() > 7) 
            {
                lineChart.moveViewToX(dates.size() - 7);
            } 
            else 
            {
                lineChart.moveViewToX(0);
            }
        }
        
        // 启用拖动，禁用缩放
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);

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
        
        // 刷新图表
        lineChart.invalidate();

        // 初始显示预测结果
        updatePowerInfo(dates.size() - 1, dates, historicalData);
    }

    /**
     * 为动力用户设置图表，处理相位调整
     */
    private void setupPowerUserChart(List<UserData> historicalData)
    {
        // 创建三相数据集
        List<Entry> entriesA = new ArrayList<>();
        List<Entry> entriesB = new ArrayList<>();
        List<Entry> entriesC = new ArrayList<>();
        
        // 创建颜色列表
        List<Integer> colorsA = new ArrayList<>();
        List<Integer> colorsB = new ArrayList<>();
        List<Integer> colorsC = new ArrayList<>();
        
        // 当前颜色状态（初始状态：A相红色，B相绿色，C相蓝色）
        int currentColorA = Color.RED;
        int currentColorB = Color.GREEN;
        int currentColorC = Color.BLUE;
        
        // 处理历史数据
        for (int i = 0; i < historicalData.size(); i++)
        {
            UserData data = historicalData.get(i);
            dates.add(data.getDate());
            
            // 添加三相电量数据
            entriesA.add(new Entry(i, (float) data.getPhaseAPower()));
            entriesB.add(new Entry(i, (float) data.getPhaseBPower()));
            entriesC.add(new Entry(i, (float) data.getPhaseCPower()));
            
            // 查询是否有相位调整记录
            Map<String, Object> adjustment = dbHelper.getUserPhaseAdjustment(userId, data.getDate());
            
            if (adjustment != null)
            {
                // 获取旧相位和新相位
                String oldPhase = (String) adjustment.get("oldPhase");
                String newPhase = (String) adjustment.get("newPhase");
                
                if (oldPhase != null && newPhase != null)
                {
                    // 判断调整步数
                    boolean isOneStep = ((oldPhase.equals("A") && newPhase.equals("B")) ||
                                        (oldPhase.equals("B") && newPhase.equals("C")) ||
                                        (oldPhase.equals("C") && newPhase.equals("A")));
                    
                    // 一步调整（顺时针）：颜色循环变化
                    if (isOneStep)
                    {
                        // 保存当前颜色
                        int tempColorA = currentColorA;
                        int tempColorB = currentColorB;
                        int tempColorC = currentColorC;
                        
                        // 颜色顺时针旋转：红->绿->蓝->红
                        currentColorA = tempColorC;  // A相的颜色变为原来C相的颜色
                        currentColorB = tempColorA;  // B相的颜色变为原来A相的颜色
                        currentColorC = tempColorB;  // C相的颜色变为原来B相的颜色
                    }
                    // 两步调整（逆时针）：颜色逆时针变化
                    else
                    {
                        // 保存当前颜色
                        int tempColorA = currentColorA;
                        int tempColorB = currentColorB;
                        int tempColorC = currentColorC;
                        
                        // 颜色逆时针旋转：红->蓝->绿->红
                        currentColorA = tempColorB;  // A相的颜色变为原来B相的颜色
                        currentColorB = tempColorC;  // B相的颜色变为原来C相的颜色
                        currentColorC = tempColorA;  // C相的颜色变为原来A相的颜色
                    }
                }
            }
            
            // 添加当前颜色
            colorsA.add(currentColorA);
            colorsB.add(currentColorB);
            colorsC.add(currentColorC);
        }

        // 创建历史数据集
        LineDataSet setA = new LineDataSet(entriesA, "A相实际值");
        LineDataSet setB = new LineDataSet(entriesB, "B相实际值");
        LineDataSet setC = new LineDataSet(entriesC, "C相实际值");
        
        // 设置数据集基本属性
        configureDataSet(setA, Color.RED, colorsA);
        configureDataSet(setB, Color.GREEN, colorsB);
        configureDataSet(setC, Color.BLUE, colorsC);
        
        // 创建预测数据点
        List<Entry> predictionA = new ArrayList<>();
        List<Entry> predictionB = new ArrayList<>();
        List<Entry> predictionC = new ArrayList<>();
        
        // 获取最后一个历史点和预测点
        if (!entriesA.isEmpty()) {
            int lastIndex = entriesA.size() - 1;
            float lastX = entriesA.get(lastIndex).getX();
            float lastAY = entriesA.get(lastIndex).getY();
            float lastBY = entriesB.get(lastIndex).getY();
            float lastCY = entriesC.get(lastIndex).getY();
            
            // 添加历史最后一个点作为预测线的起点
            predictionA.add(new Entry(lastX, lastAY));
            predictionB.add(new Entry(lastX, lastBY));
            predictionC.add(new Entry(lastX, lastCY));
            
            // 添加预测点
            predictionA.add(new Entry(lastX + 1, (float) predictedPhaseA));
            predictionB.add(new Entry(lastX + 1, (float) predictedPhaseB));
            predictionC.add(new Entry(lastX + 1, (float) predictedPhaseC));
            
            // 添加预测日期标签
            dates.add("预测值");
        }
        
        // 创建预测数据集（虚线）
        LineDataSet predSetA = new LineDataSet(predictionA, "A相预测值");
        LineDataSet predSetB = new LineDataSet(predictionB, "B相预测值");
        LineDataSet predSetC = new LineDataSet(predictionC, "C相预测值");
        
        // 配置预测数据集（虚线）
        configurePredictionDataSet(predSetA, currentColorA);
        configurePredictionDataSet(predSetB, currentColorB);
        configurePredictionDataSet(predSetC, currentColorC);
        
        // 禁用默认图例
        lineChart.getLegend().setEnabled(false);
        
        // 更新图表
        LineData lineData = new LineData(setA, setB, setC, predSetA, predSetB, predSetC);
        lineChart.setData(lineData);
    }
    
    // 配置数据集属性
    private void configureDataSet(LineDataSet dataSet, int defaultColor, List<Integer> colors)
    {
        dataSet.setColor(defaultColor);
        dataSet.setCircleColor(defaultColor);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setColors(colors);
        dataSet.setCircleColors(colors);
    }

    // 配置预测数据集属性（虚线）
    private void configurePredictionDataSet(LineDataSet dataSet, int color)
    {
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        
        // 设置为虚线
        dataSet.enableDashedLine(10f, 5f, 0f);
    }

    //显示预测结果及历史记录
    private void updatePowerInfo(int index, List<String> dates, List<UserData> historicalData)
    {
        try
        {
            if (index < dates.size() - 1)  // 历史数据
            {
                UserData data = historicalData.get(index);
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
        process.append("   - 可信度评估：").append(getReliabilityLevel(variationCoef)).append("\n");
        
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

    // 用于提取电量数据的函数式接口
    @FunctionalInterface
    interface PowerExtractor
    {
        double getPower(UserData data);
    }
}