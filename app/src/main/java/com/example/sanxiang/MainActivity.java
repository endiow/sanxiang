package com.example.sanxiang;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;  // 添加日志工具
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

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

import com.chaquo.python.android.AndroidPlatform;
import com.example.sanxiang.test.TestResultActivity;
import com.example.sanxiang.userdata.UserDataActivity;
import com.example.sanxiang.prediction.PredictionActivity;
import com.example.sanxiang.phasebalance.PhaseBalanceActivity;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.userdata.model.UserData;
import com.chaquo.python.Python;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";  // 添加日志标签
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;  // 10MB 文件大小限制
    
    private DatabaseHelper dbHelper;    // 数据库帮助类
    private LineChart lineChart;        // 图表视图
    private ActivityResultLauncher<Intent> filePickerLauncher; // 文件选择器结果处理器
    private Button btnImportData;        // 导入数据按钮
    private Button btnViewData;         // 查看数据按钮
    private Button btnPredict;          // 预测按钮
    private Button btnAdjustPhase;      // 相位调整按钮
    private Button btnClearData;        // 清空数据按钮
    private TextView textView;          // 文本视图

    // 初始化界面、设置布局和基本配置
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try
        {
            Log.i(TAG, "正在初始化应用...");
            
            // 初始化Python环境
            if (!Python.isStarted())
            {
                Log.d(TAG, "正在启动Python环境");
                Python.start(new AndroidPlatform(this));
            }

            dbHelper = new DatabaseHelper(this);
            lineChart = findViewById(R.id.lineChart);
            setupChart();
            setupButtons();
            updateChartData();
            
            // 初始化文件选择器结果处理器
            filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
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
                                // 在主线程中显示进度对话框
                                AlertDialog progressDialog = new AlertDialog.Builder(this)
                                    .setTitle("正在导入数据")
                                    .setMessage("正在处理文件：0/" + uris.size())
                                    .setCancelable(false)
                                    .create();
                                progressDialog.show();
                                
                                // 在后台线程中处理文件
                                new Thread(() -> {
                                    processFiles(uris, progressDialog);
                                }).start();
                            }
                        }
                    }
                });
            
            Log.i(TAG, "应用初始化完成");
        }
        catch (Exception e)
        {
            Log.e(TAG, "初始化应用出错", e);
            Toast.makeText(this, "初始化应用出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        // 启用边缘到边缘的显示效果
        EdgeToEdge.enable(this);
        // 设置系统栏（状态栏和导航栏）的内边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        // 每次返回主界面时更新图表数据
        updateChartData();
    }

    //测试Python环境
    private void testPythonEnvironment() 
    {
        try 
        {
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
        lineChart.setDragEnabled(false);   // 禁止拖动
        lineChart.setScaleEnabled(false);  // 禁止缩放
        lineChart.setPinchZoom(false);     // 禁止双指缩放
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
    }

    //初始化按钮
    private void setupButtons()
    {
        // 导入数据按钮
        btnImportData = findViewById(R.id.btnImportData);
        // 查看数据按钮
        btnViewData = findViewById(R.id.btnViewData);
        // 预测按钮
        btnPredict = findViewById(R.id.btnPredict);
        // 相位调整按钮
        btnAdjustPhase = findViewById(R.id.btnAdjustPhase);
        // 清空数据按钮
        btnClearData = findViewById(R.id.btnClearData);
        // 文本视图
        textView = findViewById(R.id.textView);

        btnImportData.setOnClickListener(v -> checkPermissionAndOpenPicker());
        btnViewData.setOnClickListener(v -> handleViewData());
        btnPredict.setOnClickListener(v -> handlePredictPower());
        btnAdjustPhase.setOnClickListener(v -> handleAdjustPhase());
        btnClearData.setOnClickListener(v -> handleClearData());
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

    //打开文件选择器
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
            Log.e(TAG, "无法打开文件选择器", e);
            Toast.makeText(this, "无法打开文件选择器：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    //处理文件，导入数据库
    private void processFiles(List<Uri> uris, AlertDialog progressDialog)
    {
        // 在后台线程中处理数据
        Map<String, Map<String, List<UserData>>> dateUserGroupedData = new HashMap<>();
        int totalFiles = uris.size();
        int successCount = 0;

        try 
        {
            // 读取所有文件数据
            for (int fileIndex = 0; fileIndex < uris.size(); fileIndex++)
            {
                Uri uri = uris.get(fileIndex);
                if (uri == null) continue;

                // 更新进度对话框
                int currentFileIndex = fileIndex;
                progressDialog.setMessage(String.format("正在处理文件：%d/%d", currentFileIndex + 1, totalFiles));

                // 处理文件
                if (processFile(uri, dateUserGroupedData))
                {
                    successCount++;
                    Log.i(TAG, "成功导入文件：" + uri);
                }
            }

            // 导入数据到数据库
            dbHelper.importBatchData(dateUserGroupedData);

            // 返回主线程更新UI
            int finalSuccessCount = successCount;
            startActivity(new Intent(MainActivity.this, MainActivity.class));
            finish();
        }
        catch (Exception e)
        {
            Log.e(TAG, "处理文件时出错", e);
            startActivity(new Intent(MainActivity.this, MainActivity.class));
            finish();
        }
    }

    private boolean processFile(Uri uri, Map<String, Map<String, List<UserData>>> dateUserGroupedData)
    {
        InputStream inputStream = null;
        BufferedReader reader = null;

        try
        {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null)
            {
                Log.e(TAG, "无法打开文件：" + uri);
                return false;
            }

            // 检查文件大小
            long fileSize = inputStream.available();
            if (fileSize > MAX_FILE_SIZE)
            {
                Log.w(TAG, "文件过大：" + fileSize + " bytes");
                return false;
            }

            // 处理文件内容
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            int lineCount = 0;

            // 跳过标题行
            reader.readLine();

            while ((line = reader.readLine()) != null)
            {
                lineCount++;
                if (line.trim().isEmpty()) continue;

                try
                {
                    String[] data = line.split(",");
                    if (data.length < 9)
                    {
                        Log.w(TAG, "数据格式错误，行 " + lineCount + "：列数不足");
                        continue;
                    }

                    // 验证数据
                    String date = data[0].trim();
                    String userId = data[1].trim();
                    if (date.isEmpty() || userId.isEmpty())
                    {
                        Log.w(TAG, "数据格式错误，行 " + lineCount + "：日期或用户ID为空");
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
                        Log.w(TAG, "数据格式错误，行 " + lineCount + "：电量数据无效");
                        continue;
                    }

                    UserData userData = new UserData();
                    userData.setDate(date);
                    userData.setUserId(userId);
                    userData.setUserName(data[2].trim());
                    userData.setRouteNumber(data[3].trim());
                    userData.setBranchNumber(data[4].trim());
                    userData.setPhase(data[5].trim());
                    userData.setPhaseAPower(Double.parseDouble(data[6].trim()));
                    userData.setPhaseBPower(Double.parseDouble(data[7].trim()));
                    userData.setPhaseCPower(Double.parseDouble(data[8].trim()));

                    dateUserGroupedData
                        .computeIfAbsent(userData.getDate(), k -> new HashMap<>())
                        .computeIfAbsent(userData.getUserId(), k -> new ArrayList<>())
                        .add(userData);
                }
                catch (Exception e)
                {
                    Log.e(TAG, "处理数据行时出错，行 " + lineCount, e);
                }
            }

            return true;
        }
        catch (Exception e)
        {
            Log.e(TAG, "处理文件出错：" + uri, e);
            return false;
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
                Log.e(TAG, "关闭文件流时出错", e);
            }
        }
    }

    //-----------------------------------查看数据-----------------------------------
    private void handleViewData() 
    {
        try 
        {
            // 检查是否有用户数据
            List<String> userIds = dbHelper.getAllUserIds();
            
            if (userIds.isEmpty()) 
            {
                Toast.makeText(this, "请先导入用户数据", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 启动数据查看活动
            Intent intent = new Intent(MainActivity.this, UserDataActivity.class);
            startActivity(intent);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "打开数据查看失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //-----------------------------------预测电量-----------------------------------
    private void handlePredictPower() 
    {
        try 
        {
            // 检查是否有用户数据
            List<String> userIds = dbHelper.getAllUserIds();
            
            if (userIds.isEmpty()) 
            {
                Toast.makeText(this, "请先导入用户数据", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 启动预测电量活动
            Intent intent = new Intent(MainActivity.this, PredictionActivity.class);
            startActivity(intent);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "打开预测电量失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //-----------------------------------相位调整-----------------------------------
    //相位调整
    private void handleAdjustPhase()
    {
        try 
        {
            // 检查是否有用户数据
            List<String> userIds = dbHelper.getAllUserIds();
            
            if (userIds.isEmpty()) 
            {
                Toast.makeText(this, "请先导入用户数据", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 启动相位调整活动
            Intent intent = new Intent(this, PhaseBalanceActivity.class);
            startActivity(intent);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "打开相位调整失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //-----------------------------------清空数据-----------------------------------
    private void handleClearData()
    {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除所有数据吗？此操作不可恢复！")
            .setPositiveButton("确定", (dialog, which) -> {
                try 
                {
                    Log.i(TAG, "开始清空数据");
                    dbHelper.clearAllData();
                    updateChartData();
                    Toast.makeText(this, "所有数据已清空", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "数据清空完成");
                } 
                catch (Exception e) 
                {
                    Log.e(TAG, "清空数据时出错", e);
                    Toast.makeText(this, "清空数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    //-----------------------------------更新图表数据-----------------------------------
    //更新图表数据
    private void updateChartData()
    {
        try
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
                    try
                    {
                        double[] powers = dbHelper.getTotalPowerByDate(dates.get(i));
                        if (powers != null && powers.length >= 3)
                        {
                            entriesA.add(new Entry(i, (float)powers[0]));
                            entriesB.add(new Entry(i, (float)powers[1]));
                            entriesC.add(new Entry(i, (float)powers[2]));
                        }
                        else
                        {
                            // 如果数据无效，添加0值
                            entriesA.add(new Entry(i, 0f));
                            entriesB.add(new Entry(i, 0f));
                            entriesC.add(new Entry(i, 0f));
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        // 发生异常时添加0值
                        entriesA.add(new Entry(i, 0f));
                        entriesB.add(new Entry(i, 0f));
                        entriesC.add(new Entry(i, 0f));
                    }
                }
            }
            else
            {
                // 没有数据时，添加空的日期标签和零值数据点
                for (int i = 6; i >= 0; i--)
                {
                    dates.add(String.format("Day %d", i + 1));
                    entriesA.add(new Entry(6-i, 0f));
                    entriesB.add(new Entry(6-i, 0f));
                    entriesC.add(new Entry(6-i, 0f));
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
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "更新图表时出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() 
    {
        super.onDestroy();
        try 
        {
            if (dbHelper != null) 
            {
                dbHelper.close();
                Log.i(TAG, "数据库连接已关闭");
            }
        } 
        catch (Exception e) 
        {
            Log.e(TAG, "关闭数据库时出错", e);
        }
    }
}