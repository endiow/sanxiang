package com.example.sanxiang.userdata;

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

import com.example.sanxiang.R;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.userdata.adapter.UserDataAdapter;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.UnbalanceCalculator;
import com.example.sanxiang.util.DateValidator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

// 用户数据显示界面
public class UserDataActivity extends AppCompatActivity
{
    private DatabaseHelper dbHelper;      // 数据库操作类
    private EditText etDate;             // 日期输入框
    private TextView tvTotalPower;       // 总电量显示
    private RecyclerView recyclerView;   // 用户列表
    private UserDataAdapter adapter;     // 列表适配器
    private String currentDate;          // 当前显示的日期

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
        // 日期输入框
        etDate = findViewById(R.id.etDate);
        // 总电量显示
        tvTotalPower = findViewById(R.id.tvTotalPower);
        // 用户列表
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
            
            // 获取并显示总电量和用户数据
            double[] totalPower = dbHelper.getTotalPowerByDate(date);
            List<UserData> userDataList = dbHelper.getUserDataByDate(date);
            
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

            // 格式化显示文本
            String powerInfo = String.format(
                "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)",
                totalPower[0], totalPower[1], totalPower[2], unbalanceRate, status
            );
            
            // 设置不平衡度可点击
            SpannableString spannableString = new SpannableString(powerInfo);
            int start = powerInfo.indexOf("三相不平衡度");
            if (start >= 0)
            {
                int end = start + 6;
                
                // 设置不平衡度点击显示计算过程
                ClickableSpan clickableSpan = new ClickableSpan()
                {
                    @Override
                    public void onClick(@NonNull View view)
                    {
                        try
                        {
                            UnbalanceCalculator.showCalculationProcess(
                                UserDataActivity.this,
                                totalPower[0], totalPower[1], totalPower[2]
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
                
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvTotalPower.setText(spannableString);
                tvTotalPower.setMovementMethod(LinkMovementMethod.getInstance());
            }
            else
            {
                tvTotalPower.setText(powerInfo);
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