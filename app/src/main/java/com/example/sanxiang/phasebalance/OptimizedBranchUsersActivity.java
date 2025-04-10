package com.example.sanxiang.phasebalance;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.adapter.OptimizedUserAdapter;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.db.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class OptimizedBranchUsersActivity extends AppCompatActivity 
{
    public static final String EXTRA_ROUTE_NUMBER = "route_number";
    public static final String EXTRA_BRANCH_NUMBER = "branch_number";
    public static final String EXTRA_ADJUSTED_USERS = "adjusted_users";  // 需要调整的用户ID列表
    public static final String EXTRA_NEW_PHASES = "new_phases";  // 优化后的新相位
    public static final String EXTRA_PHASE_MOVES = "phase_moves";  // 相位移动次数
    
    private DatabaseHelper dbHelper;
    private RecyclerView rvUsers;
    private TextView tvBranchInfo;
    private OptimizedUserAdapter adapter;
    private List<User> users;
    private List<Byte> newPhases;
    private List<Byte> phaseMoves;  // 添加移动次数列表
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimized_branch_users);
        
        try 
        {
            String routeNumber = getIntent().getStringExtra(EXTRA_ROUTE_NUMBER);
            String branchNumber = getIntent().getStringExtra(EXTRA_BRANCH_NUMBER);
            ArrayList<String> adjustedUserIds = getIntent().getStringArrayListExtra(EXTRA_ADJUSTED_USERS);
            byte[] optimizedPhases = getIntent().getByteArrayExtra(EXTRA_NEW_PHASES);
            byte[] phaseMoves = getIntent().getByteArrayExtra(EXTRA_PHASE_MOVES);  // 获取移动次数
            
            if (routeNumber == null || branchNumber == null || 
                adjustedUserIds == null || optimizedPhases == null || phaseMoves == null) 
            {
                Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            dbHelper = new DatabaseHelper(this);
            users = new ArrayList<>();
            newPhases = new ArrayList<>();
            this.phaseMoves = new ArrayList<>();  // 初始化移动次数列表
            
            initializeViews();
            loadUsers(routeNumber, branchNumber, adjustedUserIds, optimizedPhases, phaseMoves);
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
        
        adapter = new OptimizedUserAdapter(users, newPhases, phaseMoves, dbHelper);  // 添加移动次数参数
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);
    }
    
    private void loadUsers(String routeNumber, String branchNumber, List<String> adjustedUserIds, 
                         byte[] optimizedPhases, byte[] phaseMoves) 
    {
        try 
        {
            tvBranchInfo.setText(String.format("回路%s支线%s优化结果", routeNumber, branchNumber));
            
            users.clear();
            newPhases.clear();
            this.phaseMoves.clear();
            
            // 添加调试日志
            Log.d("OptimizedBranchUsersActivity", String.format(
                "加载回路%s支线%s的优化用户，需要调整的用户数量: %d", 
                routeNumber, branchNumber, adjustedUserIds.size()
            ));
            
            // 获取该支线的所有用户
            List<User> branchUsers = dbHelper.getUsersByRouteBranch(routeNumber, branchNumber);
            
            // 添加调试日志
            Log.d("OptimizedBranchUsersActivity", String.format(
                "该支线总用户数: %d", branchUsers.size()
            ));
            
            if (!branchUsers.isEmpty()) 
            {
                // 只显示需要调整的用户
                for (User user : branchUsers) 
                {
                    if (adjustedUserIds.contains(user.getUserId())) 
                    {
                        users.add(user);
                        // 从优化结果中获取新相位和移动次数
                        int userIndex = branchUsers.indexOf(user);
                        byte newPhase = optimizedPhases[userIndex];
                        byte moves = phaseMoves[userIndex];
                        newPhases.add(newPhase);
                        this.phaseMoves.add(moves);
                        
                        // 添加调试日志
                        Log.d("OptimizedBranchUsersActivity", String.format(
                            "添加调整用户: %s, 原相位: %s, 新相位: %d, 移动次数: %d",
                            user.getUserId(), user.getCurrentPhase(), newPhase, moves
                        ));
                    }
                }
                
                // 添加调试日志
                Log.d("OptimizedBranchUsersActivity", String.format(
                    "最终显示的调整用户数量: %d", users.size()
                ));
                
                // 更新用户列表
                adapter.notifyDataSetChanged();
            }
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