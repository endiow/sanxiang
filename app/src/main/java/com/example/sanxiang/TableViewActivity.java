package com.example.sanxiang;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.sanxiang.db.DatabaseHelper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TableViewActivity extends AppCompatActivity
{
    public static final String EXTRA_TABLE_TYPE = "table_type";
    public static final int TYPE_USER_INFO = 1;
    public static final int TYPE_TOTAL_POWER = 2;

    private DatabaseHelper dbHelper;
    private TextView tvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table_view);

        dbHelper = new DatabaseHelper(this);
        tvContent = findViewById(R.id.tvContent);

        int tableType = getIntent().getIntExtra(EXTRA_TABLE_TYPE, TYPE_USER_INFO);
        
        if (tableType == TYPE_USER_INFO)
        {
            setTitle("用户信息表");
            displayUserInfo();
        }
        else
        {
            setTitle("总电量表");
            displayTotalPower();
        }
    }

    private void displayUserInfo()
    {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        StringBuilder content = new StringBuilder();
        content.append(String.format("%-15s %-15s %-15s %-15s\n",
            "用户编号", "用户名称", "回路编号", "线路名称"));
        content.append("------------------------------------------------\n");

        Cursor cursor = db.query("user_info", null, null, null, null, null, "user_id ASC");
        while (cursor.moveToNext())
        {
            String userId = cursor.getString(cursor.getColumnIndex("user_id"));
            String userName = cursor.getString(cursor.getColumnIndex("user_name"));
            String routeNumber = cursor.getString(cursor.getColumnIndex("route_number"));
            String routeName = cursor.getString(cursor.getColumnIndex("route_name"));

            content.append(String.format("%-15s %-15s %-15s %-15s\n",
                userId, userName, routeNumber, routeName));
        }
        cursor.close();

        tvContent.setText(content.toString());
    }

    private void displayTotalPower()
    {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        StringBuilder content = new StringBuilder();
        content.append(String.format("%-12s %-10s %-10s %-10s %-10s\n",
            "日期", "A相总量", "B相总量", "C相总量", "不平衡度"));
        content.append("------------------------------------------------\n");

        Cursor cursor = db.query("total_power", null, null, null, null, null, "date DESC");
        while (cursor.moveToNext())
        {
            String date = cursor.getString(cursor.getColumnIndex("date"));
            double totalA = cursor.getDouble(cursor.getColumnIndex("total_phase_a"));
            double totalB = cursor.getDouble(cursor.getColumnIndex("total_phase_b"));
            double totalC = cursor.getDouble(cursor.getColumnIndex("total_phase_c"));
            double unbalanceRate = cursor.getDouble(cursor.getColumnIndex("unbalance_rate"));

            content.append(String.format("%-12s %-10.2f %-10.2f %-10.2f %-10.2f%%\n",
                date, totalA, totalB, totalC, unbalanceRate));
        }
        cursor.close();

        tvContent.setText(content.toString());
    }
} 