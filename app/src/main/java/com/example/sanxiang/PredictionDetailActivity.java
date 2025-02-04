package com.example.sanxiang;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

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
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredictionDetailActivity extends AppCompatActivity 
{
    private DatabaseHelper dbHelper;
    private LineChart lineChart;
    private TextView tvUserInfo;
    private TextView tvPredictionProcess;
    private String userId;
    private String userName;

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

        // 获取传递的用户信息
        userId = getIntent().getStringExtra("userId");
        userName = getIntent().getStringExtra("userName");

        // 设置用户信息
        tvUserInfo.setText(String.format("用户编号: %s\n用户名称: %s", userId, userName));

        // 设置图表
        setupChart();
        
        // 加载数据并预测
        loadDataAndPredict();
    }

    private void setupChart() 
    {
        // 配置图表
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
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

    private void loadDataAndPredict() 
    {
        // 获取历史数据
        List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId,7);
        if (historicalData.isEmpty()) 
        {
            tvPredictionProcess.setText("没有找到历史数据");
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

        // 更新图表
        LineData lineData = new LineData(setA, setB, setC);
        lineChart.setData(lineData);
        lineChart.invalidate();

        // 进行预测并显示过程
        try 
        {
            // 准备数据
            List<Map<String, Object>> pyData = new ArrayList<>();
            for (int i = historicalData.size() - 1; i >= 0; i--) 
            {
                UserData data = historicalData.get(i);
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("date", data.getDate());
                dayData.put("phase_a", data.getPhaseAPower());
                dayData.put("phase_b", data.getPhaseBPower());
                dayData.put("phase_c", data.getPhaseCPower());
                pyData.add(0, dayData);  // 添加到列表开头，确保按日期升序排序
            }
            
            // 调用Python预测
            Python py = Python.getInstance();
            PyObject predictorModule = py.getModule("predictor.power_predictor");
            PyObject predictorClass = predictorModule.get("PowerPredictor");
            PyObject predictor = predictorClass.call();
            PyObject pyResult = predictor.callAttr("predict", pyData);
            
            // 解析预测结果
            JSONObject jsonResult = new JSONObject(pyResult.toString());
            if (jsonResult.getBoolean("success")) 
            {
                JSONObject predictions = jsonResult.getJSONObject("predictions");
                JSONObject modelInfo = jsonResult.getJSONObject("model_info");
                
                StringBuilder sb = new StringBuilder();
                sb.append("=== 预测过程 ===\n\n");
                
                // 显示数据信息
                sb.append("【数据信息】\n");
                sb.append(String.format("使用数据点数: %d\n", modelInfo.getInt("data_points")));
                sb.append(String.format("最后数据日期: %s\n\n", modelInfo.getString("last_date")));
                
                // 显示预测结果
                sb.append("【预测结果】\n");
                appendPhaseResult(sb, "A相", predictions.getJSONObject("phase_a"));
                appendPhaseResult(sb, "B相", predictions.getJSONObject("phase_b"));
                appendPhaseResult(sb, "C相", predictions.getJSONObject("phase_c"));
                
                sb.append("\n【置信水平】\n");
                sb.append(String.format("%.0f%%\n", modelInfo.getDouble("confidence_level") * 100));
                
                tvPredictionProcess.setText(sb.toString());
            } 
            else 
            {
                tvPredictionProcess.setText("预测失败: " + jsonResult.getString("error"));
            }
        } 
        catch (Exception e) 
        {
            tvPredictionProcess.setText("预测过程出错: " + e.getMessage());
        }
    }
    
    private void appendPhaseResult(StringBuilder sb, String phase, JSONObject result) throws Exception 
    {
        double value = result.getDouble("value");
        JSONObject interval = result.getJSONObject("interval");
        double lower = interval.getDouble("lower");
        double upper = interval.getDouble("upper");
        
        sb.append(String.format("%s预测值: %.2f\n", phase, value));
        sb.append(String.format("预测区间: [%.2f, %.2f]\n\n", lower, upper));
    }
}