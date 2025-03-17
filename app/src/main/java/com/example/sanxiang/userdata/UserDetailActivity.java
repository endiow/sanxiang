package com.example.sanxiang.userdata;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserDetailActivity extends AppCompatActivity
{
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
        lineChart.setDragEnabled(false);   // 禁止拖动
        lineChart.setScaleEnabled(false);  // 禁止缩放
        lineChart.setPinchZoom(false);     // 禁止双指缩放
        lineChart.setDrawGridBackground(false);
        lineChart.setMaxVisibleValueCount(30); // 最大显示30个数据点
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

        // 设置前一天按钮点击事件
        findViewById(R.id.btnPrevDay).setOnClickListener(v -> 
        {
            String currentDate = etDate.getText().toString().trim();
            if (DateValidator.isValidDateFormat(currentDate, etDate))
            {
                int currentIndex = dates.indexOf(currentDate);
                if (currentIndex > 0)
                {
                    String prevDate = dates.get(currentIndex - 1);
                    etDate.setText(prevDate);
                    highlightDate(prevDate);
                }
                else
                {
                    Toast.makeText(this, "已经是最早的日期", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 设置后一天按钮点击事件
        findViewById(R.id.btnNextDay).setOnClickListener(v -> 
        {
            String currentDate = etDate.getText().toString().trim();
            if (DateValidator.isValidDateFormat(currentDate, etDate))
            {
                int currentIndex = dates.indexOf(currentDate);
                if (currentIndex < dates.size() - 1)
                {
                    String nextDate = dates.get(currentIndex + 1);
                    etDate.setText(nextDate);
                    highlightDate(nextDate);
                }
                else
                {
                    Toast.makeText(this, "已经是最新的日期", Toast.LENGTH_SHORT).show();
                }
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
                lineChart.highlightValue(index, 0);
                updatePowerInfo(standardDate);
                // 确保高亮的点在可见范围内
                lineChart.moveViewToX(Math.max(0, index - 2));
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
            // 动力用户：分别显示三相数据
            List<Entry> entriesA = new ArrayList<>();
            List<Entry> entriesB = new ArrayList<>();
            List<Entry> entriesC = new ArrayList<>();

            for (int i = 0; i < historicalData.size(); i++)
            {
                UserData data = historicalData.get(i);
                dates.add(data.getDate());
                
                entriesA.add(new Entry(i, (float) data.getPhaseAPower()));
                entriesB.add(new Entry(i, (float) data.getPhaseBPower()));
                entriesC.add(new Entry(i, (float) data.getPhaseCPower()));
            }

            // 创建三个数据集
            LineDataSet dataSetA = new LineDataSet(entriesA, "");
            dataSetA.setColor(Color.RED);
            dataSetA.setCircleColor(Color.RED);
            dataSetA.setDrawCircles(true);
            dataSetA.setCircleRadius(4f);
            dataSetA.setDrawValues(false);
            dataSetA.setLineWidth(2f);

            LineDataSet dataSetB = new LineDataSet(entriesB, "");
            dataSetB.setColor(Color.GREEN);
            dataSetB.setCircleColor(Color.GREEN);
            dataSetB.setDrawCircles(true);
            dataSetB.setCircleRadius(4f);
            dataSetB.setDrawValues(false);
            dataSetB.setLineWidth(2f);

            LineDataSet dataSetC = new LineDataSet(entriesC, "");
            dataSetC.setColor(Color.BLUE);
            dataSetC.setCircleColor(Color.BLUE);
            dataSetC.setDrawCircles(true);
            dataSetC.setCircleRadius(4f);
            dataSetC.setDrawValues(false);
            dataSetC.setLineWidth(2f);

            // 禁用默认图例
            lineChart.getLegend().setEnabled(false);

            // 更新图表
            LineData lineData = new LineData(dataSetA, dataSetB, dataSetC);
            lineChart.setData(lineData);
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
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));
        lineChart.invalidate();
    }
} 