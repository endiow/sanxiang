package com.example.sanxiang.prediction;

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
import android.app.ProgressDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.R;
import com.example.sanxiang.prediction.adapter.PredictionAdapter;
import com.example.sanxiang.prediction.model.PredictionResult;
import com.example.sanxiang.userdata.model.UserData;
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
        etSearch.setHint("输入用户编号搜索");
        etSearch.setFilters(new android.text.InputFilter[] 
        {
            new android.text.InputFilter.LengthFilter(10)
        });
        
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
                String searchId = s.toString().trim();
                adapter.filter(searchId);
            }
        });
    }

    private void loadPredictions()
    {
        // 创建进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("正在预测");
        progressDialog.setMessage("正在加载数据...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 使用异步任务处理预测
        new Thread(() -> 
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
                
                // 设置进度条最大值
                runOnUiThread(() -> 
                {
                    progressDialog.setMax(userIds.size());
                    progressDialog.setProgress(0);
                });

                // 用于跟踪进度
                int progress = 0;
                
                for (String userId : userIds)
                {
                    // 更新进度消息
                    final String currentUserId = userId;
                    runOnUiThread(() -> 
                    {
                        progressDialog.setMessage("正在预测用户 " + currentUserId + " 的用电量...");
                    });

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
                                result.setRouteName(latestData.getBranchNumber());
                                result.setPhase(latestData.getPhase());
                                result.setPredictedPhaseAPower(0.0);
                                result.setPredictedPhaseBPower(0.0);
                                result.setPredictedPhaseCPower(0.0);
                                
                                predictionResults.add(result);
                                insufficientDataUsers.add(latestData.getUserId() + "(" + latestData.getUserName() + ")");
                            }
                        }
                        else
                        {
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
                                    result.setRouteName(latestData.getBranchNumber());
                                    result.setPhase(latestData.getPhase());
                                    
                                    // 设置预测值
                                    double predictedPhaseA = predictionsObj.getJSONObject("phase_a").getDouble("value");
                                    double predictedPhaseB = predictionsObj.getJSONObject("phase_b").getDouble("value");
                                    double predictedPhaseC = predictionsObj.getJSONObject("phase_c").getDouble("value");
                                    
                                    // 根据相位设置预测值
                                    switch (latestData.getPhase().toUpperCase())
                                    {
                                        case "A":
                                            predictedPhaseB = 0.0;
                                            predictedPhaseC = 0.0;
                                            break;
                                        case "B":
                                            predictedPhaseA = 0.0;
                                            predictedPhaseC = 0.0;
                                            break;
                                        case "C":
                                            predictedPhaseA = 0.0;
                                            predictedPhaseB = 0.0;
                                            break;
                                        // 如果是三相用户（ABC）或其他情况，保持原值
                                    }
                                    
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
                                String phase = (String)prediction.get("phase");
                                result.setPhase(phase);
                                
                                // 获取预测值
                                double predPhaseA = (Double)prediction.get("phase_a");
                                double predPhaseB = (Double)prediction.get("phase_b");
                                double predPhaseC = (Double)prediction.get("phase_c");
                                
                                // 根据相位设置预测值
                                switch (phase.toUpperCase())
                                {
                                    case "A":
                                        predPhaseB = 0.0;
                                        predPhaseC = 0.0;
                                        break;
                                    case "B":
                                        predPhaseA = 0.0;
                                        predPhaseC = 0.0;
                                        break;
                                    case "C":
                                        predPhaseA = 0.0;
                                        predPhaseB = 0.0;
                                        break;
                                    // 如果是三相用户（ABC）或其他情况，保持原值
                                }
                                
                                result.setPredictedPhaseAPower(predPhaseA);
                                result.setPredictedPhaseBPower(predPhaseB);
                                result.setPredictedPhaseCPower(predPhaseC);
                                
                                predictionResults.add(result);
                                
                                // 累加总电量
                                totalPhaseA += predPhaseA;
                                totalPhaseB += predPhaseB;
                                totalPhaseC += predPhaseC;
                            }
                        }
                    }

                    // 更新进度
                    progress++;
                    final int currentProgress = progress;
                    runOnUiThread(() -> 
                    {
                        progressDialog.setProgress(currentProgress);
                    });
                }

                // 计算三相不平衡度
                double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(totalPhaseA, totalPhaseB, totalPhaseC);
                String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);

                // 更新UI
                runOnUiThread(() -> 
                {
                    // 关闭进度对话框
                    progressDialog.dismiss();

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
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
                runOnUiThread(() -> 
                {
                    // 关闭进度对话框
                    progressDialog.dismiss();
                    Toast.makeText(PredictionActivity.this, "加载预测数据时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
} 