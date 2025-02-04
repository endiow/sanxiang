package com.example.sanxiang;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity
{
    private static final int PERMISSION_REQUEST_CODE = 1;
    private DatabaseHelper dbHelper;
    private LineChart lineChart;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private Button btnImportData;
    private Button btnViewData;
    private Button btnPredict;
    private Button btnAdjustPhase;
    private TextView textView;

    // 初始化界面、设置布局和基本配置
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // 调用父类的onCreate方法
        super.onCreate(savedInstanceState);
        // 启用边缘到边缘的显示效果
        EdgeToEdge.enable(this);
        // 设置Activity的布局
        setContentView(R.layout.activity_main);
        // 设置系统栏（状态栏和导航栏）的内边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        // 初始化数据库帮助类
        dbHelper = new DatabaseHelper(this);
        // 获取图表视图引用
        lineChart = findViewById(R.id.lineChart);
        // 设置图表的基本配置
        setupChart();
        // 设置所有按钮的点击事件监听器
        setupButtons();
        // 初始化文件选择器结果处理器
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> 
            {
                if (result.getResultCode() == RESULT_OK)
                {
                    Intent data = result.getData();
                    List<Uri> uris = new ArrayList<>();
                    
                    if (data != null)
                    {
                        // 处理多选结果
                        if (data.getClipData() != null)
                        {
                            ClipData clipData = data.getClipData();
                            for (int i = 0; i < clipData.getItemCount(); i++)
                            {
                                uris.add(clipData.getItemAt(i).getUri());
                            }
                        }
                        // 处理单选结果
                        else if (data.getData() != null)
                        {
                            uris.add(data.getData());
                        }
                        
                        if (!uris.isEmpty())
                        {
                            handleMultipleFileSelection(uris);
                        }
                    }
                }
            });

        // 测试 Python 环境
        //testPythonEnvironment();
    }

    //测试Python环境
    private void testPythonEnvironment() 
    {
        try 
        {
            // 初始化 Python
            if (!Python.isStarted()) 
            {
                Python.start(new AndroidPlatform(this));
            }
            
            // 启动测试结果界面
            Intent intent = new Intent(this, TestResultActivity.class);
            startActivity(intent);
        } 
        catch (Exception e) 
        {
            textView.setText("Python 环境初始化错误：" + e.getMessage());
        }
    }

    //初始化图表
    private void setupChart()
    {
        // 配置图表
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        
        // 调整边距
        lineChart.setExtraBottomOffset(20f);
        lineChart.setExtraLeftOffset(10f);
        lineChart.setExtraRightOffset(25f);  // 增加右边距
        lineChart.setExtraTopOffset(10f);

        // 配置X轴
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-30);  // 减小旋转角度
        xAxis.setTextSize(11f);            // 稍微减小文字大小
        xAxis.setYOffset(5f);              // 增加Y方向的偏移，让标签往上移

        // 配置Y轴
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextSize(12f);
        lineChart.getAxisRight().setEnabled(false);

        // 配置图例
        lineChart.getLegend().setTextSize(12f);
        lineChart.getLegend().setFormSize(12f);

        // 更新数据
        updateChartData();
    }

    //初始化按钮
    private void setupButtons()
    {
        btnImportData = findViewById(R.id.btnImportData);
        btnViewData = findViewById(R.id.btnViewData);
        btnPredict = findViewById(R.id.btnPredict);
        btnAdjustPhase = findViewById(R.id.btnAdjustPhase);
        textView = findViewById(R.id.textView);

        btnImportData.setOnClickListener(v -> checkPermissionAndOpenPicker());
        btnViewData.setOnClickListener(v -> 
        {
            Intent intent = new Intent(this, UserDataActivity.class);
            startActivity(intent);
        });
        btnPredict.setOnClickListener(v -> 
        {
            Intent intent = new Intent(this, PredictionActivity.class);
            startActivity(intent);
        });
        btnAdjustPhase.setOnClickListener(v -> handleAdjustPhase());
    }


    //-----------------------------------导入数据-----------------------------------
    //检查权限并打开文件选择器
    private void checkPermissionAndOpenPicker()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            // 如果没有权限，申请权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
        else
        {
            // 已有权限，打开文件选择器
            openFilePicker();
        }
    }

    //请求权限结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE)
        {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                // 用户授予了权限
                openFilePicker();
            }
            else
            {
                // 用户拒绝了权限
                Toast.makeText(this, "需要存储权限才能读取文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker()
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/*");  // 允许选择所有文本文件
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);  // 允许多选
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(Intent.createChooser(intent, "选择CSV文件"));
        }
        catch (Exception e)
        {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    //处理多选结果
    private void handleMultipleFileSelection(List<Uri> uris)
    {
        if (uris == null || uris.isEmpty())
        {
            Toast.makeText(this, "没有选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try
        {
            int successCount = 0;
            int totalFiles = uris.size();
            // 使用Map按日期和用户ID分组存储数据
            Map<String, Map<String, List<UserData>>> dateUserGroupedData = new HashMap<>();

            // 读取所有文件数据
            for (Uri uri : uris)
            {
                if (uri == null) continue;

                InputStream inputStream = null;
                BufferedReader reader = null;
                try
                {
                    inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream == null)
                    {
                        Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
                        continue;
                    }

                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;

                    // 跳过标题行
                    reader.readLine();

                    while ((line = reader.readLine()) != null)
                    {
                        if (line.trim().isEmpty()) continue;

                        try
                        {
                            String[] data = line.split(",");
                            if (data.length < 9)
                            {
                                Toast.makeText(this, "文件格式错误：数据列数不足", Toast.LENGTH_SHORT).show();
                                continue;
                            }

                            // 验证数据
                            String date = data[0].trim();
                            String userId = data[1].trim();
                            if (date.isEmpty() || userId.isEmpty())
                            {
                                Toast.makeText(this, "文件格式错误：日期或用户ID为空", Toast.LENGTH_SHORT).show();
                                continue;
                            }

                            try
                            {
                                // 验证电量数据是否为有效数字
                                Double.parseDouble(data[6].trim());
                                Double.parseDouble(data[7].trim());
                                Double.parseDouble(data[8].trim());
                            }
                            catch (NumberFormatException e)
                            {
                                Toast.makeText(this, "文件格式错误：电量数据无效", Toast.LENGTH_SHORT).show();
                                continue;
                            }

                            UserData userData = new UserData();
                            userData.setDate(date);
                            userData.setUserId(userId);
                            userData.setUserName(data[2].trim());
                            userData.setRouteNumber(data[3].trim());
                            userData.setRouteName(data[4].trim());
                            userData.setPhase(data[5].trim());
                            userData.setPhaseAPower(Double.parseDouble(data[6].trim()));
                            userData.setPhaseBPower(Double.parseDouble(data[7].trim()));
                            userData.setPhaseCPower(Double.parseDouble(data[8].trim()));

                            // 按日期和用户ID分组存储数据
                            dateUserGroupedData
                                .computeIfAbsent(date, k -> new HashMap<>())
                                .computeIfAbsent(userId, k -> new ArrayList<>())
                                .add(userData);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                            Toast.makeText(this, "处理数据行时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    successCount++;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    Toast.makeText(this, "读取文件时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                finally
                {
                    try
                    {
                        if (reader != null) reader.close();
                        if (inputStream != null) inputStream.close();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            if (dateUserGroupedData.isEmpty())
            {
                Toast.makeText(this, "没有有效数据可以导入", Toast.LENGTH_SHORT).show();
                return;
            }

            // 导入数据到数据库
            try
            {
                dbHelper.importBatchData(dateUserGroupedData);

                // 显示导入结果
                if (successCount == totalFiles)
                {
                    Toast.makeText(this, "所有文件导入成功", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(this, String.format("成功导入 %d/%d 个文件", successCount, totalFiles), Toast.LENGTH_SHORT).show();
                }

                // 更新图表
                updateChartData();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                Toast.makeText(this, "导入数据到数据库时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "处理文件时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //-----------------------------------相位调整-----------------------------------
    //相位调整
    private void handleAdjustPhase()
    {
        // TODO: 实现相位调整功能
        Toast.makeText(this, "相位调整功能待实现", Toast.LENGTH_SHORT).show();
    }


    //-----------------------------------更新图表数据-----------------------------------
    //更新图表数据
    private void updateChartData()
    {
        // 准备三相数据
        List<Entry> entriesA = new ArrayList<>();
        List<Entry> entriesB = new ArrayList<>();
        List<Entry> entriesC = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        // 获取最近7天的数据
        List<String> dbDates = dbHelper.getLastNDays(7);
        
        if (!dbDates.isEmpty())
        {
            // 反转日期列表，使其按时间顺序排列
            Collections.reverse(dbDates);
            dates.addAll(dbDates);

            // 获取每天的数据
            for (int i = 0; i < dates.size(); i++)
            {
                double[] powers = dbHelper.getTotalPowerByDate(dates.get(i));
                entriesA.add(new Entry(i, (float)powers[0]));
                entriesB.add(new Entry(i, (float)powers[1]));
                entriesC.add(new Entry(i, (float)powers[2]));
            }
        }
        else
        {
            // 没有数据时，添加空的日期标签
            for (int i = 6; i >= 0; i--)
            {
                dates.add(String.format("Day %d", i + 1));
            }
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

        // 设置Y轴范围
        if (dbDates.isEmpty())
        {
            // 没有数据时，设置一个合适的Y轴范围
            lineChart.getAxisLeft().setAxisMinimum(0f);
            lineChart.getAxisLeft().setAxisMaximum(100f);
        }
        else
        {
            // 有数据时，让图表自动调整范围
            lineChart.getAxisLeft().resetAxisMinimum();
            lineChart.getAxisLeft().resetAxisMaximum();
        }

        // 刷新图表
        lineChart.invalidate();
    }
}