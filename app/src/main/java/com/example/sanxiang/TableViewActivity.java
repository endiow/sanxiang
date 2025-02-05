package com.example.sanxiang;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.content.Intent;

import com.example.sanxiang.db.DatabaseHelper;
import java.util.ArrayList;
import java.util.List;

public class TableViewActivity extends AppCompatActivity
{
    public static final String EXTRA_TABLE_TYPE = "table_type";
    public static final int TYPE_USER_INFO = 1;
    public static final int TYPE_TOTAL_POWER = 2;

    private DatabaseHelper dbHelper;
    private RecyclerView recyclerView;
    private TextView tvTitle;
    private EditText etSearch;
    private List<String> allRows = new ArrayList<>();
    private TableAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table_view);

        dbHelper = new DatabaseHelper(this);
        recyclerView = findViewById(R.id.recyclerView);
        tvTitle = findViewById(R.id.tvTitle);
        etSearch = findViewById(R.id.etSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        int tableType = getIntent().getIntExtra(EXTRA_TABLE_TYPE, TYPE_USER_INFO);
        adapter = new TableAdapter(new ArrayList<>(), tableType);
        recyclerView.setAdapter(adapter);

        if (tableType == TYPE_USER_INFO)
        {
            displayUserInfo();
            setupUserInfoSearch();
        }
        else
        {
            displayTotalPower();
            setupTotalPowerSearch();
        }
    }

    private void setupUserInfoSearch()
    {
        etSearch.setHint("输入用户编号搜索...");
        etSearch.addTextChangedListener(new TextWatcher()
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
            public void afterTextChanged(Editable s)
            {
                filterUserInfo(s.toString());
            }
        });
    }

    private void setupTotalPowerSearch()
    {
        etSearch.setHint("输入日期搜索...");
        etSearch.addTextChangedListener(new TextWatcher()
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
            public void afterTextChanged(Editable s)
            {
                filterTotalPower(s.toString());
            }
        });
    }

    private void filterUserInfo(String query)
    {
        List<String> filteredList = new ArrayList<>();
        for (String row : allRows)
        {
            if (row.toLowerCase().contains(query.toLowerCase()))
            {
                filteredList.add(row);
            }
        }
        adapter.updateData(filteredList);
    }

    private void filterTotalPower(String query)
    {
        List<String> filteredList = new ArrayList<>();
        for (String row : allRows)
        {
            if (row.toLowerCase().contains(query.toLowerCase()))
            {
                filteredList.add(row);
            }
        }
        adapter.updateData(filteredList);
    }

    private void displayUserInfo()
    {
        tvTitle.setText("用户信息表");
        allRows.clear();
        
        List<String> userIds = dbHelper.getAllUserIds();
        for (String userId : userIds)
        {
            String userInfo = dbHelper.getUserInfo(userId);
            if (userInfo != null)
            {
                allRows.add(userInfo);
            }
        }

        adapter.updateData(allRows);
    }

    private void displayTotalPower()
    {
        tvTitle.setText("总电量表");
        allRows.clear();
        
        List<String> dates = dbHelper.getLastNDays(30);
        for (String date : dates)
        {
            double[] powers = dbHelper.getTotalPowerByDate(date);
            if (powers != null)
            {
                String row = String.format(
                    "日期：%s\n" +
                    "A相总量：%.2f\n" +
                    "B相总量：%.2f\n" +
                    "C相总量：%.2f\n" +
                    "三相不平衡度：%.2f%%",
                    date, powers[0], powers[1], powers[2], powers[3]
                );
                allRows.add(row);
            }
        }

        adapter.updateData(allRows);
    }

    private static class TableAdapter extends RecyclerView.Adapter<TableAdapter.ViewHolder>
    {
        private List<String> rows;
        private int tableType;

        public TableAdapter(List<String> rows, int tableType)
        {
            this.rows = rows;
            this.tableType = tableType;
        }

        public void updateData(List<String> newRows)
        {
            this.rows = newRows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_table_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position)
        {
            holder.tvRowContent.setText(rows.get(position));
            
            if (tableType == TYPE_USER_INFO)
            {
                holder.itemView.setOnClickListener(v -> 
                {
                    String content = rows.get(position);
                    String userId = extractUserId(content);
                    if (userId != null)
                    {
                        Intent intent = new Intent(v.getContext(), UserDetailActivity.class);
                        intent.putExtra("userId", userId);
                        v.getContext().startActivity(intent);
                    }
                });
            }
            else
            {
                holder.itemView.setClickable(false);
            }
        }

        private String extractUserId(String content)
        {
            try
            {
                int start = content.indexOf("用户编号：") + 5;
                int end = content.indexOf("\n", start);
                return content.substring(start, end).trim();
            }
            catch (Exception e)
            {
                return null;
            }
        }

        @Override
        public int getItemCount()
        {
            return rows.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder
        {
            TextView tvRowContent;

            ViewHolder(View view)
            {
                super(view);
                tvRowContent = view.findViewById(R.id.tvRowContent);
            }
        }
    }
} 