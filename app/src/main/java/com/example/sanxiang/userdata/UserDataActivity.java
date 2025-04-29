package com.example.sanxiang.userdata;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
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
                    "线路损耗：%.5f × R/U² kWh\n" +
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
                            
                            Log.d("UserDataActivity", "开始计算调整前电量数据 - 当前日期: " + currentDate);
                            Log.d("UserDataActivity", String.format("当前调整后电量 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                phaseA, phaseB, phaseC));
                            
                            // 检查是否存在D相（动力相位）并进行相应处理
                            boolean hasDPhase = false;
                            int dPhaseSteps = 0;
                            
                            // 从old_data表获取调整前的三相总量数据
                            List<String> adjustedUsers = dbHelper.getUsersWithPhaseAdjustmentOnDate(currentDate);
                            Log.d("UserDataActivity", "找到调整过相位的用户数量: " + adjustedUsers.size());
                            
                            // 先检查是否有D相用户，并获取其旧电量与新电量作比较
                            for (String userId : adjustedUsers) {
                                Map<String, Object> adjustment = dbHelper.getUserPhaseAdjustment(userId, currentDate);
                                UserData userData = getUserDataById(userId);
                                
                                if (adjustment != null && userData != null && "D".equals(userData.getPhase())) {
                                    hasDPhase = true;
                                    Log.d("UserDataActivity", "找到D相用户: " + userId);
                                    
                                    // 获取旧三相电量数据
                                    double oldPhaseA = (double) adjustment.get("phaseAPower");
                                    double oldPhaseB = (double) adjustment.get("phaseBPower");
                                    double oldPhaseC = (double) adjustment.get("phaseCPower");
                                    
                                    // 获取新三相电量数据
                                    double newPhaseA = userData.getPhaseAPower();
                                    double newPhaseB = userData.getPhaseBPower();
                                    double newPhaseC = userData.getPhaseCPower();
                                    
                                    Log.d("UserDataActivity", String.format(
                                        "旧电量 - A: %.2f, B: %.2f, C: %.2f", 
                                        oldPhaseA, oldPhaseB, oldPhaseC));
                                    Log.d("UserDataActivity", String.format(
                                        "新电量 - A: %.2f, B: %.2f, C: %.2f", 
                                        newPhaseA, newPhaseB, newPhaseC));
                                    
                                    // 检查是否为顺时针移动一步 (A->B, B->C, C->A)
                                    boolean isClockwise1Step = 
                                        Math.abs(newPhaseA - oldPhaseC) < 0.1 * Math.max(0.1, oldPhaseC) && 
                                        Math.abs(newPhaseB - oldPhaseA) < 0.1 * Math.max(0.1, oldPhaseA) && 
                                        Math.abs(newPhaseC - oldPhaseB) < 0.1 * Math.max(0.1, oldPhaseB);
                                    
                                    // 检查是否为顺时针移动两步 (A->C, B->A, C->B)
                                    boolean isClockwise2Steps = 
                                        Math.abs(newPhaseA - oldPhaseB) < 0.1 * Math.max(0.1, oldPhaseB) && 
                                        Math.abs(newPhaseB - oldPhaseC) < 0.1 * Math.max(0.1, oldPhaseC) && 
                                        Math.abs(newPhaseC - oldPhaseA) < 0.1 * Math.max(0.1, oldPhaseA);
                                    
                                    if (isClockwise1Step) {
                                        dPhaseSteps = 1;
                                        Log.d("UserDataActivity", "检测到D相用户顺时针移动1步");
                                        
                                        // 调整三相电量计算 - 考虑对总电量的影响
                                        Log.d("UserDataActivity", String.format(
                                            "调整前电量 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                            beforePhaseA, beforePhaseB, beforePhaseC));
                                            
                                        // A相总电量减去旧的C相电量，加上新的B相电量
                                        beforePhaseA = beforePhaseA - oldPhaseC + newPhaseB;
                                        // 其他相位也需要相应调整
                                        beforePhaseB = beforePhaseB - oldPhaseA + newPhaseC;
                                        beforePhaseC = beforePhaseC - oldPhaseB + newPhaseA;
                                        
                                        Log.d("UserDataActivity", String.format(
                                            "D相调整后电量 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                            beforePhaseA, beforePhaseB, beforePhaseC));
                                    }
                                    else if (isClockwise2Steps) {
                                        dPhaseSteps = 2;
                                        Log.d("UserDataActivity", "检测到D相用户顺时针移动2步");
                                        
                                        // 调整三相电量计算 - 考虑对总电量的影响
                                        Log.d("UserDataActivity", String.format(
                                            "调整前电量 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                            beforePhaseA, beforePhaseB, beforePhaseC));
                                            
                                        // A相总电量减去旧的B相电量，加上新的C相电量
                                        beforePhaseA = beforePhaseA - oldPhaseB + newPhaseC;
                                        // 其他相位也需要相应调整
                                        beforePhaseB = beforePhaseB - oldPhaseC + newPhaseA;
                                        beforePhaseC = beforePhaseC - oldPhaseA + newPhaseB;
                                        
                                        Log.d("UserDataActivity", String.format(
                                            "D相调整后电量(顺时针2步) - A相: %.2f, B相: %.2f, C相: %.2f", 
                                            beforePhaseA, beforePhaseB, beforePhaseC));
                                    }
                                    break;
                                }
                            }
                            
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
                                        
                                        Log.d("UserDataActivity", String.format(
                                            "用户ID: %s, 旧相位: %s, 新相位: %s, 电量: %.2f",
                                            userId, oldPhase, newPhase, userPower));
                                        Log.d("UserDataActivity", String.format(
                                            "用户三相电量 - A相: %.2f, B相: %.2f, C相: %.2f",
                                            userData.getPhaseAPower(), userData.getPhaseBPower(), userData.getPhaseCPower()));
                                        
                                        // 从现在的相位减去电量
                                        double beforeA = beforePhaseA;
                                        double beforeB = beforePhaseB;
                                        double beforeC = beforePhaseC;
                                        
                                        // 使用if-else结构代替switch
                                        if ("A".equals(newPhase)) {
                                            beforePhaseA -= userPower;
                                        } else if ("B".equals(newPhase)) {
                                            beforePhaseB -= userPower;
                                        } else if ("C".equals(newPhase)) {
                                            beforePhaseC -= userPower;
                                        } else {
                                            Log.e("UserDataActivity", "错误的新相位值: " + newPhase);
                                        }
                                        
                                        // 给原来的相位加上电量
                                        if ("A".equals(oldPhase)) {
                                            beforePhaseA += userPower;
                                        } else if ("B".equals(oldPhase)) {
                                            beforePhaseB += userPower;
                                        } else if ("C".equals(oldPhase)) {
                                            beforePhaseC += userPower;
                                        } else {
                                            Log.e("UserDataActivity", "错误的旧相位值: " + oldPhase);
                                        }
                                        
                                        Log.d("UserDataActivity", String.format(
                                            "计算中 - 用户: %s, 减去 %s相: %.2f, 加上 %s相: %.2f", 
                                            userId, newPhase, userPower, oldPhase, userPower));
                                        Log.d("UserDataActivity", String.format(
                                            "修正后电量 - A相: %.2f (变化: %.2f), B相: %.2f (变化: %.2f), C相: %.2f (变化: %.2f)",
                                            beforePhaseA, beforePhaseA - beforeA,
                                            beforePhaseB, beforePhaseB - beforeB,
                                            beforePhaseC, beforePhaseC - beforeC));
                                    }
                                }
                                else
                                {
                                    Log.d("UserDataActivity", "未找到用户" + userId + "的相位调整记录");
                                }
                            }
                            
                            Log.d("UserDataActivity", String.format(
                                "最终计算得到调整前电量 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                beforePhaseA, beforePhaseB, beforePhaseC));
                            
                            // 保存到缓存
                            beforePhaseCache = new HashMap<>();
                            beforePhaseCache.put("A", beforePhaseA);
                            beforePhaseCache.put("B", beforePhaseB);
                            beforePhaseCache.put("C", beforePhaseC);
                            if (hasDPhase) {
                                beforePhaseCache.put("D_STEPS", (double)dPhaseSteps);
                            }
                        }
                        
                        // 从缓存读取
                        final double beforePhaseA = beforePhaseCache.get("A");
                        final double beforePhaseB = beforePhaseCache.get("B");
                        final double beforePhaseC = beforePhaseCache.get("C");
                        final boolean hasDPhase = beforePhaseCache.containsKey("D_STEPS");
                        final int dPhaseSteps = hasDPhase ? beforePhaseCache.get("D_STEPS").intValue() : 0;
                        
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
                            String updatedInfo;
                            if (hasDPhase) {
                                updatedInfo = String.format(
                                    "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)\n" +
                                    "线路损耗：%.5f × R/U² kWh\n" +
                                    "线路损耗优化比：%.2f%%\n" +
                                    "D相顺时针移动步数：%d",
                                    phaseA, phaseB, phaseC, unbalanceRate, status,
                                    lossCoefficient, finalOptimizationRatio, dPhaseSteps
                                );
                            } else {
                                updatedInfo = String.format(
                                    "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)\n" +
                                    "线路损耗：%.5f × R/U² kWh\n" +
                                    "线路损耗优化比：%.2f%%",
                                    phaseA, phaseB, phaseC, unbalanceRate, status,
                                    lossCoefficient, finalOptimizationRatio
                                );
                            }
                            
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
                    "线路损耗：%.5f × R/U² kWh",
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
            
            // 输出详细日志信息用于调试
            Log.d("UserDataActivity", "显示优化详情对话框");
            Log.d("UserDataActivity", String.format("调整前数据 - A相: %.2f, B相: %.2f, C相: %.2f", 
                beforePhaseA, beforePhaseB, beforePhaseC));
            Log.d("UserDataActivity", String.format("调整后数据 - A相: %.2f, B相: %.2f, C相: %.2f", 
                phaseA, phaseB, phaseC));
            
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
            
            Log.d("UserDataActivity", String.format("调整前不平衡度: %.2f%%, 状态: %s", 
                beforeUnbalanceRate, beforeStatus));
            Log.d("UserDataActivity", String.format("调整后不平衡度: %.2f%%, 状态: %s", 
                afterUnbalanceRate, afterStatus));
            Log.d("UserDataActivity", String.format("调整前损耗系数: %.5f, 调整后损耗系数: %.5f", 
                beforeLossCoefficient, afterLossCoefficient));
            
            // 计算优化比例
            double optimizationRatio = 0;
            if (beforeLossCoefficient > 0) {
                optimizationRatio = (beforeLossCoefficient - afterLossCoefficient) / beforeLossCoefficient * 100;
            }
            
            Log.d("UserDataActivity", String.format("计算得到的优化比例: %.2f%%", optimizationRatio));
            
            // 构建详情信息
            String message = String.format(
                "线路损耗优化详情：\n\n" +
                "调整前三相电量：\n" +
                "A相：%.2f kWh\n" +
                "B相：%.2f kWh\n" +
                "C相：%.2f kWh\n" +
                "不平衡度：%.2f%% (%s)\n\n" +
                "调整前损耗：%.5f × R/U² kWh\n\n" +
                "调整后三相电量：\n" +
                "A相：%.2f kWh\n" +
                "B相：%.2f kWh\n" +
                "C相：%.2f kWh\n" +
                "不平衡度：%.2f%% (%s)\n\n" +
                "调整后损耗：%.5f × R/U² kWh\n\n" +
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
