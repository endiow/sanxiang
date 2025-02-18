package com.example.sanxiang;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.algorithm.*;
import com.example.sanxiang.data.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.*;

public class PhaseBalanceActivity extends AppCompatActivity 
{
    private DatabaseHelper dbHelper;
    private RecyclerView rvBranchGroups;
    private BranchGroupAdapter adapter;
    private List<BranchGroup> branchGroups;
    private Button btnOptimize;
    private TextView tvResult;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phase_balance);
        
        dbHelper = new DatabaseHelper(this);
        branchGroups = new ArrayList<>();
        
        initializeViews();
        setupListeners();
    }
    
    private void initializeViews() 
    {
        rvBranchGroups = findViewById(R.id.rvBranchGroups);
        btnOptimize = findViewById(R.id.btnOptimize);
        tvResult = findViewById(R.id.tvResult);
        
        adapter = new BranchGroupAdapter(branchGroups);
        rvBranchGroups.setLayoutManager(new LinearLayoutManager(this));
        rvBranchGroups.setAdapter(adapter);
        
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> showAddBranchGroupDialog());
    }
    
    private void setupListeners() 
    {
        btnOptimize.setOnClickListener(v -> optimizePhases());
    }
    
    private void showAddBranchGroupDialog() 
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_branch_group, null);
        
        EditText etRouteNumber = dialogView.findViewById(R.id.etRouteNumber);
        EditText etBranchNumber = dialogView.findViewById(R.id.etBranchNumber);
        
        builder.setView(dialogView)
               .setTitle("添加支线组")
               .setPositiveButton("确定", (dialog, which) -> 
               {
                   String routeNumber = etRouteNumber.getText().toString();
                   String branchNumber = etBranchNumber.getText().toString();
                   
                   if (!routeNumber.isEmpty() && !branchNumber.isEmpty()) 
                   {
                       BranchGroup group = new BranchGroup(routeNumber, branchNumber);
                       branchGroups.add(group);
                       adapter.notifyDataSetChanged();
                       
                       // 显示用户选择对话框
                       showUserSelectionDialog(group);
                   }
               })
               .setNegativeButton("取消", null)
               .show();
    }
    
    private void showUserSelectionDialog(BranchGroup group) 
    {
        // 获取该回路支线的所有用户
        List<User> users = getUsersByRouteBranch(group.getRouteNumber(), group.getBranchNumber());
        String[] userNames = users.stream()
                                .map(user -> user.getUserName() + "(" + user.getUserId() + ")")
                                .toArray(String[]::new);
        boolean[] checkedItems = new boolean[userNames.length];
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择用户")
               .setMultiChoiceItems(userNames, checkedItems, (dialog, which, isChecked) -> 
               {
                   if (isChecked) 
                   {
                       group.addUser(users.get(which).getUserId());
                   }
               })
               .setPositiveButton("确定", (dialog, which) -> adapter.notifyDataSetChanged())
               .show();
    }
    
    private List<User> getUsersByRouteBranch(String routeNumber, String branchNumber) 
    {
        try 
        {
            // 从数据库获取用户数据
            return dbHelper.getUsersByRouteBranch(routeNumber, branchNumber);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "获取用户数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return new ArrayList<>();
        }
    }
    
    private List<User> getAllUsers() 
    {
        List<User> users = new ArrayList<>();
        try 
        {
            // 获取所有用户ID
            List<String> userIds = dbHelper.getAllUserIds();
            
            // 遍历每个用户ID，获取最近一天的数据
            for (String userId : userIds) 
            {
                List<UserData> userDataList = dbHelper.getUserLastNDaysData(userId, 1);
                if (!userDataList.isEmpty()) 
                {
                    UserData userData = userDataList.get(0);
                    
                    // 计算总功率
                    double totalPower = userData.getPhaseAPower() + 
                                        userData.getPhaseBPower() + 
                                        userData.getPhaseCPower();
                    
                    if (totalPower > 0) 
                    {
                        // 确定当前相位
                        byte currentPhase = 0;
                        if (userData.getPhaseAPower() > 0) currentPhase = 1;
                        else if (userData.getPhaseBPower() > 0) currentPhase = 2;
                        else if (userData.getPhaseCPower() > 0) currentPhase = 3;
                        
                        // 判断是否为动力相（三相都有电量）
                        boolean isPowerPhase = userData.getPhaseAPower() > 0 && 
                                               userData.getPhaseBPower() > 0 && 
                                               userData.getPhaseCPower() > 0;

                        User user = new User(
                            userId,
                            userData.getUserName(),
                            userData.getRouteNumber(),
                            userData.getBranchNumber(),
                            totalPower,
                            currentPhase,
                            isPowerPhase
                        );
                        users.add(user);
                        
                        Log.d("PhaseBalanceActivity", String.format(
                            "读取到用户 - ID: %s, 名称: %s, 功率: %.2f, 相位: %d, 是否动力相: %b",
                            userId, userData.getUserName(), totalPower, currentPhase, isPowerPhase
                        ));
                    }
                }
            }
            
            Log.d("PhaseBalanceActivity", "总共返回 " + users.size() + " 个用户");
        } 
        catch (Exception e) 
        {
            Log.e("PhaseBalanceActivity", "获取用户数据时出错", e);
            e.printStackTrace();
        }
        
        return users;
    }
    
    private void optimizePhases() 
    {
        try 
        {
            // 获取所有用户数据
            List<User> allUsers = getAllUsers();
            
            if (allUsers.isEmpty()) 
            {
                Log.d("PhaseBalanceActivity", "没有找到可优化的用户数据");
                Toast.makeText(this, "没有可优化的用户数据，请确保已导入用户数据且存在当天的用电量记录", Toast.LENGTH_LONG).show();
                return;
            }

            // 创建并执行遗传算法
            try 
            {
                PhaseBalancer balancer = new PhaseBalancer(allUsers, branchGroups.isEmpty() ? null : branchGroups);
                PhaseBalancer.Solution solution = balancer.optimize();
                
                if (solution == null) 
                {
                    Toast.makeText(this, "优化失败：未能找到有效解决方案", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 显示优化结果
                showOptimizationResult(allUsers, solution);
            } 
            catch (Exception e) 
            {
                Log.e("PhaseBalanceActivity", "优化过程出错", e);
                e.printStackTrace();
                Toast.makeText(this, "优化过程出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } 
        catch (Exception e) 
        {
            Log.e("PhaseBalanceActivity", "优化相位失败", e);
            e.printStackTrace();
            Toast.makeText(this, "优化相位失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showOptimizationResult(List<User> users, PhaseBalancer.Solution solution) 
    {
        try 
        {
            StringBuilder result = new StringBuilder();
            result.append("优化结果：\n\n");
            
            // 计算每相总电量
            double[] phasePowers = new double[3];
            int changedUsers = 0;
            
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                byte newPhase = solution.getPhase(i);
                
                if (newPhase > 0) 
                {
                    phasePowers[newPhase - 1] += user.getPower();
                }
                
                if (newPhase != user.getCurrentPhase()) 
                {
                    changedUsers++;
                    result.append(String.format("%s: %d -> %d\n", 
                        user.getUserName(), user.getCurrentPhase(), newPhase));
                }
            }
            
            result.append(String.format("\n调整用户数：%d\n", changedUsers));
            result.append(String.format("优化后三相电量：\nA相：%.2f\nB相：%.2f\nC相：%.2f\n",
                phasePowers[0], phasePowers[1], phasePowers[2]));
            
            double maxPower = Math.max(Math.max(phasePowers[0], phasePowers[1]), phasePowers[2]);
            double minPower = Math.min(Math.min(phasePowers[0], phasePowers[1]), phasePowers[2]);
            
            if (maxPower > 0) 
            {
                double unbalanceRate = ((maxPower - minPower) / maxPower) * 100;
                result.append(String.format("三相不平衡度：%.2f%%", unbalanceRate));
            } 
            else 
            {
                result.append("三相不平衡度：0.00%");
            }
            
            tvResult.setText(result.toString());
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "显示优化结果失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 