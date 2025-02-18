package com.example.sanxiang.userdata;

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

import com.example.sanxiang.R;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.util.DateValidator;

import java.util.ArrayList;
import java.util.List;

public class TableViewActivity extends AppCompatActivity
{
    public static final String EXTRA_TABLE_TYPE = "table_type";
    public static final int TYPE_USER_INFO = 1;
    public static final int TYPE_TOTAL_POWER = 2;

    private DatabaseHelper dbHelper;    // 数据库操作类
    private RecyclerView recyclerView;  // 列表视图
    private TextView tvTitle;         // 标题
    private EditText etSearch;         // 搜索框

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
        etSearch.setHint("输入用户编号搜索");
        etSearch.setFilters(new android.text.InputFilter[] 
        {
            new android.text.InputFilter.LengthFilter(10)
        });
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
        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.setHint("输入日期搜索...");
        etSearch.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                String searchQuery = s.toString().trim();
                if (searchQuery.isEmpty())
                {
                    adapter.updateData(allRows);
                    return;
                }

                List<String> filteredList = new ArrayList<>();
                try
                {
                    // 不管日期格式是否完整，都尝试进行匹配
                    for (String row : allRows)
                    {
                        // 提取行中的日期部分
                        int dateStart = row.indexOf("日期：") + 3;
                        int dateEnd = row.indexOf("\n", dateStart);
                        if (dateStart >= 3 && dateEnd > dateStart)
                        {
                            String rowDate = row.substring(dateStart, dateEnd).trim();
                            
                            // 首先尝试精确匹配（如果输入的是有效日期）
                            if (DateValidator.isValidDateFormat(searchQuery, null))
                            {
                                String standardizedSearchDate = DateValidator.standardizeDate(searchQuery);
                                String standardizedRowDate = DateValidator.standardizeDate(rowDate);
                                
                                if (standardizedSearchDate != null && 
                                    standardizedRowDate != null && 
                                    standardizedRowDate.equals(standardizedSearchDate))
                                {
                                    filteredList.add(row);
                                    continue;
                                }
                            }
                            
                            // 如果精确匹配失败，进行模糊匹配
                            // 将日期拆分为年月日
                            String[] rowParts = rowDate.split("[-/.]");
                            if (rowParts.length == 3)
                            {
                                try
                                {
                                    // 处理年份
                                    String year = rowParts[0];
                                    if (cleanNumber(year).contains(cleanNumber(searchQuery)))
                                    {
                                        filteredList.add(row);
                                        continue;
                                    }
                                    
                                    // 处理月份
                                    String month = rowParts[1];
                                    if (searchQuery.length() <= 2)
                                    {
                                        int searchMonth = Integer.parseInt(searchQuery);
                                        int rowMonth = Integer.parseInt(month);
                                        if (searchMonth == rowMonth)
                                        {
                                            filteredList.add(row);
                                            continue;
                                        }
                                    }
                                    
                                    // 处理日期
                                    String day = rowParts[2];
                                    if (searchQuery.length() <= 2)
                                    {
                                        int searchDay = Integer.parseInt(searchQuery);
                                        int rowDay = Integer.parseInt(day);
                                        if (searchDay == rowDay)
                                        {
                                            filteredList.add(row);
                                            continue;
                                        }
                                    }
                                    
                                    // 处理组合日期（如2024-2）
                                    String[] searchParts = searchQuery.split("[-/.]");
                                    if (searchParts.length == 2)
                                    {
                                        int searchYear = Integer.parseInt(searchParts[0].length() == 2 ? "20" + searchParts[0] : searchParts[0]);
                                        int searchMonth = Integer.parseInt(searchParts[1]);
                                        int rowYear = Integer.parseInt(year.length() == 2 ? "20" + year : year);
                                        int rowMonth = Integer.parseInt(month);
                                        if (searchYear == rowYear && searchMonth == rowMonth)
                                        {
                                            filteredList.add(row);
                                            continue;
                                        }
                                    }
                                }
                                catch (NumberFormatException e)
                                {
                                    // 如果数字解析失败，继续下一次循环
                                    continue;
                                }
                            }
                        }
                    }
                    adapter.updateData(filteredList);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    adapter.updateData(allRows);
                }
            }
        });
    }

    // 清理数字字符串，移除前导零
    private String cleanNumber(String number)
    {
        try
        {
            return String.valueOf(Integer.parseInt(number));
        }
        catch (NumberFormatException e)
        {
            return number;
        }
    }

    // 用户信息搜索
    private void filterUserInfo(String searchId)
    {
        if (searchId == null || searchId.trim().isEmpty())
        {
            adapter.updateData(allRows);
            return;
        }

        try
        {
            List<String> filteredList = new ArrayList<>();
            String trimmedSearchId = searchId.trim();
            
            for (String row : allRows)
            {
                // 提取用户ID
                int start = row.indexOf("用户编号：") + 5;
                int end = row.indexOf("\n", start);
                if (start >= 5 && end > start)
                {
                    String userId = row.substring(start, end).trim();
                    
                    // 确保用户ID长度为10位
                    if (userId.length() != 10) continue;
                    
                    // 检查每一位是否匹配
                    boolean matches = true;
                    for (int i = 0; i < trimmedSearchId.length() && i < 10; i++)
                    {
                        if (trimmedSearchId.charAt(i) != '_' && // 使用_表示任意字符
                            trimmedSearchId.charAt(i) != userId.charAt(i))
                        {
                            matches = false;
                            break;
                        }
                    }
                    
                    if (matches)
                    {
                        filteredList.add(row);
                    }
                }
            }
            adapter.updateData(filteredList);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            adapter.updateData(allRows);
        }
    }

    // 总电量搜索
    private void filterTotalPower(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            adapter.updateData(allRows);
            return;
        }

        try
        {
            List<String> filteredList = new ArrayList<>();
            String searchQuery = query.trim();
            
            // 如果输入的是完整日期，尝试标准化格式
            if (searchQuery.length() >= 6)  // 至少包含年月日
            {
                try
                {
                    if (DateValidator.isValidDateFormat(searchQuery, null))
                    {
                        // 将日期转换为标准格式
                        String[] parts = searchQuery.split("[-/.]");
                        String standardDate = String.format("%04d-%02d-%02d",
                            parts[0].length() == 2 ? Integer.parseInt("20" + parts[0]) : Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]));
                        searchQuery = standardDate;
                    }
                }
                catch (Exception e)
                {
                    // 如果转换失败，使用原始输入继续搜索
                }
            }

            // 搜索包含输入日期的记录
            for (String row : allRows)
            {
                // 提取行中的日期部分
                int dateStart = row.indexOf("日期：") + 3;
                int dateEnd = row.indexOf("\n", dateStart);
                if (dateStart >= 3 && dateEnd > dateStart)
                {
                    String rowDate = row.substring(dateStart, dateEnd).trim();
                    
                    // 尝试将行中的日期也标准化
                    try
                    {
                        if (DateValidator.isValidDateFormat(rowDate, null))
                        {
                            String[] parts = rowDate.split("[-/.]");
                            String standardRowDate = String.format("%04d-%02d-%02d",
                                parts[0].length() == 2 ? Integer.parseInt("20" + parts[0]) : Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]));
                            rowDate = standardRowDate;
                        }
                    }
                    catch (Exception e)
                    {
                        // 如果转换失败，使用原始日期继续比较
                    }

                    // 如果标准化后的日期匹配，或者原始输入包含在日期中
                    if (rowDate.contains(searchQuery) || searchQuery.contains(rowDate))
                    {
                        filteredList.add(row);
                    }
                }
            }
            adapter.updateData(filteredList);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            // 如果发生错误，显示所有数据
            adapter.updateData(allRows);
        }
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table_row, parent, false);
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