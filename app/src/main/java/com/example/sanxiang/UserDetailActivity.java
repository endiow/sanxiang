package com.example.sanxiang;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
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
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserDetailActivity extends AppCompatActivity
{
    private DatabaseHelper dbHelper;
    private LineChart lineChart;
    private TextView tvUserInfo;
    private TextView tvCurrentPower;
    private EditText etDate;
    private String userId;
    private List<String> dates = new ArrayList<>();

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
        lineChart = findViewById(R.id.lineChart);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        tvCurrentPower = findViewById(R.id.tvCurrentPower);
        etDate = findViewById(R.id.etDate);
    }

    private void setupDateInput()
    {
        // 设置日期输入监听
        etDate.setOnEditorActionListener((v, actionId, event) -> 
        {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            {
                String date = etDate.getText().toString().trim();
                if (isValidDateFormat(date))
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
            if (isValidDateFormat(currentDate))
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
            if (isValidDateFormat(currentDate))
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

    private boolean isValidDateFormat(String date)
    {
        String regex = "\\d{4}-\\d{2}-\\d{2}";
        if (!date.matches(regex))
        {
            Toast.makeText(this, "日期格式无效，请使用yyyy-MM-dd格式", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void highlightDate(String date)
    {
        int index = dates.indexOf(date);
        if (index >= 0)
        {
            lineChart.highlightValue(index, 0);
            updatePowerInfo(date);
            // 确保高亮的点在可见范围内
            lineChart.moveViewToX(Math.max(0, index - 2));
        }
        else
        {
            Toast.makeText(this, "未找到该日期的数据", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePowerInfo(String date)
    {
        List<UserData> userDataList = dbHelper.getUserDataByDate(date);
        for (UserData data : userDataList)
        {
            if (data.getUserId().equals(userId))
            {
                String powerInfo = String.format(
                    "A相电量：%.2f\n" +
                    "B相电量：%.2f\n" +
                    "C相电量：%.2f",
                    data.getPhaseAPower(),
                    data.getPhaseBPower(),
                    data.getPhaseCPower()
                );
                tvCurrentPower.setText(powerInfo);
                return;
            }
        }
        tvCurrentPower.setText(String.format(
            "A相电量：%.2f\n" +
            "B相电量：%.2f\n" +
            "C相电量：%.2f",
            0.0, 0.0, 0.0
        ));
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

        // 配置图例
        lineChart.getLegend().setTextSize(12f);
        lineChart.getLegend().setFormSize(12f);

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
                if (isValidDateFormat(currentDate))
                {
                    updatePowerInfo(currentDate);
                }
            }
        });
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
        List<Entry> entriesA = new ArrayList<>();
        List<Entry> entriesB = new ArrayList<>();
        List<Entry> entriesC = new ArrayList<>();
        dates = new ArrayList<>();

        // 反转列表以按时间顺序显示
        Collections.reverse(historicalData);

        for (int i = 0; i < historicalData.size(); i++)
        {
            UserData data = historicalData.get(i);
            entriesA.add(new Entry(i, (float) data.getPhaseAPower()));
            entriesB.add(new Entry(i, (float) data.getPhaseBPower()));
            entriesC.add(new Entry(i, (float) data.getPhaseCPower()));
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
    }
} 