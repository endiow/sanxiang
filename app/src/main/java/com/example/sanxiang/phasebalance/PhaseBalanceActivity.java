package com.example.sanxiang.phasebalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.adapter.BranchGroupAdapter;
import com.example.sanxiang.phasebalance.algorithm.*;
import com.example.sanxiang.phasebalance.model.*;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.db.DatabaseHelper;

import java.util.*;

public class PhaseBalanceActivity extends AppCompatActivity 
{
    private DatabaseHelper dbHelper;
    private RecyclerView rvBranchGroups;
    private RecyclerView rvOptimizedGroups;
    private BranchGroupAdapter adapter;
    private BranchGroupAdapter optimizedAdapter;
    private List<BranchGroup> branchGroups;
    private List<BranchGroup> optimizedGroups;
    private Button btnOptimize;
    private TextView tvResultStats;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabDelete;
    private TextView tvSelectedCount;
    private CardView cardDelete;
    private Button btnDelete;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phase_balance);
        
        dbHelper = new DatabaseHelper(this);
        branchGroups = new ArrayList<>();
        optimizedGroups = new ArrayList<>();
        
        initializeViews();
        setupListeners();
        loadBranchGroups();
    }
    
    private void initializeViews() 
    {
        rvBranchGroups = findViewById(R.id.rvBranchGroups);
        rvOptimizedGroups = findViewById(R.id.rvOptimizedGroups);
        btnOptimize = findViewById(R.id.btnOptimize);
        tvResultStats = findViewById(R.id.tvResultStats);
        fabAdd = findViewById(R.id.fabAdd);
        fabDelete = findViewById(R.id.fabDelete);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        cardDelete = findViewById(R.id.cardDelete);
        btnDelete = findViewById(R.id.btnDelete);
        
        adapter = new BranchGroupAdapter(branchGroups, group -> {
            // 处理支线组点击事件
            if (!adapter.isSelectionMode()) 
            {
                Intent intent = new Intent(this, BranchUsersActivity.class);
                intent.putExtra(BranchUsersActivity.EXTRA_ROUTE_NUMBER, group.getRouteNumber());
                intent.putExtra(BranchUsersActivity.EXTRA_BRANCH_NUMBER, group.getBranchNumber());
                startActivity(intent);
            }
        });
        
        optimizedAdapter = new BranchGroupAdapter(optimizedGroups, group -> {
            Intent intent = new Intent(this, BranchUsersActivity.class);
            intent.putExtra(BranchUsersActivity.EXTRA_ROUTE_NUMBER, group.getRouteNumber());
            intent.putExtra(BranchUsersActivity.EXTRA_BRANCH_NUMBER, group.getBranchNumber());
            startActivity(intent);
        });
        
        adapter.setSelectionChangeListener(selectedCount -> {
            tvSelectedCount.setText(String.format("已选择 %d 项", selectedCount));
            tvSelectedCount.setVisibility(selectedCount > 0 ? View.VISIBLE : View.GONE);
            cardDelete.setVisibility(selectedCount > 0 ? View.VISIBLE : View.GONE);
        });
        
        rvBranchGroups.setLayoutManager(new LinearLayoutManager(this));
        rvBranchGroups.setAdapter(adapter);
        
        rvOptimizedGroups.setLayoutManager(new LinearLayoutManager(this));
        rvOptimizedGroups.setAdapter(optimizedAdapter);
    }
    
    private void setupListeners() 
    {
        btnOptimize.setOnClickListener(v -> optimizePhases());
        fabAdd.setOnClickListener(v -> showAddBranchGroupDialog());
        
        fabDelete.setOnClickListener(v -> {
            if (adapter.isSelectionMode()) 
            {
                // 退出选择模式
                exitSelectionMode();
            } 
            else 
            {
                // 进入选择模式
                enterSelectionMode();
            }
        });
        
        btnDelete.setOnClickListener(v -> {
            List<BranchGroup> selectedGroups = adapter.getSelectedGroups();
            if (!selectedGroups.isEmpty()) 
            {
                showDeleteConfirmationDialog(selectedGroups);
            }
        });
    }
    
    private void enterSelectionMode() 
    {
        adapter.setSelectionMode(true);
        fabDelete.setVisibility(View.GONE);  // 隐藏删除按钮
        btnOptimize.setVisibility(View.GONE);
    }
    
    private void exitSelectionMode() 
    {
        adapter.setSelectionMode(false);
        fabDelete.setVisibility(View.VISIBLE);  // 显示删除按钮
        btnOptimize.setVisibility(View.VISIBLE);
        tvSelectedCount.setVisibility(View.GONE);
        cardDelete.setVisibility(View.GONE);
    }
    
    private void showDeleteConfirmationDialog(List<BranchGroup> selectedGroups) 
    {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage(String.format("确定要删除选中的 %d 个支线组吗？", selectedGroups.size()))
            .setPositiveButton("确定", (dialog, which) -> {
                deleteSelectedGroups(selectedGroups);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void deleteSelectedGroups(List<BranchGroup> selectedGroups) 
    {
        boolean success = true;
        for (BranchGroup group : selectedGroups) 
        {
            if (!dbHelper.deleteBranchGroup(group.getRouteNumber(), group.getBranchNumber())) 
            {
                success = false;
                break;
            }
        }
        
        if (success) 
        {
            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
            loadBranchGroups();
            exitSelectionMode();
        } 
        else 
        {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showAddBranchGroupDialog() 
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_branch_group, null);
        
        EditText etRouteNumber = dialogView.findViewById(R.id.etRouteNumber);
        EditText etBranchNumber = dialogView.findViewById(R.id.etBranchNumber);
        TextView tvUserCount = dialogView.findViewById(R.id.tvUserCount);
        
        // 添加文本变化监听器
        TextWatcher textWatcher = new TextWatcher() 
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) 
            {
                String routeNumber = etRouteNumber.getText().toString();
                String branchNumber = etBranchNumber.getText().toString();
                
                if (!routeNumber.isEmpty() && !branchNumber.isEmpty()) 
                {
                    // 获取用户数量
                    List<User> users = dbHelper.getUsersByRouteBranch(routeNumber, branchNumber);
                    int userCount = users.size();
                    tvUserCount.setText(String.format("该支线用户数量：%d", userCount));
                    tvUserCount.setVisibility(View.VISIBLE);
                } 
                else 
                {
                    tvUserCount.setVisibility(View.GONE);
                }
            }
        };
        
        etRouteNumber.addTextChangedListener(textWatcher);
        etBranchNumber.addTextChangedListener(textWatcher);
        
        builder.setView(dialogView)
               .setTitle("添加支线组")
               .setPositiveButton("确定", (dialog, which) -> 
               {
                   String routeNumber = etRouteNumber.getText().toString();
                   String branchNumber = etBranchNumber.getText().toString();
                   
                   if (!routeNumber.isEmpty() && !branchNumber.isEmpty()) 
                   {
                       // 检查支线组是否已存在
                       if (dbHelper.branchGroupExists(routeNumber, branchNumber)) 
                       {
                           Toast.makeText(this, "该支线组已存在", Toast.LENGTH_SHORT).show();
                           return;
                       }
                       
                       // 检查数据库中是否有该支线的用户数据
                       List<User> users = dbHelper.getUsersByRouteBranch(routeNumber, branchNumber);
                       if (users.isEmpty()) 
                       {
                           Toast.makeText(this, "未找到该支线的用户数据", Toast.LENGTH_SHORT).show();
                           return;
                       }
                       
                       // 添加支线组到数据库
                       if (dbHelper.addBranchGroup(routeNumber, branchNumber)) 
                       {
                           // 重新加载支线组数据
                           loadBranchGroups();
                           Toast.makeText(this, String.format("支线组添加成功，包含%d个用户", users.size()), Toast.LENGTH_SHORT).show();
                       } 
                       else 
                       {
                           Toast.makeText(this, "添加支线组失败", Toast.LENGTH_SHORT).show();
                       }
                   }
               })
               .setNegativeButton("取消", null)
               .show();
    }
    
    private void loadBranchGroups() 
    {
        try 
        {
            branchGroups.clear();
            List<BranchGroup> groups = dbHelper.getAllBranchGroups();
            if (groups != null && !groups.isEmpty()) 
            {
                branchGroups.addAll(groups);
                adapter.notifyDataSetChanged();
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "加载支线组数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() 
    {
        super.onResume();
        loadBranchGroups();
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
            StringBuilder stats = new StringBuilder();
            
            // 计算每相总电量和变化的用户数
            double[] phasePowers = new double[3];
            int changedUsers = 0;
            int changedPowerUsers = 0;  // 添加动力用户计数
            
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
                    if (user.isPowerPhase()) 
                    {
                        changedPowerUsers++;
                    }
                }
            }
            
            // 显示总体统计信息，包括动力用户数
            stats.append(String.format("总调整用户数：%d（其中动力用户：%d）\n\n", changedUsers, changedPowerUsers));
            
            stats.append(String.format("三相总电量：\n" +
                "A相：%.2f\n" +
                "B相：%.2f\n" +
                "C相：%.2f\n\n",
                phasePowers[0], phasePowers[1], phasePowers[2]));
            
            double maxPower = Math.max(Math.max(phasePowers[0], phasePowers[1]), phasePowers[2]);
            double minPower = Math.min(Math.min(phasePowers[0], phasePowers[1]), phasePowers[2]);
            
            if (maxPower > 0) 
            {
                double unbalanceRate = ((maxPower - minPower) / maxPower) * 100;
                stats.append(String.format("三相不平衡度：%.2f%%", unbalanceRate));
            } 
            else 
            {
                stats.append("三相不平衡度：0.00%");
            }
            
            // 设置统计信息
            tvResultStats.setText(stats.toString());
            
            // 清空并重新添加优化后的支线组
            optimizedGroups.clear();
            
            // 按支线组分组显示用户
            Map<String, Map<String, Integer>> groupedUsers = new HashMap<>();
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                byte newPhase = solution.getPhase(i);
                
                if (newPhase != user.getCurrentPhase()) 
                {
                    String routeNumber = user.getRouteNumber();
                    String branchNumber = user.getBranchNumber();
                    
                    groupedUsers.computeIfAbsent(routeNumber, k -> new HashMap<>())
                               .merge(branchNumber, 1, Integer::sum);
                }
            }
            
            // 将分组数据转换为支线组列表
            List<BranchGroup> tempGroups = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> routeEntry : groupedUsers.entrySet()) 
            {
                String routeNumber = routeEntry.getKey();
                for (Map.Entry<String, Integer> branchEntry : routeEntry.getValue().entrySet()) 
                {
                    String branchNumber = branchEntry.getKey();
                    int userCount = branchEntry.getValue();
                    
                    BranchGroup group = new BranchGroup(routeNumber, branchNumber);
                    group.setUserCount(userCount);
                    tempGroups.add(group);
                }
            }
            
            // 对支线组进行排序
            Collections.sort(tempGroups, (g1, g2) -> {
                // 先按回路号排序
                int routeCompare = g1.getRouteNumber().compareTo(g2.getRouteNumber());
                if (routeCompare != 0) 
                {
                    return routeCompare;
                }
                // 回路号相同时按支线号排序
                return g1.getBranchNumber().compareTo(g2.getBranchNumber());
            });
            
            // 更新优化后的支线组列表
            optimizedGroups.clear();
            optimizedGroups.addAll(tempGroups);
            
            // 通知适配器数据已更新
            optimizedAdapter.notifyDataSetChanged();
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "显示优化结果失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onBackPressed() 
    {
        if (adapter.isSelectionMode()) 
        {
            // 如果在选择模式，则退出选择模式
            exitSelectionMode();
        } 
        else 
        {
            // 否则执行默认的返回操作
            super.onBackPressed();
        }
    }
} 