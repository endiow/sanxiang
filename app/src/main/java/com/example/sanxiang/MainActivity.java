package com.example.sanxiang;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;

import com.example.sanxiang.data.PredictionResult;
import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

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
        Button btnImportData = findViewById(R.id.btnImportData);
        Button btnViewData = findViewById(R.id.btnViewData);
        Button btnPredict = findViewById(R.id.btnPredict);
        Button btnAdjustPhase = findViewById(R.id.btnAdjustPhase);

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
        if (uris != null && !uris.isEmpty())
        {
            int successCount = 0;
            int totalFiles = uris.size();
            // 使用Map按日期分组存储数据
            Map<String, List<UserData>> dateGroupedData = new HashMap<>();

            for (Uri uri : uris)
            {
                try
                {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;

                    // 跳过标题行
                    reader.readLine();

                    while ((line = reader.readLine()) != null)
                    {
                        try
                        {
                            String[] data = line.split(",");
                            if (data.length >= 9)
                            {
                                UserData userData = new UserData();
                                String date = data[0].trim();
                                userData.setDate(date);
                                userData.setUserId(data[1].trim());
                                userData.setUserName(data[2].trim());
                                userData.setRouteNumber(data[3].trim());
                                userData.setRouteName(data[4].trim());
                                userData.setPhase(data[5].trim());
                                userData.setPhaseAPower(Double.parseDouble(data[6].trim()));
                                userData.setPhaseBPower(Double.parseDouble(data[7].trim()));
                                userData.setPhaseCPower(Double.parseDouble(data[8].trim()));

                                // 按日期分组存储数据
                                dateGroupedData.computeIfAbsent(date, k -> new ArrayList<>()).add(userData);
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }

                    reader.close();
                    inputStream.close();
                    successCount++;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            // 按日期顺序导入数据
            List<String> sortedDates = new ArrayList<>(dateGroupedData.keySet());
            Collections.sort(sortedDates);  // 按日期升序排序

            for (String date : sortedDates)
            {
                List<UserData> dayData = dateGroupedData.get(date);
                if (!dayData.isEmpty())
                {
                    dbHelper.importData(dayData);
                }
            }

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
    }

    //预测下一天的用电量
    private void predictNextDayPower()
    {
        List<String> userIds = dbHelper.getAllUserIds();
        List<PredictionResult> predictions = new ArrayList<>();
        
        for (String userId : userIds)
        {
            List<UserData> historicalData = dbHelper.getLastTwentyDaysData(userId);
            if (historicalData.size() >= 3)  // 至少需要3天的数据才能预测
            {
                PredictionResult prediction = predictUserPower(historicalData);
                predictions.add(prediction);
            }
        }
        
        // 显示预测结果
        showPredictionResults(predictions);
    }

    //预测用户用电量
    private PredictionResult predictUserPower(List<UserData> historicalData)
    {
        PredictionResult result = new PredictionResult();
        result.setUserId(historicalData.get(0).getUserId());
        result.setUserName(historicalData.get(0).getUserName());
        
        // 使用简单移动平均法预测
        double sumA = 0, sumB = 0, sumC = 0;
        int count = Math.min(7, historicalData.size());  // 使用最近7天的数据
        
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

    //显示预测结果
    private void showPredictionResults(List<PredictionResult> predictions)
    {
        // 创建预测结果的展示字符串
        StringBuilder message = new StringBuilder("明日用电量预测：\n\n");
        double totalA = 0, totalB = 0, totalC = 0;
        
        for (PredictionResult prediction : predictions)
        {
            message.append(String.format("用户：%s\n", prediction.getUserName()));
            message.append(String.format("A相：%.2f\n", prediction.getPredictedPhaseAPower()));
            message.append(String.format("B相：%.2f\n", prediction.getPredictedPhaseBPower()));
            message.append(String.format("C相：%.2f\n\n", prediction.getPredictedPhaseCPower()));
            
            totalA += prediction.getPredictedPhaseAPower();
            totalB += prediction.getPredictedPhaseBPower();
            totalC += prediction.getPredictedPhaseCPower();
        }
        
        message.append("总计：\n");
        message.append(String.format("A相总量：%.2f\n", totalA));
        message.append(String.format("B相总量：%.2f\n", totalB));
        message.append(String.format("C相总量：%.2f", totalC));
        
        // 显示对话框
        new AlertDialog.Builder(this)
                .setTitle("预测结果")
                .setMessage(message.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    //相位调整
    private void handleAdjustPhase()
    {
        // TODO: 实现相位调整功能
        Toast.makeText(this, "相位调整功能待实现", Toast.LENGTH_SHORT).show();
    }


    //更新图表数据
    private void updateChartData()
    {
        List<String> dates = dbHelper.getLastNDays(7);
        if (dates.isEmpty()) return;

        // 准备三相数据
        List<Entry> entriesA = new ArrayList<>();
        List<Entry> entriesB = new ArrayList<>();
        List<Entry> entriesC = new ArrayList<>();

        // 反转日期列表，使其按时间顺序排列
        Collections.reverse(dates);

        // 获取每天的数据
        for (int i = 0; i < dates.size(); i++)
        {
            double[] powers = dbHelper.getTotalPowerByDate(dates.get(i));
            entriesA.add(new Entry(i, (float)powers[0]));
            entriesB.add(new Entry(i, (float)powers[1]));
            entriesC.add(new Entry(i, (float)powers[2]));
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