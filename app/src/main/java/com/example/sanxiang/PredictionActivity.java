package com.example.sanxiang;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.data.PredictionResult;
import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;

import java.util.ArrayList;
import java.util.List;

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
        List<String> userIds = dbHelper.getAllUserIds();
        List<PredictionResult> predictions = new ArrayList<>();
        
        for (String userId : userIds)
        {
            List<UserData> historicalData = dbHelper.getLastTwentyDaysData(userId);
            if (historicalData.size() >= 3)
            {
                PredictionResult prediction = predictUserPower(historicalData);
                predictions.add(prediction);
            }
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
        int end = start + 5;

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
        
        // 计算预测电量
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
        
        return result;
    }
} 