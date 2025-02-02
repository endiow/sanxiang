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
    private DatabaseHelper dbHelper;
    private TextView tvDate;
    private TextView tvTotalPower;
    private RecyclerView recyclerView;
    private UserDataAdapter adapter;
    private String currentDate;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_data);

        dbHelper = new DatabaseHelper(this);
        
        tvDate = findViewById(R.id.tvDate);
        tvTotalPower = findViewById(R.id.tvTotalPower);
        recyclerView = findViewById(R.id.recyclerView);
        Button btnPrevDay = findViewById(R.id.btnPrevDay);
        Button btnNextDay = findViewById(R.id.btnNextDay);
        EditText etSearch = findViewById(R.id.etSearch);
        Button btnSearch = findViewById(R.id.btnSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserDataAdapter();
        recyclerView.setAdapter(adapter);

        currentDate = dbHelper.getLatestDate();
        loadData(currentDate);

        btnPrevDay.setOnClickListener(v -> loadPrevDay());
        btnNextDay.setOnClickListener(v -> loadNextDay());
        
        btnSearch.setOnClickListener(v -> {
            String userId = etSearch.getText().toString();
            adapter.filter(userId);
        });
    }

    private void loadData(String date)
    {
        if (date != null)
        {
            currentDate = date;
            tvDate.setText(date);
            
            double[] totalPower = dbHelper.getTotalPowerByDate(date);
            displayTotalPower(totalPower);
            
            List<UserData> userDataList = dbHelper.getUserDataByDate(date);
            adapter.setData(userDataList);
        }
        else
        {
            Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayTotalPower(double[] totalPower)
    {
        double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(
            totalPower[0], totalPower[1], totalPower[2]);
        String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);

        String powerInfo = String.format(
            "三相总电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n三相不平衡度：%.2f%% (%s)",
            totalPower[0], totalPower[1], totalPower[2], unbalanceRate, status
        );
        
        SpannableString spannableString = new SpannableString(powerInfo);
        int start = powerInfo.indexOf("三相不平衡度");
        int end = start + 5;
        
        // 设置文字样式和点击事件
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                UnbalanceCalculator.showCalculationProcess(
                    UserDataActivity.this,
                    totalPower[0], totalPower[1], totalPower[2]
                );
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(Color.rgb(51, 102, 153));  // 蓝色
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);  // 粗体
            }
        };
        
        spannableString.setSpan(clickableSpan, start, end, 
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        tvTotalPower.setText(spannableString);
        tvTotalPower.setMovementMethod(LinkMovementMethod.getInstance());
    }

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