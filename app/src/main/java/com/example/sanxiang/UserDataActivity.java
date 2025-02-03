package com.example.sanxiang;

import android.graphics.Color;
import android.graphics.Typeface;
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

import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;

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
} 