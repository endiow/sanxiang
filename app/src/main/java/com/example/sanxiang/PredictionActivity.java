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
        Button btnSearch = findViewById(R.id.btnSearch);
        
        btnSearch.setOnClickListener(v -> 
        {
            String userId = etSearch.getText().toString();
            adapter.filter(userId);
        });

        loadPredictions();
    }

    private void loadPredictions()
    {
        // 首先检查总电量表中的数据量是否足够
        List<String> dates = dbHelper.getLastNDays(7);
        if (dates.size() < 7)
        {
            Toast.makeText(this, 
                "数据量不足，至少需要7天的数据才能进行预测。当前仅有 " + dates.size() + " 天数据。", 
                Toast.LENGTH_LONG).show();
            return;
        }

        List<String> userIds = dbHelper.getAllUserIds();
        List<PredictionResult> predictions = new ArrayList<>();
        
        //对每个用户进行预测
        for (String userId : userIds)
        {
            List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 20);
            //至少有7天数据，才进行预测
            if (historicalData.size() >= 7)
            {
                PredictionResult prediction = predictUserPower(historicalData);
                predictions.add(prediction);
            }
        }

        // 如果没有任何用户有足够的数据进行预测
        if (predictions.isEmpty())
        {
            Toast.makeText(this, "没有任何用户具有足够的历史数据进行预测", Toast.LENGTH_LONG).show();
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

    //预测电量函数
    private PredictionResult predictUserPower(List<UserData> historicalData)
    {
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
            if (pyResult.get("success").toBoolean()) 
            {
                PyObject predictions = pyResult.get("predictions");
                result.setPredictedPhaseAPower(predictions.get("phase_a").get("value").toDouble());
                result.setPredictedPhaseBPower(predictions.get("phase_b").get("value").toDouble());
                result.setPredictedPhaseCPower(predictions.get("phase_c").get("value").toDouble());
            } 
            else 
            {
                // 如果Python预测失败，使用简单平均作为备选
                double sumA = 0, sumB = 0, sumC = 0;
                int count = Math.min(7, historicalData.size());
                
                for (int i = 0; i < count; i++) 
                {
                    UserData data = historicalData.get(i);
                    sumA += data.getPhaseAPower();
                    sumB += data.getPhaseBPower();
                    sumC += data.getPhaseCPower();
                }
                
                result.setPredictedPhaseAPower(sumA / count);
                result.setPredictedPhaseBPower(sumB / count);
                result.setPredictedPhaseCPower(sumC / count);
            }
        } 
        catch (Exception e) 
        {
            // 发生异常时使用简单平均
            double sumA = 0, sumB = 0, sumC = 0;
            int count = Math.min(7, historicalData.size());
            
            for (int i = 0; i < count; i++) 
            {
                UserData data = historicalData.get(i);
                sumA += data.getPhaseAPower();
                sumB += data.getPhaseBPower();
                sumC += data.getPhaseCPower();
            }
            
            result.setPredictedPhaseAPower(sumA / count);
            result.setPredictedPhaseBPower(sumB / count);
            result.setPredictedPhaseCPower(sumC / count);
        }
        
        return result;
    }
} 