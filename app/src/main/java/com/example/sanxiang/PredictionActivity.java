package com.example.sanxiang;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.data.PredictionResult;
import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import android.content.Intent;

public class PredictionActivity extends AppCompatActivity
{
    private DatabaseHelper dbHelper;
    private TextView tvTotalPrediction;
    private RecyclerView recyclerView;
    private PredictionAdapter adapter;
    
    // 添加成员变量存储三相电量
    private double totalPhaseA;
    private double totalPhaseB;
    private double totalPhaseC;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        // 初始化Python环境
        if (!Python.isStarted())
        {
            Python.start(new AndroidPlatform(this));
        }

        dbHelper = new DatabaseHelper(this);
        tvTotalPrediction = findViewById(R.id.tvTotalPrediction);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PredictionAdapter();
        recyclerView.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.etSearch);
        
        // 设置文本变化监听器，实现实时过滤
        etSearch.addTextChangedListener(new android.text.TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(android.text.Editable s)
            {
                String userId = s.toString();
                if (userId.isEmpty())
                {
                    adapter.filter(null);
                }
                else
                {
                    adapter.filter(userId);
                }
            }
        });

        loadPredictions();
    }

    private void predictUserPower(String userId)
    {
        // 获取最近30天的历史数据用于预测
        List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 30);
        
        if (historicalData.isEmpty())
        {
            Toast.makeText(this, "没有找到该用户的历史数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用Holt-Winters进行预测
        PredictionResult result = predictUserPowerWithHoltWinters(historicalData);
        
        // 显示预测结果
        if (result != null)
        {
            Intent intent = new Intent(this, PredictionDetailActivity.class);
            intent.putExtra("userId", result.getUserId());
            intent.putExtra("userName", result.getUserName());
            intent.putExtra("routeNumber", result.getRouteNumber());
            intent.putExtra("routeName", result.getRouteName());
            intent.putExtra("phase", result.getPhase());
            intent.putExtra("predictedPhaseAPower", result.getPredictedPhaseAPower());
            intent.putExtra("predictedPhaseBPower", result.getPredictedPhaseBPower());
            intent.putExtra("predictedPhaseCPower", result.getPredictedPhaseCPower());
            startActivity(intent);
        }
    }

    private void loadPredictions()
    {
        try
        {
            List<String> userIds = dbHelper.getAllUserIds();
            if (userIds.isEmpty())
            {
                Toast.makeText(this, "没有找到任何用户数据", Toast.LENGTH_LONG).show();
                return;
            }

            List<PredictionResult> predictions = new ArrayList<>();
            
            //对每个用户进行预测
            for (String userId : userIds)
            {
                try
                {
                    // 获取最近30天的历史数据用于预测
                    List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 30);
                    if (historicalData != null && !historicalData.isEmpty())
                    {
                        PredictionResult prediction = predictUserPowerWithHoltWinters(historicalData);
                        if (prediction != null)
                        {
                            predictions.add(prediction);
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    // 单个用户预测失败，继续处理下一个用户
                    continue;
                }
            }

            if (predictions.isEmpty())
            {
                Toast.makeText(this, "没有可用的预测结果", Toast.LENGTH_LONG).show();
                return;
            }

            // 计算总预测电量和不平衡度
            totalPhaseA = 0;
            totalPhaseB = 0;
            totalPhaseC = 0;
            for (PredictionResult prediction : predictions)
            {
                totalPhaseA += prediction.getPredictedPhaseAPower();
                totalPhaseB += prediction.getPredictedPhaseBPower();
                totalPhaseC += prediction.getPredictedPhaseCPower();
            }

            double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(totalPhaseA, totalPhaseB, totalPhaseC);
            String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);

            String totalInfo = String.format(
                "明日总电量预测：\n" +
                "A相总量：%.2f\nB相总量：%.2f\nC相总量：%.2f\n" +
                "三相不平衡度：%.2f%% (%s)",
                totalPhaseA, totalPhaseB, totalPhaseC, unbalanceRate, status
            );
            
            SpannableString spannableString = new SpannableString(totalInfo);
            int start = totalInfo.indexOf("三相不平衡度");
            int end = start + 6;

            // 设置文字样式和点击事件
            ClickableSpan clickableSpan = new ClickableSpan() 
            {
                @Override
                public void onClick(@NonNull View view) 
                {
                    UnbalanceCalculator.showCalculationProcess(
                        PredictionActivity.this,
                        totalPhaseA, totalPhaseB, totalPhaseC
                    );
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) 
                {
                    ds.setColor(Color.rgb(51, 102, 153));  // 蓝色
                    ds.setUnderlineText(false);
                    ds.setFakeBoldText(true);  // 粗体
                }
            };

            spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            tvTotalPrediction.setText(spannableString);
            tvTotalPrediction.setMovementMethod(LinkMovementMethod.getInstance());

            // 显示各用户预测结果
            adapter.setPredictions(predictions);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "预测过程中发生错误：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //使用Holt-Winters方法进行预测
    private PredictionResult predictUserPowerWithHoltWinters(List<UserData> historicalData)
    {
        if (historicalData == null || historicalData.isEmpty())
        {
            return null;
        }

        PredictionResult result = new PredictionResult();
        UserData latestData = historicalData.get(0);  // 使用最新的用户数据
        
        // 设置用户基本信息
        result.setUserId(latestData.getUserId());
        result.setUserName(latestData.getUserName());
        result.setRouteNumber(latestData.getRouteNumber());
        result.setRouteName(latestData.getRouteName());
        result.setPhase(latestData.getPhase());

        try 
        {
            // 准备数据 - 按日期升序排序
            double[] phaseAData = new double[historicalData.size()];
            double[] phaseBData = new double[historicalData.size()];
            double[] phaseCData = new double[historicalData.size()];

            // 反转数据顺序，使其按时间升序排列
            for (int i = 0; i < historicalData.size(); i++) 
            {
                UserData data = historicalData.get(historicalData.size() - 1 - i);
                phaseAData[i] = Math.max(0, data.getPhaseAPower());
                phaseBData[i] = Math.max(0, data.getPhaseBPower());
                phaseCData[i] = Math.max(0, data.getPhaseCPower());
            }

            // 检查数据是否足够
            if (historicalData.size() < 14)
            {
                // 如果数据不足，使用最近一天的数据作为预测值
                result.setPredictedPhaseAPower(latestData.getPhaseAPower());
                result.setPredictedPhaseBPower(latestData.getPhaseBPower());
                result.setPredictedPhaseCPower(latestData.getPhaseCPower());
                return result;
            }

            // 使用Holt-Winters方法进行预测
            double predictedA = predictNextValue(phaseAData);
            double predictedB = predictNextValue(phaseBData);
            double predictedC = predictNextValue(phaseCData);

            // 确保预测值非负
            result.setPredictedPhaseAPower(Math.max(0, predictedA));
            result.setPredictedPhaseBPower(Math.max(0, predictedB));
            result.setPredictedPhaseCPower(Math.max(0, predictedC));
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            // 如果预测失败，使用最近一天的数据
            result.setPredictedPhaseAPower(latestData.getPhaseAPower());
            result.setPredictedPhaseBPower(latestData.getPhaseBPower());
            result.setPredictedPhaseCPower(latestData.getPhaseCPower());
        }
        
        return result;
    }

    // Holt-Winters预测方法
    private double predictNextValue(double[] data) 
    {
        if (data == null || data.length < 14) 
        {
            throw new IllegalArgumentException("数据量不足");
        }

        // 检查数据是否全为0
        boolean allZero = true;
        for (double value : data)
        {
            if (value != 0)
            {
                allZero = false;
                break;
            }
        }
        if (allZero)
        {
            return 0.0;
        }

        // 数据预处理
        double[] processedData = preprocessData(data);
        
        // 自适应参数选择
        double[] params = optimizeParameters(processedData);
        double alpha = params[0]; // 水平平滑因子
        double beta = params[1];  // 趋势平滑因子
        double gamma = params[2]; // 季节性平滑因子
        
        int season = 7;     // 季节性周期（7天）

        // 初始化季节性因子
        double[] seasonalFactors = initializeSeasonalFactors(processedData, season);

        // 初始化水平和趋势
        double level = calculateInitialLevel(processedData);
        double trend = calculateInitialTrend(processedData);

        // 应用改进的Holt-Winters算法
        for (int i = 0; i < processedData.length; i++) 
        {
            int season_idx = i % season;
            double value = processedData[i];
            double oldLevel = level;
            
            // 更新水平（加入自适应权重）
            level = alpha * (value / seasonalFactors[season_idx]) + 
                   (1 - alpha) * (oldLevel + trend);
            
            // 更新趋势（加入阻尼因子）
            double phi = 0.9; // 阻尼因子
            trend = phi * (beta * (level - oldLevel) + (1 - beta) * trend);
            
            // 更新季节性因子（加入归一化）
            seasonalFactors[season_idx] = normalizeSeasonalFactor(
                gamma * (value / level) + (1 - gamma) * seasonalFactors[season_idx]
            );
        }

        // 预测下一个值（加入趋势调整）
        int nextSeasonIdx = processedData.length % season;
        double prediction = adjustPrediction(level + trend) * seasonalFactors[nextSeasonIdx];
        
        // 确保预测值非负
        return Math.max(0, prediction);
    }

    // 数据预处理
    private double[] preprocessData(double[] data) 
    {
        double[] processed = data.clone();
        
        // 移除异常值
        double mean = calculateMean(processed);
        double std = calculateStd(processed, mean);
        double threshold = 2.0; // 标准差阈值
        
        for (int i = 0; i < processed.length; i++) 
        {
            if (Math.abs(processed[i] - mean) > threshold * std) 
            {
                // 使用相邻值的平均值替换异常值
                processed[i] = i > 0 && i < processed.length - 1 ? 
                    (processed[i-1] + processed[i+1]) / 2 : mean;
            }
        }
        
        // 数据归一化
        return normalizeData(processed);
    }

    // 参数优化
    private double[] optimizeParameters(double[] data) 
    {
        double bestAlpha = 0.1;
        double bestBeta = 0.1;
        double bestGamma = 0.1;
        double minError = Double.MAX_VALUE;
        
        // 网格搜索最优参数
        for (double a = 0.1; a <= 0.9; a += 0.2) 
        {
            for (double b = 0.1; b <= 0.9; b += 0.2) 
            {
                for (double g = 0.1; g <= 0.9; g += 0.2) 
                {
                    double error = calculateError(data, a, b, g);
                    if (error < minError) 
                    {
                        minError = error;
                        bestAlpha = a;
                        bestBeta = b;
                        bestGamma = g;
                    }
                }
            }
        }
        
        return new double[]{bestAlpha, bestBeta, bestGamma};
    }

    // 计算预测误差
    private double calculateError(double[] data, double alpha, double beta, double gamma) 
    {
        double error = 0;
        int season = 7;
        double level = data[0];
        double trend = (data[1] - data[0]);
        double[] seasonalFactors = new double[season];
        
        // 初始化季节性因子
        for (int i = 0; i < season; i++) 
        {
            seasonalFactors[i] = 1.0;
        }
        
        // 计算一步预测误差
        for (int i = season; i < data.length; i++) 
        {
            double prediction = (level + trend) * seasonalFactors[i % season];
            error += Math.pow(data[i] - prediction, 2);
            
            // 更新参数
            double oldLevel = level;
            level = alpha * (data[i] / seasonalFactors[i % season]) + (1 - alpha) * (oldLevel + trend);
            trend = beta * (level - oldLevel) + (1 - beta) * trend;
            seasonalFactors[i % season] = gamma * (data[i] / level) + (1 - gamma) * seasonalFactors[i % season];
        }
        
        return Math.sqrt(error / (data.length - season)); // RMSE
    }

    // 初始化季节性因子
    private double[] initializeSeasonalFactors(double[] data, int season) 
    {
        double[] seasonalFactors = new double[season];
        double[] seasonalAverages = new double[season];
        int[] seasonalCounts = new int[season];
        
        // 计算每个季节位置的平均值
        for (int i = 0; i < data.length; i++) 
        {
            int idx = i % season;
            seasonalAverages[idx] += data[i];
            seasonalCounts[idx]++;
        }
        
        // 计算季节性因子
        double totalAverage = calculateMean(data);
        for (int i = 0; i < season; i++) 
        {
            if (seasonalCounts[i] > 0) 
            {
                seasonalAverages[i] /= seasonalCounts[i];
                seasonalFactors[i] = seasonalAverages[i] / totalAverage;
            } 
            else 
            {
                seasonalFactors[i] = 1.0;
            }
        }
        
        return seasonalFactors;
    }

    // 计算初始水平
    private double calculateInitialLevel(double[] data) 
    {
        // 使用前7天的加权平均
        double sum = 0;
        double weightSum = 0;
        for (int i = 0; i < Math.min(7, data.length); i++) 
        {
            double weight = Math.exp(-0.1 * i); // 指数衰减权重
            sum += data[i] * weight;
            weightSum += weight;
        }
        return sum / weightSum;
    }

    // 计算初始趋势
    private double calculateInitialTrend(double[] data) 
    {
        if (data.length < 2) return 0;
        
        // 使用加权平均斜率
        double sumSlope = 0;
        double weightSum = 0;
        for (int i = 1; i < Math.min(7, data.length); i++) 
        {
            double weight = Math.exp(-0.1 * (i-1));
            sumSlope += (data[i] - data[i-1]) * weight;
            weightSum += weight;
        }
        return sumSlope / weightSum;
    }

    // 归一化季节性因子
    private double normalizeSeasonalFactor(double factor) 
    {
        // 限制季节性因子的范围
        return Math.max(0.5, Math.min(1.5, factor));
    }

    // 调整预测值
    private double adjustPrediction(double prediction) 
    {
        // 确保预测值非负
        return Math.max(0, prediction);
    }

    // 计算均值
    private double calculateMean(double[] data) 
    {
        double sum = 0;
        for (double value : data) 
        {
            sum += value;
        }
        return sum / data.length;
    }

    // 计算标准差
    private double calculateStd(double[] data, double mean) 
    {
        double sumSquaredDiff = 0;
        for (double value : data) 
        {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquaredDiff / data.length);
    }

    // 数据归一化
    private double[] normalizeData(double[] data) 
    {
        double mean = calculateMean(data);
        double std = calculateStd(data, mean);
        double[] normalized = new double[data.length];
        
        if (std == 0) return data.clone();
        
        for (int i = 0; i < data.length; i++) 
        {
            normalized[i] = (data[i] - mean) / std;
        }
        return normalized;
    }

    
} 