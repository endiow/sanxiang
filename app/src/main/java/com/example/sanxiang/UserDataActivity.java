package com.example.sanxiang;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;
import com.example.sanxiang.util.CsvHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class UserDataActivity extends AppCompatActivity
{
    // 数据库帮助类
    private DatabaseHelper dbHelper;
    // 日期显示文本框
    private TextView tvDate;
    // 总电量显示文本框
    private TextView tvTotalPower;
    // 用户数据列表视图
    private RecyclerView recyclerView;
    // 列表适配器
    private UserDataAdapter adapter;
    // 当前显示的日期
    private String currentDate;

    /**
     * Activity创建时的初始化
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_data);

        // 初始化数据库
        dbHelper = new DatabaseHelper(this);
        
        // 初始化视图组件
        tvDate = findViewById(R.id.tvDate);
        tvTotalPower = findViewById(R.id.tvTotalPower);
        recyclerView = findViewById(R.id.recyclerView);
        Button btnPrevDay = findViewById(R.id.btnPrevDay);
        Button btnNextDay = findViewById(R.id.btnNextDay);
        EditText etSearch = findViewById(R.id.etSearch);
        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnOldData = findViewById(R.id.btnOldData);

        // 设置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserDataAdapter();
        recyclerView.setAdapter(adapter);

        // 默认加载最新日期的数据
        currentDate = dbHelper.getLatestDate();
        tvDate.setText(currentDate);
        loadData(currentDate);

        // 设置按钮点击事件
        btnPrevDay.setOnClickListener(v -> loadPrevDay());
        btnNextDay.setOnClickListener(v -> loadNextDay());
        btnSearch.setOnClickListener(v -> 
        {
            String userId = etSearch.getText().toString();
            adapter.filter(userId);
        });
        btnOldData.setOnClickListener(v -> showOldDataDialog());
        
        // 设置日期输入完成事件
        tvDate.setOnFocusChangeListener((v, hasFocus) -> 
        {
            if (!hasFocus)
            {
                String searchDate = tvDate.getText().toString().trim();
                if (!searchDate.isEmpty() && isValidDateFormat(searchDate))
                {
                    loadData(searchDate);
                }
            }
        });
    }

    /**
     * 验证日期格式是否正确
     */
    private boolean isValidDateFormat(String date)
    {
        // 使用正则表达式验证日期格式
        String regex = "\\d{4}-\\d{2}-\\d{2}";
        if (!date.matches(regex))
        {
            return false;
        }
        
        try
        {
            String[] parts = date.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            
            // 简单的日期验证
            return year >= 2000 && year <= 2100 
                && month >= 1 && month <= 12 
                && day >= 1 && day <= 31;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 加载指定日期的数据
     */
    private void loadData(String date)
    {
        if (date != null)
        {
            currentDate = date;
            tvDate.setText(date);
            
            // 获取并显示总电量
            double[] totalPower = dbHelper.getTotalPowerByDate(date);
            if (totalPower != null)
            {
                displayTotalPower(totalPower);
                // 获取并显示用户数据列表
                List<UserData> userDataList = dbHelper.getUserDataByDate(date);
                adapter.setData(userDataList);
            }
            else
            {
                Toast.makeText(this, "该日期没有数据", Toast.LENGTH_SHORT).show();
            }
        }
        else
        {
            Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示总电量和不平衡度信息
     */
    private void displayTotalPower(double[] totalPower)
    {
        // 使用数据库中存储的平衡度值
        double unbalanceRate = totalPower[3];  // 第四个元素是平衡度
        String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);

        // 格式化显示信息
        String powerInfo = String.format(
            "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)",
            totalPower[0], totalPower[1], totalPower[2], unbalanceRate, status
        );
        
        SpannableString spannableString = new SpannableString(powerInfo);
        int start = powerInfo.indexOf("三相不平衡度");
        int end = start + 5;
        
        // 设置文字样式和点击事件
        ClickableSpan clickableSpan = new ClickableSpan()
        {
            @Override
            public void onClick(@NonNull View view)
            {
                UnbalanceCalculator.showCalculationProcess(
                    UserDataActivity.this,
                    totalPower[0], totalPower[1], totalPower[2]
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
        
        tvTotalPower.setText(spannableString);
        tvTotalPower.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * 加载前一天的数据
     */
    private void loadPrevDay()
    {
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

    /**
     * 加载后一天的数据
     */
    private void loadNextDay()
    {
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

    /**
     * 显示旧数据操作对话框
     */
    private void showOldDataDialog()
    {
        String[] options = {"查看旧数据", "导出旧数据"};
        
        new AlertDialog.Builder(this)
            .setTitle("旧数据操作")
            .setItems(options, (dialog, which) -> 
            {
                if (which == 0)
                {
                    // 查看旧数据
                    showOldData();
                }
                else
                {
                    // 导出旧数据
                    exportOldData();
                }
            })
            .show();
    }

    /**
     * 显示旧数据
     */
    private void showOldData()
    {
        List<UserData> oldData = CsvHelper.readUserDataFromCsv(this);
        if (oldData.isEmpty())
        {
            Toast.makeText(this, "没有旧数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建一个临时适配器显示旧数据
        UserDataAdapter oldDataAdapter = new UserDataAdapter();
        oldDataAdapter.setData(oldData);

        // 保存当前适配器
        RecyclerView.Adapter<?> currentAdapter = recyclerView.getAdapter();
        
        // 设置旧数据适配器
        recyclerView.setAdapter(oldDataAdapter);

        // 显示提示和返回按钮
        new AlertDialog.Builder(this)
            .setTitle("查看旧数据")
            .setMessage("当前显示的是历史数据")
            .setPositiveButton("返回当前数据", (dialog, which) -> 
            {
                // 恢复原来的适配器
                recyclerView.setAdapter(currentAdapter);
            })
            .setCancelable(false)
            .show();
    }

    /**
     * 导出旧数据
     */
    private void exportOldData()
    {
        // 选择导出目录
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "old_data");

        startActivityForResult(intent, EXPORT_REQUEST_CODE);
    }

    private static final int EXPORT_REQUEST_CODE = 123;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_REQUEST_CODE && resultCode == RESULT_OK)
        {
            Uri uri = data.getData();
            if (uri != null)
            {
                try
                {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
                    if (pfd != null)
                    {
                        FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                        File exportDir = new File(getCacheDir(), "export");
                        exportDir.mkdirs();
                        
                        // 导出数据到临时目录
                        CsvHelper.exportOldData(this, exportDir);
                        
                        // 将临时目录中的文件写入到选择的位置
                        File[] files = exportDir.listFiles();
                        if (files != null)
                        {
                            for (File file : files)
                            {
                                FileInputStream fis = new FileInputStream(file);
                                byte[] buffer = new byte[1024];
                                int length;
                                while ((length = fis.read(buffer)) > 0)
                                {
                                    fos.write(buffer, 0, length);
                                }
                                fis.close();
                            }
                        }
                        
                        fos.close();
                        pfd.close();
                        
                        // 清理临时文件
                        for (File file : files)
                        {
                            file.delete();
                        }
                        exportDir.delete();
                        
                        Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
} 