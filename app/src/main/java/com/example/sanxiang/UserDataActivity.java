package com.example.sanxiang;

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

import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

// 用户数据显示界面
public class UserDataActivity extends AppCompatActivity
{
    private DatabaseHelper dbHelper;      // 数据库操作类
    private TextView tvDate;             // 日期显示
    private TextView tvTotalPower;       // 总电量显示
    private RecyclerView recyclerView;   // 用户列表
    private UserDataAdapter adapter;     // 列表适配器
    private String currentDate;          // 当前显示的日期

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_data);

        // 初始化数据库和视图
        dbHelper = new DatabaseHelper(this);
        initViews();
        setupListeners();

        // 加载最新日期的数据
        currentDate = dbHelper.getLatestDate();
        tvDate.setText(currentDate);
        loadData(currentDate);
    }

    // 初始化视图组件
    private void initViews()
    {
        tvDate = findViewById(R.id.tvDate);
        tvTotalPower = findViewById(R.id.tvTotalPower);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserDataAdapter();
        recyclerView.setAdapter(adapter);
    }

    // 设置监听器
    private void setupListeners()
    {
        Button btnPrevDay = findViewById(R.id.btnPrevDay);
        Button btnNextDay = findViewById(R.id.btnNextDay);
        EditText etSearch = findViewById(R.id.etSearch);
        Button btnSearch = findViewById(R.id.btnSearch);
        
        // 设置日期导航按钮的点击事件
        btnPrevDay.setOnClickListener(v -> loadPrevDay());
        btnNextDay.setOnClickListener(v -> loadNextDay());
        
        // 设置搜索按钮的点击事件
        btnSearch.setOnClickListener(v -> 
        {
            String userId = etSearch.getText().toString();
            adapter.filter(userId);
        });

        // 设置浮动按钮的点击事件
        FloatingActionButton fabUserInfo = findViewById(R.id.fabUserInfo);
        FloatingActionButton fabTotalPower = findViewById(R.id.fabTotalPower);

        fabUserInfo.setOnClickListener(v -> 
        {
            Intent intent = new Intent(this, TableViewActivity.class);
            intent.putExtra(TableViewActivity.EXTRA_TABLE_TYPE, TableViewActivity.TYPE_USER_INFO);
            startActivity(intent);
        });

        fabTotalPower.setOnClickListener(v -> 
        {
            Intent intent = new Intent(this, TableViewActivity.class);
            intent.putExtra(TableViewActivity.EXTRA_TABLE_TYPE, TableViewActivity.TYPE_TOTAL_POWER);
            startActivity(intent);
        });

        // 日期输入完成监听
        tvDate.setOnEditorActionListener((v, actionId, event) -> 
        {
            // 当用户点击软键盘上的"完成"、"下一步"或"回车"键时触发
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER))
            {
                String searchDate = tvDate.getText().toString().trim();
                if (!searchDate.isEmpty() && isValidDateFormat(searchDate))
                {
                    loadData(searchDate);
                }
                return true;
            }
            return false;
        });

        // 日期输入框失去焦点监听
        tvDate.setOnFocusChangeListener((v, hasFocus) -> 
        {
            // 当输入框失去焦点时触发（比如用户点击了界面其他地方）
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

    // 验证日期格式
    private boolean isValidDateFormat(String date)
    {
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
            
            return year >= 2000 && year <= 2100 
                && month >= 1 && month <= 12 
                && day >= 1 && day <= 31;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    // 加载指定日期的数据
    private void loadData(String date)
    {
        if (date != null)
        {
            currentDate = date;
            tvDate.setText(date);
            
            // 获取并显示总电量和用户数据
            double[] totalPower = dbHelper.getTotalPowerByDate(date);
            if (totalPower != null)
            {
                displayTotalPower(totalPower);
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

    // 显示总电量和不平衡度信息
    private void displayTotalPower(double[] totalPower)
    {
        double unbalanceRate = totalPower[3];
        String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);

        // 格式化显示文本
        String powerInfo = String.format(
            "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)",
            totalPower[0], totalPower[1], totalPower[2], unbalanceRate, status
        );
        
        // 设置不平衡度可点击
        SpannableString spannableString = new SpannableString(powerInfo);
        int start = powerInfo.indexOf("三相不平衡度");
        int end = start + 6;
        
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
                ds.setColor(Color.rgb(51, 102, 153));
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
            }
        };
        
        spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvTotalPower.setText(spannableString);
        tvTotalPower.setMovementMethod(LinkMovementMethod.getInstance());
    }

    // 加载前一天数据
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

    // 加载后一天数据
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