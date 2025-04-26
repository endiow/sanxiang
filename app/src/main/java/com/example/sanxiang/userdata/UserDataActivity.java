package com.example.sanxiang.userdata;

import android.content.Intent;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.R;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.userdata.adapter.UserDataAdapter;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;
import com.example.sanxiang.util.DateValidator;
import com.example.sanxiang.util.PowerLossCalculator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

// 用户数据显示界面
public class UserDataActivity extends AppCompatActivity
{
    private DatabaseHelper dbHelper;      // 数据库操作类
    private EditText etDate;             // 日期输入框
    private TextView tvTotalPower;       // 总电量显示
    private RecyclerView recyclerView;   // 用户列表
    private UserDataAdapter adapter;     // 列表适配器
    private String currentDate;          // 当前显示的日期
    private Map<String, UserData> userDataCache = new HashMap<>();
    private Map<String, Double> beforePhaseCache = null;
    private boolean isCalculating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_data);

        try
        {
            // 初始化数据库和视图
            dbHelper = new DatabaseHelper(this);
            
            // 先初始化视图和适配器
            initViews();
            
            // 再设置监听器
            setupListeners();

            // 初始化显示
            tvTotalPower.setText("暂无数据");
            adapter.setData(new ArrayList<>());

            // 加载最新日期的数据
            try
            {
                currentDate = dbHelper.getLatestDate();
                if (currentDate != null)
                {
                    // 设置日期
                    etDate.setText(currentDate);
                    // 直接加载数据
                    loadData(currentDate);
                }
                else
                {
                    Toast.makeText(this, "暂无数据", Toast.LENGTH_SHORT).show();
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                Toast.makeText(this, "加载最新日期数据时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "初始化界面时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            // 显示错误信息
            if (tvTotalPower != null)
            {
                tvTotalPower.setText("暂无数据");
            }
            if (adapter != null)
            {
                adapter.setData(new ArrayList<>());
            }
        }
    }

    // 初始化视图组件
    private void initViews()
    {
        // 初始化视图
        etDate = findViewById(R.id.etDate);
        tvTotalPower = findViewById(R.id.tvTotalPower);
        recyclerView = findViewById(R.id.recyclerView);
        
        // 设置布局管理器
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 初始化适配器
        adapter = new UserDataAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    // 设置监听器
    private void setupListeners()
    {
        Button btnPrevDay = findViewById(R.id.btnPrevDay);
        Button btnNextDay = findViewById(R.id.btnNextDay);
        EditText etSearch = findViewById(R.id.etSearch);
    
        // 设置日期导航按钮的点击事件
        btnPrevDay.setOnClickListener(v -> loadPrevDay());
        btnNextDay.setOnClickListener(v -> loadNextDay());

        // 设置日期输入框的回车键监听
        etDate.setOnEditorActionListener((v, actionId, event) -> 
        {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            {
                String date = etDate.getText().toString().trim();
                if (DateValidator.isValidDateFormat(date, etDate))
                {
                    loadData(date);
                }
                else
                {
                    Toast.makeText(this, "请输入正确的日期格式，例如：2024-03-21、2024/03/21、24.3.21", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

        // 设置搜索框的最大长度限制
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
                adapter.filter(s.toString());
            }
        });
        
        // 设置浮动按钮的点击事件
        FloatingActionButton fabUserInfo = findViewById(R.id.fabUserInfo);
        FloatingActionButton fabTotalPower = findViewById(R.id.fabTotalPower);

        // 用户信息表
        fabUserInfo.setOnClickListener(v -> 
        {
            Intent intent = new Intent(this, TableViewActivity.class);
            intent.putExtra(TableViewActivity.EXTRA_TABLE_TYPE, TableViewActivity.TYPE_USER_INFO);
            startActivity(intent);
        }); 

        // 总电量表
        fabTotalPower.setOnClickListener(v -> 
        {
            Intent intent = new Intent(this, TableViewActivity.class);
            intent.putExtra(TableViewActivity.EXTRA_TABLE_TYPE, TableViewActivity.TYPE_TOTAL_POWER);
            startActivity(intent);
        });
    }

    // 加载指定日期的数据
    private void loadData(String date)
    {
        if (date == null || date.trim().isEmpty())
        {
            Toast.makeText(this, "日期不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        try
        {
            currentDate = date;
            etDate.setText(date);
            
            // 清空缓存
            userDataCache.clear();
            beforePhaseCache = null;
            
            // 设置适配器的当前日期
            adapter.setCurrentDate(date);
            
            // 获取并显示总电量和用户数据
            double[] totalPower = dbHelper.getTotalPowerByDate(date);
            List<UserData> userDataList = dbHelper.getUserDataByDate(date);
            
            // 缓存用户数据，加速后续查询
            if (userDataList != null)
            {
                for (UserData userData : userDataList)
                {
                    userDataCache.put(userData.getUserId(), userData);
                }
            }
            
            if (totalPower != null && totalPower.length >= 4)
            {
                displayTotalPower(totalPower);
            }
            else
            {
                tvTotalPower.setText("暂无数据");
            }
            
            if (userDataList != null && !userDataList.isEmpty())
            {
                adapter.setData(userDataList);
                
                // 保持搜索状态
                EditText etSearch = findViewById(R.id.etSearch);
                if (etSearch != null)
                {
                    String searchText = etSearch.getText().toString();
                    if (!searchText.isEmpty())
                    {
                        adapter.filter(searchText);
                    }
                }
            }
            else
            {
                adapter.setData(new ArrayList<>());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            tvTotalPower.setText("暂无数据");
            adapter.setData(new ArrayList<>());
            Toast.makeText(this, "加载数据时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 显示总电量和不平衡度信息
    private void displayTotalPower(double[] totalPower)
    {
        try
        {
            if (totalPower == null || totalPower.length < 4 || tvTotalPower == null)
            {
                if (tvTotalPower != null)
                {
                    tvTotalPower.setText("暂无数据");
                }
                return;
            }

            double unbalanceRate = totalPower[3];
            String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);
            
            // 计算损耗信息
            double phaseA = totalPower[0];
            double phaseB = totalPower[1];
            double phaseC = totalPower[2];
            
            // 计算线路损耗系数
            double lossCoefficient = PowerLossCalculator.calculateLineLossCoefficient(phaseA, phaseB, phaseC);
            
            // 检查是否有相位调整
            final boolean hasAdjustment = dbHelper.hasPhaseAdjustmentOnDate(currentDate);
            String powerInfo;
            
            if (hasAdjustment && !isCalculating) 
            {
                // 显示基本信息和加载提示
                final String initialInfo = String.format(
                    "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)\n" +
                    "线路损耗：%.5f × R kWh\n" +
                    "线路损耗优化比：0.00%%",
                    phaseA, phaseB, phaseC, unbalanceRate, status,
                    lossCoefficient
                );
                tvTotalPower.setText(initialInfo);
                
                // 标记正在计算
                isCalculating = true;
                
                // 异步计算
                new Thread(() -> {
                    try {
                        // 如果没有缓存过，进行计算
                        if (beforePhaseCache == null) {
                            // 计算调整前的三相电量 - 从当前电量开始，逆向推算
                            double beforePhaseA = phaseA;
                            double beforePhaseB = phaseB;
                            double beforePhaseC = phaseC;
                            
                            // 从old_data表获取调整前的三相总量数据
                            List<String> adjustedUsers = dbHelper.getUsersWithPhaseAdjustmentOnDate(currentDate);
                            
                            // 根据调整前后的相位变化反向计算原始电量
                            for (String userId : adjustedUsers) 
                            {
                                Map<String, Object> adjustment = dbHelper.getUserPhaseAdjustment(userId, currentDate);
                                if (adjustment != null) 
                                {
                                    String oldPhase = (String) adjustment.get("oldPhase");
                                    String newPhase = (String) adjustment.get("newPhase");
                                    
                                    // 获取用户当前的电量
                                    UserData userData = getUserDataById(userId);
                                    if (userData != null)
                                    {
                                        double userPower = 0;
                                        
                                        // 计算该用户的总用电量
                                        userPower = userData.getPhaseAPower() + userData.getPhaseBPower() + userData.getPhaseCPower();
                                        
                                        // 从现在的相位减去电量
                                        switch (newPhase)
                                        {
                                            case "A":
                                                beforePhaseA -= userPower;
                                                break;
                                            case "B":
                                                beforePhaseB -= userPower;
                                                break;
                                            case "C":
                                                beforePhaseC -= userPower;
                                                break;
                                        }
                                        
                                        // 给原来的相位加上电量
                                        switch (oldPhase)
                                        {
                                            case "A":
                                                beforePhaseA += userPower;
                                                break;
                                            case "B":
                                                beforePhaseB += userPower;
                                                break;
                                            case "C":
                                                beforePhaseC += userPower;
                                                break;
                                        }
                                    }
                                }
                            }
                            
                            // 保存到缓存
                            beforePhaseCache = new HashMap<>();
                            beforePhaseCache.put("A", beforePhaseA);
                            beforePhaseCache.put("B", beforePhaseB);
                            beforePhaseCache.put("C", beforePhaseC);
                        }
                        
                        // 从缓存读取
                        final double beforePhaseA = beforePhaseCache.get("A");
                        final double beforePhaseB = beforePhaseCache.get("B");
                        final double beforePhaseC = beforePhaseCache.get("C");
                        
                        // 计算调整前的假设线路损耗
                        final double beforeLossCoefficient = PowerLossCalculator.calculateLineLossCoefficient(
                            beforePhaseA, beforePhaseB, beforePhaseC);
                        
                        // 计算优化比例
                        double optimizationRatio = 0;
                        if (beforeLossCoefficient > 0) {
                            optimizationRatio = (beforeLossCoefficient - lossCoefficient) / beforeLossCoefficient * 100;
                        }
                        
                        // 格式化最终显示文本
                        final double finalOptimizationRatio = optimizationRatio;
                        runOnUiThread(() -> {
                            // 更新UI显示
                            String updatedInfo = String.format(
                                "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)\n" +
                                "线路损耗：%.5f × R kWh\n" +
                                "线路损耗优化比：%.2f%%",
                                phaseA, phaseB, phaseC, unbalanceRate, status,
                                lossCoefficient,
                                finalOptimizationRatio
                            );
                            
                            // 设置可点击的文本
                            updateClickableSpans(updatedInfo, phaseA, phaseB, phaseC, beforePhaseA, beforePhaseB, beforePhaseC);
                            
                            // 标记计算完成
                            isCalculating = false;
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            Toast.makeText(UserDataActivity.this, "计算优化比例时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            isCalculating = false;
                        });
                    }
                }).start();
                
                return;
            } 
            else if (!hasAdjustment)
            {
                // 格式化显示文本 - 无相位调整
                powerInfo = String.format(
                    "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)\n" +
                    "线路损耗：%.5f × R kWh",
                    phaseA, phaseB, phaseC, unbalanceRate, status,
                    lossCoefficient
                );
                
                // 设置不平衡度可点击
                updateClickableSpans(powerInfo, phaseA, phaseB, phaseC, 0, 0, 0);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            if (tvTotalPower != null)
            {
                tvTotalPower.setText("暂无数据");
            }
        }
    }
    
    // 更新可点击文本
    private void updateClickableSpans(String powerInfo, double phaseA, double phaseB, double phaseC, 
                                     double beforePhaseA, double beforePhaseB, double beforePhaseC) 
    {
        SpannableString spannableString = new SpannableString(powerInfo);
        
        // 不平衡度点击事件
        int unbalanceStart = powerInfo.indexOf("三相不平衡度");
        if (unbalanceStart >= 0)
        {
            int unbalanceEnd = unbalanceStart + 6;
            
            // 设置不平衡度点击显示计算过程
            ClickableSpan unbalanceClickableSpan = new ClickableSpan()
            {
                @Override
                public void onClick(@NonNull View view)
                {
                    try
                    {
                        UnbalanceCalculator.showCalculationProcess(
                            UserDataActivity.this,
                            phaseA, phaseB, phaseC
                        );
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds)
                {
                    ds.setColor(Color.rgb(51, 102, 153));
                    ds.setUnderlineText(false);
                    ds.setFakeBoldText(true);
                }
            };
            
            spannableString.setSpan(unbalanceClickableSpan, unbalanceStart, unbalanceEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        // 线路损耗点击事件 - 无论是否有相位调整均可点击
        int lossStart = powerInfo.indexOf("线路损耗");
        if (lossStart >= 0)
        {
            // 对第一个"线路损耗"设置点击事件
            int lossEnd = lossStart + 4;
            
            // 设置损耗点击显示详细计算
            ClickableSpan lossClickableSpan = new ClickableSpan()
            {
                @Override
                public void onClick(@NonNull View view)
                {
                    try
                    {
                        PowerLossCalculator.showLossCalculationDetails(
                            UserDataActivity.this,
                            phaseA, phaseB, phaseC
                        );
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds)
                {
                    ds.setColor(Color.rgb(51, 102, 153));
                    ds.setUnderlineText(false);
                    ds.setFakeBoldText(true);
                }
            };
            
            spannableString.setSpan(lossClickableSpan, lossStart, lossEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        // 如果有优化比，添加点击事件
        int optimizationStart = powerInfo.indexOf("线路损耗优化比");
        if (optimizationStart >= 0 && !powerInfo.contains("0.00%"))
        {
            int optimizationEnd = optimizationStart + 7;
            
            // 设置优化比点击事件
            ClickableSpan optimizationClickableSpan = new ClickableSpan()
            {
                @Override
                public void onClick(@NonNull View view)
                {
                    try
                    {
                        // 显示优化详情对话框 - 使用缓存的数据
                        if (beforePhaseCache != null) {
                            showOptimizationDetails(phaseA, phaseB, phaseC);
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds)
                {
                    ds.setColor(Color.rgb(51, 102, 153));
                    ds.setUnderlineText(false);
                    ds.setFakeBoldText(true);
                }
            };
            
            spannableString.setSpan(optimizationClickableSpan, optimizationStart, optimizationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        tvTotalPower.setText(spannableString);
        tvTotalPower.setMovementMethod(LinkMovementMethod.getInstance());
    }
    
    // 根据用户ID获取当前数据
    private UserData getUserDataById(String userId)
    {
        if (currentDate == null || userId == null) return null;
        
        // 从缓存中获取
        if (userDataCache.containsKey(userId)) {
            return userDataCache.get(userId);
        }
        
        // 缓存未命中，从数据库查询
        UserData userData = null;
        List<UserData> userDataList = dbHelper.getUserDataByDate(currentDate);
        for (UserData data : userDataList)
        {
            if (userId.equals(data.getUserId()))
            {
                userData = data;
                break;
            }
        }
        
        // 更新缓存
        if (userData != null) {
            userDataCache.put(userId, userData);
        }
        
        return userData;
    }
    
    // 显示优化详情对话框
    private void showOptimizationDetails(double phaseA, double phaseB, double phaseC)
    {
        try
        {
            // 使用缓存的调整前数据
            double beforePhaseA = beforePhaseCache.get("A");
            double beforePhaseB = beforePhaseCache.get("B");
            double beforePhaseC = beforePhaseCache.get("C");
            
            // 计算调整前的不平衡度
            double beforeUnbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(
                beforePhaseA, beforePhaseB, beforePhaseC);
            String beforeStatus = UnbalanceCalculator.getUnbalanceStatus(beforeUnbalanceRate);
            
            // 获取当前(调整后)的不平衡度
            double afterUnbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(phaseA, phaseB, phaseC);
            String afterStatus = UnbalanceCalculator.getUnbalanceStatus(afterUnbalanceRate);
            
            // 计算调整前后的线路损耗
            double afterLossCoefficient = PowerLossCalculator.calculateLineLossCoefficient(
                phaseA, phaseB, phaseC);
            double beforeLossCoefficient = PowerLossCalculator.calculateLineLossCoefficient(
                beforePhaseA, beforePhaseB, beforePhaseC);
            
            // 计算优化比例
            double optimizationRatio = 0;
            if (beforeLossCoefficient > 0) {
                optimizationRatio = (beforeLossCoefficient - afterLossCoefficient) / beforeLossCoefficient * 100;
            }
            
            // 构建详情信息
            String message = String.format(
                "线路损耗优化详情：\n\n" +
                "调整前三相电量：\n" +
                "A相：%.2f kWh\n" +
                "B相：%.2f kWh\n" +
                "C相：%.2f kWh\n" +
                "不平衡度：%.2f%% (%s)\n\n" +
                "调整前损耗：%.5f × R kWh\n\n" +
                "调整后三相电量：\n" +
                "A相：%.2f kWh\n" +
                "B相：%.2f kWh\n" +
                "C相：%.2f kWh\n" +
                "不平衡度：%.2f%% (%s)\n\n" +
                "调整后损耗：%.5f × R kWh\n\n" +
                "优化比例：%.2f%%",
                beforePhaseA, beforePhaseB, beforePhaseC, beforeUnbalanceRate, beforeStatus,
                beforeLossCoefficient,
                phaseA, phaseB, phaseC, afterUnbalanceRate, afterStatus,
                afterLossCoefficient,
                optimizationRatio
            );
            
            // 显示对话框
            new android.app.AlertDialog.Builder(this)
                .setTitle("线路损耗优化详情")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "获取优化详情失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 加载前一天数据
    private void loadPrevDay()
    {
        try
        {
            if (currentDate == null || currentDate.trim().isEmpty())
            {
                currentDate = dbHelper.getLatestDate();
                if (currentDate == null)
                {
                    Toast.makeText(this, "暂无数据", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            String prevDate = dbHelper.getPrevDate(currentDate);
            if (prevDate != null)
            {
                loadData(prevDate);
            }
            else
            {
                Toast.makeText(this, "已经是最早的数据", Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "加载前一天数据时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 加载后一天数据
    private void loadNextDay()
    {
        try
        {
            if (currentDate == null || currentDate.trim().isEmpty())
            {
                currentDate = dbHelper.getLatestDate();
                if (currentDate == null)
                {
                    Toast.makeText(this, "暂无数据", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            String nextDate = dbHelper.getNextDate(currentDate);
            if (nextDate != null)
            {
                loadData(nextDate);
            }
            else
            {
                Toast.makeText(this, "已经是最新的数据", Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "加载后一天数据时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 
