package com.example.sanxiang.userdata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.example.sanxiang.R;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.DateValidator;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.listener.ChartTouchListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UserDetailActivity extends AppCompatActivity
{
    private static final String TAG = "UserDetailActivity";
    private DatabaseHelper dbHelper;    // 数据库操作类
    private LineChart lineChart;       // 图表
    private TextView tvUserInfo;       // 用户信息
    private TextView tvCurrentPower;   // 当前电量
    private EditText etDate;           // 日期输入框
    private String userId;             // 用户编号
    private List<String> dates = new ArrayList<>(); // 日期列表

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_detail);

        // 初始化视图和数据库
        dbHelper = new DatabaseHelper(this);
        initViews();
        setupChart();

        // 获取传递的用户ID
        userId = getIntent().getStringExtra("userId");
        if (userId == null)
        {
            Toast.makeText(this, "用户ID无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 显示用户基本信息
        String userInfo = dbHelper.getUserInfo(userId);
        if (userInfo != null)
        {
            tvUserInfo.setText(userInfo);
        }

        // 设置日期输入监听
        setupDateInput();

        // 加载数据
        updateChart();

        // 显示最新日期的数据
        String latestDate = dbHelper.getLatestDate();
        if (latestDate != null)
        {
            etDate.setText(latestDate);
            highlightDate(latestDate);
        }
    }

    private void initViews()
    {
        lineChart = findViewById(R.id.lineChart);   // 图表
        tvUserInfo = findViewById(R.id.tvUserInfo);     // 用户信息
        tvCurrentPower = findViewById(R.id.tvCurrentPower);     // 当前电量
        etDate = findViewById(R.id.etDate);     // 日期输入框
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

        // 设置点击监听
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener()
        {
            @Override
            public void onValueSelected(Entry e, Highlight h)
            {
                int index = (int) e.getX();
                if (index >= 0 && index < dates.size())
                {
                    String date = dates.get(index);
                    etDate.setText(date);
                    updatePowerInfo(date);
                }
            }

            @Override
            public void onNothingSelected()
            {
                String currentDate = etDate.getText().toString().trim();
                if (DateValidator.isValidDateFormat(currentDate, etDate))
                {
                    updatePowerInfo(currentDate);
                }
            }
        });
        
        // 添加图表滚动监听，确保日期显示与图表可见区域同步
        lineChart.setOnChartGestureListener(new OnChartGestureListener() 
        {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}
            
            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) 
            {
                // 当用户结束滚动手势时更新日期显示
                if (lastPerformedGesture == ChartTouchListener.ChartGesture.DRAG || 
                    lastPerformedGesture == ChartTouchListener.ChartGesture.FLING) 
                {
                    syncDateWithVisibleRange();
                }
            }
            
            @Override
            public void onChartLongPressed(MotionEvent me) {}
            
            @Override
            public void onChartDoubleTapped(MotionEvent me) {}
            
            @Override
            public void onChartSingleTapped(MotionEvent me) {}
            
            @Override
            public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {}
            
            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {}
            
            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {}
        });
    }

    // 同步日期显示与图表可见区域
    private void syncDateWithVisibleRange() 
    {
        if (dates != null && !dates.isEmpty()) 
        {
            int firstVisibleIndex = (int)lineChart.getLowestVisibleX();
            if (firstVisibleIndex >= 0 && firstVisibleIndex < dates.size()) 
            {
                String newDate = dates.get(firstVisibleIndex);
                etDate.setText(newDate);
                updatePowerInfo(newDate);
            }
        }
    }

    private void setupDateInput()
    {
        // 设置日期输入监听
        etDate.setOnEditorActionListener((v, actionId, event) -> 
        {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            {
                String date = etDate.getText().toString().trim();
                if (DateValidator.isValidDateFormat(date, etDate))
                {
                    highlightDate(date);
                }
                return true;
            }
            return false;
        });

        // 设置前一天按钮点击事件 - 显示前一天的数据
        findViewById(R.id.btnPrevDay).setOnClickListener(v -> 
        {
            // 获取当前选中日期的索引
            int currentIndex = -1;
            String currentDate = etDate.getText().toString().trim();
            
            for (int i = 0; i < dates.size(); i++) {
                if (dates.get(i).equals(currentDate)) {
                    currentIndex = i;
                    break;
                }
            }
            
            // 如果找到当前日期，并且不是第一个日期
            if (currentIndex > 0) {
                // 显示前一天数据
                String prevDate = dates.get(currentIndex - 1);
                etDate.setText(prevDate);
                highlightDate(prevDate);
            } else {
                Toast.makeText(this, "已经是最早的日期", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置后一天按钮点击事件 - 显示后一天的数据
        findViewById(R.id.btnNextDay).setOnClickListener(v -> 
        {
            // 获取当前选中日期的索引
            int currentIndex = -1;
            String currentDate = etDate.getText().toString().trim();
            
            for (int i = 0; i < dates.size(); i++) {
                if (dates.get(i).equals(currentDate)) {
                    currentIndex = i;
                    break;
                }
            }
            
            // 如果找到当前日期，并且不是最后一个日期
            if (currentIndex >= 0 && currentIndex < dates.size() - 1) {
                // 显示后一天数据
                String nextDate = dates.get(currentIndex + 1);
                etDate.setText(nextDate);
                highlightDate(nextDate);
            } else {
                Toast.makeText(this, "已经是最新的日期", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void highlightDate(String date)
    {
        try
        {
            // 确保日期格式标准化
            if (!DateValidator.isValidDateFormat(date, null))
            {
                Toast.makeText(this, "日期格式无效", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 将日期转换为标准格式 (yyyy-MM-dd)
            String[] parts = date.trim().split("[-/.]");
            String standardDate = String.format("%04d-%02d-%02d",
                parts[0].length() == 2 ? Integer.parseInt("20" + parts[0]) : Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
            
            int index = -1;
            // 查找匹配的日期（考虑不同的格式）
            for (int i = 0; i < dates.size(); i++)
            {
                String[] dateParts = dates.get(i).split("[-/.]");
                String currentStandardDate = String.format("%04d-%02d-%02d",
                    dateParts[0].length() == 2 ? Integer.parseInt("20" + dateParts[0]) : Integer.parseInt(dateParts[0]),
                    Integer.parseInt(dateParts[1]),
                    Integer.parseInt(dateParts[2]));
                
                if (currentStandardDate.equals(standardDate))
                {
                    index = i;
                    break;
                }
            }

            if (index >= 0)
            {
                // 高亮选中的日期点
                lineChart.highlightValue(index, 0);
                
                // 更新电量信息
                updatePowerInfo(standardDate);
                
                // 计算可见范围，确保高亮的点居中显示
                float visibleRange = lineChart.getVisibleXRange();
                float halfRange = visibleRange / 2;
                float newX = Math.max(0, index - halfRange);
                
                // 如果靠近数据末尾，确保不超出范围
                if (newX + visibleRange > dates.size())
                {
                    newX = Math.max(0, dates.size() - visibleRange);
                }
                
                // 移动视图到计算的位置
                lineChart.moveViewToX(newX);
                lineChart.invalidate();
            }
            else
            {
                Toast.makeText(this, "未找到该日期的数据", Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "日期处理出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePowerInfo(String date)
    {
        try
        {
            // 确保日期格式标准化
            if (!DateValidator.isValidDateFormat(date, null))
            {
                Toast.makeText(this, "日期格式无效", Toast.LENGTH_SHORT).show();
                return;
            }

            // 将日期转换为标准格式
            String[] parts = date.trim().split("[-/.]");
            String standardDate = String.format("%04d-%02d-%02d",
                parts[0].length() == 2 ? Integer.parseInt("20" + parts[0]) : Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));

            List<UserData> userDataList = dbHelper.getUserDataByDate(standardDate);
            for (UserData data : userDataList)
            {
                if (data.getUserId().equals(userId))
                {
                    String powerInfo = String.format(
                        "相位：%s\n" +
                        "A相电量：%.2f\n" +
                        "B相电量：%.2f\n" +
                        "C相电量：%.2f",
                        data.getPhase(),
                        data.getPhaseAPower(),
                        data.getPhaseBPower(),
                        data.getPhaseCPower()
                    );
                    tvCurrentPower.setText(powerInfo);
                    return;
                }
            }
            tvCurrentPower.setText(String.format(
                "相位：无数据\n" +
                "A相电量：%.2f\n" +
                "B相电量：%.2f\n" +
                "C相电量：%.2f",
                0.0, 0.0, 0.0

            ));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "获取电量数据出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChart()
    {
        // 获取最近30天的数据
        List<UserData> historicalData = dbHelper.getUserLastNDaysData(userId, 30);
        if (historicalData.isEmpty())
        {
            Toast.makeText(this, "没有历史数据", Toast.LENGTH_SHORT).show();
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

            for (int i = 0; i < historicalData.size(); i++)
            {
                UserData data = historicalData.get(i);
                dates.add(data.getDate());
                
                // 根据相位选择对应的电量值和颜色
                float powerValue;
                int color;
                switch (data.getPhase().toUpperCase())
                {
                    case "A":
                        powerValue = (float) data.getPhaseAPower();
                        color = Color.RED;
                        break;
                    case "B":
                        powerValue = (float) data.getPhaseBPower();
                        color = Color.GREEN;
                        break;
                    case "C":
                        powerValue = (float) data.getPhaseCPower();
                        color = Color.BLUE;
                        break;
                    default:
                        powerValue = 0f;
                        color = Color.GRAY;
                        break;
                }
                entries.add(new Entry(i, powerValue));
                colors.add(color);
            }

            // 创建主数据集
            LineDataSet dataSet = new LineDataSet(entries, "");
            dataSet.setDrawCircles(true);
            dataSet.setCircleRadius(4f);
            dataSet.setDrawValues(false);
            dataSet.setLineWidth(2f);
            dataSet.setMode(LineDataSet.Mode.LINEAR);
            dataSet.setColors(colors);
            dataSet.setCircleColors(colors);

            // 禁用默认图例
            lineChart.getLegend().setEnabled(false);

            // 更新图表
            LineData lineData = new LineData(dataSet);
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
        
        if (dates.size() > 0) {
            // 设置图表可见范围为7天
            lineChart.setVisibleXRangeMaximum(7);
            
            // 默认显示最近的7天数据
            if (dates.size() > 7) {
                lineChart.moveViewToX(dates.size() - 7);
                
                // 同步更新当前显示的日期为范围内第一个日期
                if (dates.size() > 0) {
                    int firstVisibleIndex = (int)lineChart.getLowestVisibleX();
                    if (firstVisibleIndex >= 0 && firstVisibleIndex < dates.size()) {
                        etDate.setText(dates.get(firstVisibleIndex));
                        updatePowerInfo(dates.get(firstVisibleIndex));
                    }
                }
            } else {
                lineChart.moveViewToX(0);
            }
        }
        
        // 启用拖动，禁用缩放
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        
        // 刷新图表
        lineChart.invalidate();
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
                Double oldPhaseAPower = (Double) adjustment.get("phaseAPower");
                Double oldPhaseBPower = (Double) adjustment.get("phaseBPower");
                Double oldPhaseCPower = (Double) adjustment.get("phaseCPower");

                Double newPhaseAPower = (Double) data.getPhaseAPower();
                Double newPhaseBPower = (Double) data.getPhaseBPower();
                Double newPhaseCPower = (Double) data.getPhaseCPower();
                
                Log.d(TAG, "相位调整数据比较 - 日期: " + data.getDate());
                Log.d(TAG, "旧相位: " + oldPhase + ", 新相位: " + newPhase);
                Log.d(TAG, String.format("旧数据 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                        oldPhaseAPower, oldPhaseBPower, oldPhaseCPower));
                Log.d(TAG, String.format("新数据 - A相: %.2f, B相: %.2f, C相: %.2f", 
                                        newPhaseAPower, newPhaseBPower, newPhaseCPower));
                
                if (oldPhase != null && newPhase != null)
                {
                    // 判断调整步数
                    boolean isOneStep = false;
                    // 检查电量值是否按顺时针方向旋转（A->B, B->C, C->A）
                    boolean conditionA = Math.abs(oldPhaseAPower - newPhaseBPower) < 0.01;
                    boolean conditionB = Math.abs(oldPhaseBPower - newPhaseCPower) < 0.01;
                    boolean conditionC = Math.abs(oldPhaseCPower - newPhaseAPower) < 0.01;
                    
                    Log.d(TAG, String.format("条件检查 - A->B: %b, B->C: %b, C->A: %b", 
                                            conditionA, conditionB, conditionC));
                    
                    if(conditionA && conditionB && conditionC)
                    {
                        isOneStep = true;
                    }
                    
                    Log.d(TAG, "调整结果 - isOneStep: " + isOneStep);
                    
                    // 一步调整（顺时针）：颜色循环变化
                    if (isOneStep)
                    {
                        // 保存当前颜色
                        int tempColorA = currentColorA;
                        int tempColorB = currentColorB;
                        int tempColorC = currentColorC;
                        
                        // 颜色顺时针旋转：红->绿->蓝->红
                        currentColorA = tempColorB;  // A相的颜色变为原来B相的颜色
                        currentColorB = tempColorC;  // B相的颜色变为原来C相的颜色
                        currentColorC = tempColorA;  // C相的颜色变为原来A相的颜色
                    }
                    // 两步调整（逆时针）：颜色逆时针变化
                    else
                    {
                        // 保存当前颜色
                        int tempColorA = currentColorA;
                        int tempColorB = currentColorB;
                        int tempColorC = currentColorC;
                        
                        // 颜色逆时针旋转：红->蓝->绿->红
                        currentColorA = tempColorC;  // A相的颜色变为原来C相的颜色
                        currentColorB = tempColorA;  // B相的颜色变为原来A相的颜色
                        currentColorC = tempColorB;  // C相的颜色变为原来B相的颜色
                    }
                }
            }
            
            // 添加当前颜色
            colorsA.add(currentColorA);
            colorsB.add(currentColorB);
            colorsC.add(currentColorC);
        }
        
        // 创建数据集
        LineDataSet setA = new LineDataSet(entriesA, "A相");
        LineDataSet setB = new LineDataSet(entriesB, "B相");
        LineDataSet setC = new LineDataSet(entriesC, "C相");
        
        // 设置数据集基本属性
        configureDataSet(setA, Color.RED, colorsA);
        configureDataSet(setB, Color.GREEN, colorsB);
        configureDataSet(setC, Color.BLUE, colorsC);
        
        // 禁用默认图例
        lineChart.getLegend().setEnabled(false);
        
        // 更新图表
        LineData lineData = new LineData(setA, setB, setC);
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
} 