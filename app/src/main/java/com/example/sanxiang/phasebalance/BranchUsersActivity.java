package com.example.sanxiang.phasebalance;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.adapter.BranchUserListAdapter;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.db.DatabaseHelper;
import java.util.ArrayList;
import java.util.List;

public class BranchUsersActivity extends AppCompatActivity 
{
    public static final String EXTRA_ROUTE_NUMBER = "route_number";
    public static final String EXTRA_BRANCH_NUMBER = "branch_number";
    
    private DatabaseHelper dbHelper;
    private RecyclerView rvUsers;
    private TextView tvBranchInfo;
    private EditText etSearch;
    private BranchUserListAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_branch_users);
        
        try 
        {
            String routeNumber = getIntent().getStringExtra(EXTRA_ROUTE_NUMBER);
            String branchNumber = getIntent().getStringExtra(EXTRA_BRANCH_NUMBER);
            
            if (routeNumber == null || branchNumber == null) 
            {
                Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            dbHelper = new DatabaseHelper(this);
            
            initializeViews();
            setupSearch();
            loadUsers(routeNumber, branchNumber);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "初始化失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initializeViews() 
    {
        rvUsers = findViewById(R.id.rvUsers);
        tvBranchInfo = findViewById(R.id.tvBranchInfo);
        etSearch = findViewById(R.id.etSearch);
        
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BranchUserListAdapter(new ArrayList<>());
        rvUsers.setAdapter(adapter);
    }

    private void setupSearch() 
    {
        etSearch.addTextChangedListener(new TextWatcher() 
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) 
            {
                adapter.filter(s.toString());
            }
        });
    }
    
    private void loadUsers(String routeNumber, String branchNumber) 
    {
        try 
        {
            tvBranchInfo.setText(String.format("回路%s支线%s的用户列表", routeNumber, branchNumber));
            
            List<User> users = dbHelper.getUsersByRouteBranch(routeNumber, branchNumber);
            if (users.isEmpty()) 
            {
                Toast.makeText(this, "未找到用户数据", Toast.LENGTH_SHORT).show();
            }
            adapter = new BranchUserListAdapter(users);
            rvUsers.setAdapter(adapter);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "加载用户数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() 
    {
        super.onDestroy();
        if (dbHelper != null) 
        {
            dbHelper.close();
        }
    }
} 