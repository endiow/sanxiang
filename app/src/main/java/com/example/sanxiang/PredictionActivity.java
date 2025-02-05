package com.example.sanxiang;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import org.json.JSONObject;

public class PredictionActivity extends AppCompatActivity
{
    private DatabaseHelper dbHelper;    
    private TextView tvTotalPrediction;
    private TextView tvWarning;  // 添加警告信息显示
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

        try
        {
            // 初始化数据库和视图
            dbHelper = new DatabaseHelper(this);
            
            // 初始化视图和适配器
            initViews();
            
            // 初始化显示
            tvTotalPrediction.setText("暂无数据");
            adapter.setPredictions(new ArrayList<>());

            // 加载预测数据
            loadPredictions();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "初始化界面时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            // 显示错误信息
            if (tvTotalPrediction != null)
            {
                tvTotalPrediction.setText("暂无数据");
            }
            if (adapter != null)
            {
                adapter.setPredictions(new ArrayList<>());
            }
        }
    }

    // 初始化视图组件
    private void initViews()
    {
        tvTotalPrediction = findViewById(R.id.tvTotalPrediction);
        tvWarning = findViewById(R.id.tvWarning);
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
    }

    private void loadPredictions()
    {
        try
        {
            // 重置总电量
            totalPhaseA = 0;
            totalPhaseB = 0;
            totalPhaseC = 0;

            // 获取所有用户ID
            List<String> userIds = dbHelper.getAllUserIds();
            List<PredictionResult> predictionResults = new ArrayList<>();
            List<String> insufficientDataUsers = new ArrayList<>();  // 存储数据不足的用户

            // 获取最后修改时间
            String lastModifiedTime = dbHelper.getLastModifiedTime();
            
            for (String userId : userIds)
            {
                // 获取用户的预测时间
                String predictionTime = dbHelper.getPredictionTime(userId);
                
                // 如果预测结果不存在或者数据有更新，需要重新预测
                if (predictionTime == null || lastModifiedTime == null || predictionTime.compareTo(lastModifiedTime) < 0)
                {
                    // 获取历史数据并预测
                    List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 30);
                    if (historicalData.size() < 3)  
                    {
                        // 如果数据量不足3天，创建一个带有提示的预测结果
                        PredictionResult result = new PredictionResult();
                        UserData latestData = historicalData.isEmpty() ? null : historicalData.get(0);
                        
                        if (latestData != null)
                        {
                            result.setUserId(latestData.getUserId());
                            result.setUserName(latestData.getUserName());
                            result.setRouteNumber(latestData.getRouteNumber());
                            result.setRouteName(latestData.getRouteName());
                            result.setPhase(latestData.getPhase());
                            result.setPredictedPhaseAPower(0.0);
                            result.setPredictedPhaseBPower(0.0);
                            result.setPredictedPhaseCPower(0.0);
                            
                            predictionResults.add(result);
                            insufficientDataUsers.add(latestData.getUserId() + "(" + latestData.getUserName() + ")");
                        }
                        continue;
                    }

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
                            JSONObject predictionsObj = jsonResult.getJSONObject("predictions");
                            
                            // 创建预测结果对象
                            PredictionResult result = new PredictionResult();
                            UserData latestData = historicalData.get(0);  // 使用最新的用户数据
                            
                            // 设置用户基本信息
                            result.setUserId(latestData.getUserId());
                            result.setUserName(latestData.getUserName());
                            result.setRouteNumber(latestData.getRouteNumber());
                            result.setRouteName(latestData.getRouteName());
                            result.setPhase(latestData.getPhase());
                            
                            // 设置预测值
                            double predictedPhaseA = predictionsObj.getJSONObject("phase_a").getDouble("value");
                            double predictedPhaseB = predictionsObj.getJSONObject("phase_b").getDouble("value");
                            double predictedPhaseC = predictionsObj.getJSONObject("phase_c").getDouble("value");
                            
                            result.setPredictedPhaseAPower(predictedPhaseA);
                            result.setPredictedPhaseBPower(predictedPhaseB);
                            result.setPredictedPhaseCPower(predictedPhaseC);
                            
                            // 保存预测结果到数据库
                            dbHelper.savePredictionResult(userId, latestData.getPhase(), predictedPhaseA, predictedPhaseB, predictedPhaseC);
                            
                            predictionResults.add(result);
                            
                            // 累加总电量
                            totalPhaseA += predictedPhaseA;
                            totalPhaseB += predictedPhaseB;
                            totalPhaseC += predictedPhaseC;
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
                else
                {
                    // 从数据库获取预测结果
                    Map<String, Object> prediction = dbHelper.getUserPrediction(userId);
                    if (!prediction.isEmpty())
                    {
                        // 获取用户基本信息
                        String userInfo = dbHelper.getUserInfo(userId);
                        if (userInfo != null)
                        {
                            PredictionResult result = new PredictionResult();
                            result.setUserId(userId);
                            
                            // 从用户信息字符串中提取信息
                            String[] lines = userInfo.split("\n");
                            for (String line : lines)
                            {
                                if (line.startsWith("用户名称："))
                                {
                                    result.setUserName(line.substring(5));
                                }
                                else if (line.startsWith("回路编号："))
                                {
                                    result.setRouteNumber(line.substring(5));
                                }
                                else if (line.startsWith("线路名称："))
                                {
                                    result.setRouteName(line.substring(5));
                                }
                            }
                            
                            // 设置相位和预测值
                            result.setPhase((String)prediction.get("phase"));
                            result.setPredictedPhaseAPower((Double)prediction.get("phase_a"));
                            result.setPredictedPhaseBPower((Double)prediction.get("phase_b"));
                            result.setPredictedPhaseCPower((Double)prediction.get("phase_c"));
                            
                            predictionResults.add(result);
                            
                            // 累加总电量
                            totalPhaseA += (Double)prediction.get("phase_a");
                            totalPhaseB += (Double)prediction.get("phase_b");
                            totalPhaseC += (Double)prediction.get("phase_c");
                        }
                    }
                }
            }

            // 计算三相不平衡度
            double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(totalPhaseA, totalPhaseB, totalPhaseC);
            String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);

            // 更新UI
            String totalInfo = String.format(
                "预测总电量：\n" +
                "A相：%.2f\n" +
                "B相：%.2f\n" +
                "C相：%.2f\n" +
                "三相不平衡度：%.2f%% (%s)",
                totalPhaseA, totalPhaseB, totalPhaseC, unbalanceRate, status
            );

            // 设置不平衡度可点击
            SpannableString spannableString = new SpannableString(totalInfo);
            int start = totalInfo.indexOf("三相不平衡度");
            if (start >= 0)
            {
                int end = start + 6;
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
                        ds.setColor(Color.rgb(51, 102, 153));
                        ds.setUnderlineText(false);
                        ds.setFakeBoldText(true);
                    }
                };
                
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvTotalPrediction.setText(spannableString);
                tvTotalPrediction.setMovementMethod(LinkMovementMethod.getInstance());
            }
            else
            {
                tvTotalPrediction.setText(totalInfo);
            }

            // 显示警告信息
            if (!insufficientDataUsers.isEmpty())
            {
                String warningText = String.format(
                    "以下用户的历史数据少于3天，无法进行预测（预测值显示为0）：\n%s", 
                    String.join("、", insufficientDataUsers)
                );
                tvWarning.setText(warningText);
                tvWarning.setVisibility(View.VISIBLE);
            }
            else
            {
                tvWarning.setVisibility(View.GONE);
            }

            // 更新列表
            adapter.setPredictions(predictionResults);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "加载预测数据时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
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