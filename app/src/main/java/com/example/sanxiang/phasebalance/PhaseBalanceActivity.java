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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;

import com.example.sanxiang.util.UnbalanceCalculator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.adapter.BranchGroupAdapter;
import com.example.sanxiang.phasebalance.algorithm.*;
import com.example.sanxiang.phasebalance.model.*;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.prediction.model.PredictionResult;

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
    private Button btnApplyResult;
    private TextView tvResultStats;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabDelete;
    private TextView tvSelectedCount;
    private CardView cardDelete;
    private Button btnDelete;
    private List<User> users;
    private PhaseBalancer.Solution solution;
    private PhaseBalancer phaseBalancer;  // 添加PhaseBalancer引用
    private volatile boolean isOptimizing = false;  // 添加优化状态标志
    private View divider; // 添加分隔线引用
    private static final int REQUEST_CODE_PHASE_ADJUSTMENT = 1001;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phase_balance);
        
        dbHelper = new DatabaseHelper(this);
        branchGroups = new ArrayList<>();
        optimizedGroups = new ArrayList<>();
        users = new ArrayList<>();
        
        initializeViews();
        setupListeners();
        loadBranchGroups();
        
        // 检查是否来自预测活动
        boolean isFromPrediction = getIntent().getBooleanExtra("USE_PREDICTION", false);
        if (isFromPrediction) {
            // 自动开始相位优化
            Toast.makeText(this, "正在使用预测数据进行相位优化...", Toast.LENGTH_SHORT).show();
            optimizePhases();
        }
    }
    
    private void initializeViews() 
    {
        rvBranchGroups = findViewById(R.id.rvBranchGroups);
        rvOptimizedGroups = findViewById(R.id.rvOptimizedGroups);
        btnOptimize = findViewById(R.id.btnOptimize);
        btnApplyResult = findViewById(R.id.btnApplyResult);
        tvResultStats = findViewById(R.id.tvResultStats);
        fabAdd = findViewById(R.id.fabAdd);
        fabDelete = findViewById(R.id.fabDelete);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        cardDelete = findViewById(R.id.cardDelete);
        btnDelete = findViewById(R.id.btnDelete);
        divider = findViewById(R.id.divider);
        
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
            try 
            {
                if (solution == null || users == null || users.isEmpty()) 
                {
                    Toast.makeText(this, "请先执行相位优化", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                Intent intent = new Intent(this, OptimizedBranchUsersActivity.class);
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_ROUTE_NUMBER, group.getRouteNumber());
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_BRANCH_NUMBER, group.getBranchNumber());
                
                // 获取需要调整的用户ID列表
                ArrayList<String> adjustedUserIds = new ArrayList<>();
                
                // 添加调试日志
                Log.d("PhaseBalanceActivity", "检查回路" + group.getRouteNumber() + "支线" + group.getBranchNumber() + "的用户调整情况");
                
                for (int i = 0; i < users.size(); i++) 
                {
                    User user = users.get(i);
                    if (user != null && 
                        group.getRouteNumber().equals(user.getRouteNumber()) && 
                        group.getBranchNumber().equals(user.getBranchNumber())) 
                    {
                        byte newPhase = solution.getPhase(i);
                        byte moves = solution.getMoves(i);
                        boolean isChanged = false;
                        
                        // 修复判断逻辑：同时考虑普通用户的相位变化和动力用户的moves值
                        if (user.isPowerPhase()) {
                            // 动力用户通过moves判断
                            isChanged = moves > 0;
                        } else {
                            // 普通用户通过相位变化判断
                            isChanged = newPhase != user.getCurrentPhase();
                        }
                        
                        if (isChanged) 
                        {
                            adjustedUserIds.add(user.getUserId());
                            // 添加调试日志
                            Log.d("PhaseBalanceActivity", String.format(
                                "需要调整的用户: %s, 是否动力用户: %b, 原相位: %s, 新相位: %d, 移动次数: %d",
                                user.getUserId(), user.isPowerPhase(), user.getCurrentPhase(), newPhase, moves
                            ));
                        }
                    }
                }
                
                if (adjustedUserIds.isEmpty()) 
                {
                    Toast.makeText(this, "该支线组没有需要调整的用户", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 获取该支线组所有用户的优化后相位
                List<User> branchUsers = dbHelper.getUsersByRouteBranch(group.getRouteNumber(), group.getBranchNumber()
                );
                byte[] optimizedPhases = new byte[branchUsers.size()];
                byte[] phaseMoves = new byte[branchUsers.size()];  // 添加移动次数数组
                
                // 添加调试日志
                Log.d("PhaseBalanceActivity", String.format(
                    "支线总用户数: %d, 需要调整的用户数: %d", branchUsers.size(), adjustedUserIds.size()
                ));
                
                for (int i = 0; i < branchUsers.size(); i++) 
                {
                    User branchUser = branchUsers.get(i);
                    // 在所有用户中找到当前用户的索引
                    int userIndex = -1;
                    for (int j = 0; j < users.size(); j++) 
                    {
                        if (users.get(j).getUserId().equals(branchUser.getUserId())) 
                        {
                            userIndex = j;
                            break;
                        }
                    }
                    // 如果找到用户，获取其优化后的相位和移动次数
                    if (userIndex != -1) 
                    {
                        optimizedPhases[i] = solution.getPhase(userIndex);
                        phaseMoves[i] = solution.getMoves(userIndex);  // 获取移动次数
                    } 
                    else 
                    {
                        optimizedPhases[i] = branchUser.getCurrentPhase();
                        phaseMoves[i] = 0;  // 未调整的用户移动次数为0
                    }
                }
                
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_NEW_PHASES, optimizedPhases);
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_PHASE_MOVES, phaseMoves);  // 添加移动次数
                intent.putStringArrayListExtra(OptimizedBranchUsersActivity.EXTRA_ADJUSTED_USERS, adjustedUserIds);
                startActivity(intent);
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
                Toast.makeText(this, "跳转失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
        
        btnApplyResult.setOnClickListener(v -> {
            if (solution == null || users == null || users.isEmpty()) {
                Toast.makeText(this, "无有效优化结果", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                // 将solution中的phase和moves数组转换为可序列化的基本类型数组
                byte[] solutionPhases = new byte[users.size()];
                byte[] solutionMoves = new byte[users.size()];
                for (int i = 0; i < users.size(); i++) {
                    solutionPhases[i] = solution.getPhase(i);
                    solutionMoves[i] = solution.getMoves(i);
                }
                
                // 创建intent并传递数据
                Intent intent = new Intent(this, PhaseAdjustmentStrategyActivity.class);
                
                // 使用ArrayList包装用户列表，确保序列化
                ArrayList<User> serializableUsers = new ArrayList<>(users);
                intent.putExtra(PhaseAdjustmentStrategyActivity.EXTRA_USERS, serializableUsers);
                intent.putExtra(PhaseAdjustmentStrategyActivity.EXTRA_SOLUTION_PHASES, solutionPhases);
                intent.putExtra(PhaseAdjustmentStrategyActivity.EXTRA_SOLUTION_MOVES, solutionMoves);
                intent.putExtra(PhaseAdjustmentStrategyActivity.EXTRA_PHASE_POWERS, solution.getPhasePowers());
                intent.putExtra(PhaseAdjustmentStrategyActivity.EXTRA_UNBALANCE_RATE, solution.getUnbalanceRate());
                
                startActivityForResult(intent, REQUEST_CODE_PHASE_ADJUSTMENT);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("PhaseBalanceActivity", "启动调整策略界面失败: " + e.getMessage());
                Toast.makeText(this, "启动调整策略界面失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
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
            .setPositiveButton("确定", (dialog, which) -> deleteSelectedGroups(selectedGroups))
            .setNegativeButton("取消", null)
            .create()
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
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_branch_group, null);
        EditText etRouteNumber = dialogView.findViewById(R.id.etRouteNumber);
        EditText etBranchNumber = dialogView.findViewById(R.id.etBranchNumber);
        TextView tvUserCount = dialogView.findViewById(R.id.tvUserCount);
        
        // 获取所有可用的支线编号
        List<String> branchNumbers = dbHelper.getAllBranchNumbers();
        if (branchNumbers.isEmpty()) 
        {
            Toast.makeText(this, "数据库中没有可用的支线", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建支线选择对话框
        new AlertDialog.Builder(this)
            .setTitle("选择支线")
            .setItems(branchNumbers.toArray(new String[0]), (dialog, which) -> {
                String selectedBranch = branchNumbers.get(which);
                
                // 获取该支线下的所有回路
                List<String> routeNumbers = dbHelper.getRouteNumbersByBranchNumber(selectedBranch);
                if (routeNumbers.isEmpty()) 
                {
                    Toast.makeText(this, "该支线下没有回路数据", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 直接添加该支线下的所有回路到支线组
                if (dbHelper.addBranchGroups(selectedBranch, routeNumbers)) 
                {
                    Toast.makeText(this, "支线组添加成功", Toast.LENGTH_SHORT).show();
                    loadBranchGroups();
                } 
                else 
                {
                    Toast.makeText(this, "添加支线组失败", Toast.LENGTH_SHORT).show();
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
            // 检查是否使用预测数据
            boolean usePredict = getIntent().getBooleanExtra("USE_PREDICTION", false);
            
            // 如果使用预测数据并且传递了预测结果列表
            if (usePredict && getIntent().hasExtra("PREDICTION_DATA")) {
                ArrayList<PredictionResult> predictions = (ArrayList<PredictionResult>) getIntent().getSerializableExtra("PREDICTION_DATA");
                if (predictions != null && !predictions.isEmpty()) {
                    // 将预测结果转换为User对象
                    for (PredictionResult result : predictions) {
                        // 计算总功率
                        double predictedPhaseA = result.getPredictedPhaseAPower();
                        double predictedPhaseB = result.getPredictedPhaseBPower();
                        double predictedPhaseC = result.getPredictedPhaseCPower();
                        double totalPower = predictedPhaseA + predictedPhaseB + predictedPhaseC;
                        
                        if (totalPower > 0) {
                            // 确定当前相位
                            byte currentPhase = 0;
                            if (predictedPhaseA > 0) currentPhase = 1;
                            else if (predictedPhaseB > 0) currentPhase = 2;
                            else if (predictedPhaseC > 0) currentPhase = 3;
                            
                            // 判断是否为动力相
                            boolean isPowerPhase = predictedPhaseA > 0 && 
                                                  predictedPhaseB > 0 && 
                                                  predictedPhaseC > 0;
                            
                            User user = new User(
                                result.getUserId(),
                                result.getUserName(),
                                result.getRouteNumber(),
                                result.getRouteName(),
                                totalPower,
                                predictedPhaseA,
                                predictedPhaseB,
                                predictedPhaseC,
                                currentPhase,
                                isPowerPhase
                            );
                            users.add(user);
                            
                            Log.d("PhaseBalanceActivity", String.format(
                                "添加预测用户 - ID: %s, 名称: %s, 功率: %.2f, 相位: %d, 是否动力相: %b",
                                result.getUserId(), result.getUserName(), totalPower, currentPhase, isPowerPhase
                            ));
                        }
                    }
                    
                    Log.d("PhaseBalanceActivity", "从预测数据中添加了 " + users.size() + " 个用户");
                    return users;
                }
            }
            
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
                            userData.getPhaseAPower(),
                            userData.getPhaseBPower(),
                            userData.getPhaseCPower(),
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
            // 创建并显示进度框
            View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null);
            AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setView(progressView)
                .setCancelable(true)
                .setNegativeButton("终止", (dialog, which) -> {
                    if (isOptimizing && phaseBalancer != null) 
                    {
                        phaseBalancer.terminate();
                    }
                })
                .create();
            
            // 设置返回键和点击外部不关闭对话框
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) 
                {
                    if (isOptimizing && phaseBalancer != null) 
                    {
                        phaseBalancer.terminate();  // 终止优化
                        return true;  // 消费返回键事件
                    }
                }
                return false;
            });
            
            progressDialog.show();
            
            // 在后台线程中执行优化
            new Thread(() -> {
                try 
                {
                    isOptimizing = true;
                    // 获取所有用户数据
                    List<User> allUsers = getAllUsers();
                    
                    if (allUsers.isEmpty()) 
                    {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(this, "没有可优化的用户数据，请确保已导入用户数据且存在当天的用电量记录", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    // 创建并执行遗传算法
                    phaseBalancer = new PhaseBalancer(allUsers, branchGroups.isEmpty() ? null : branchGroups);
                    phaseBalancer.reset();  // 重置终止标志
                    PhaseBalancer.Solution solution = phaseBalancer.optimize();
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (solution == null) 
                        {
                            Toast.makeText(this, "优化失败，请重试优化", Toast.LENGTH_LONG).show();
                        }
                        else 
                        {
                            // 只有在优化成功时才显示结果
                            showOptimizationResult(allUsers, solution);
                        }
                    });
                } 
                catch (Exception e) 
                {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "优化过程出错：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
                finally 
                {
                    isOptimizing = false;
                    phaseBalancer = null;  // 清除引用
                }
            }).start();
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "优化相位失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showOptimizationResult(List<User> users, PhaseBalancer.Solution solution) 
    {
        try 
        {
            this.users = users;  // 保存用户列表
            this.solution = solution;  // 保存优化结果
            StringBuilder stats = new StringBuilder();
            
            // 直接使用解中已计算好的不平衡度和三相功率，避免重新计算导致不一致
            double[] phasePowers = solution.getPhasePowers();
            double unbalanceRate = solution.getUnbalanceRate();
            
            // 统计变化的用户数
            int changedUsers = 0;
            int changedPowerUsers = 0;  // 添加动力用户计数
            
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                byte newPhase = solution.getPhase(i);
                byte moves = solution.getMoves(i);
                
                // 检查用户是否发生变化 - 修复前的判断逻辑
                boolean isChanged = false;
                if (user.isPowerPhase()) 
                {
                    // 动力用户通过moves判断
                    isChanged = moves > 0;
                } 
                else 
                {
                    // 普通用户通过相位变化判断
                    isChanged = newPhase != user.getCurrentPhase();
                }
                
                if (isChanged) 
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
            
            // 使用算法中计算的不平衡度
            String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);
            stats.append(String.format("三相不平衡度：%.2f%% (%s)", unbalanceRate, status));
            
            // 添加日志以便调试
            Log.d("PhaseBalanceActivity", String.format(
                "显示优化结果 - 不平衡度: %.2f%%, 三相功率: [%.2f, %.2f, %.2f]",
                unbalanceRate, phasePowers[0], phasePowers[1], phasePowers[2]
            ));
            
            // 设置统计信息
            tvResultStats.setText(stats.toString());
            
            // 显示"按此结果调整"按钮，但默认隐藏详细分组
            btnApplyResult.setVisibility(View.VISIBLE);
            divider.setVisibility(View.GONE);
            rvOptimizedGroups.setVisibility(View.GONE);
            
            // 清空并重新添加优化后的支线组
            optimizedGroups.clear();
            
            // 按支线组分组显示用户 - 解决统计不匹配问题
            Map<String, Map<String, Integer>> groupedUsers = new HashMap<>();
            
            // 添加调试日志
            Log.d("PhaseBalanceActivity", "开始统计各支线调整用户数");
            
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                byte newPhase = solution.getPhase(i);
                byte moves = solution.getMoves(i);
                
                String routeNumber = user.getRouteNumber();
                String branchNumber = user.getBranchNumber();
                
                // 使用与前面相同的逻辑判断用户是否发生变化
                boolean isChanged = false;
                if (user.isPowerPhase()) 
                {
                    isChanged = moves > 0;
                } 
                else 
                {
                    isChanged = newPhase != user.getCurrentPhase();
                }
                
                if (isChanged) 
                {
                    // 添加调试日志
                    Log.d("PhaseBalanceActivity", String.format(
                        "调整用户: %s, 回路: %s, 支线: %s, 原相位: %s, 新相位: %s, 移动次数: %d",
                        user.getUserId(), routeNumber, branchNumber, 
                        user.getCurrentPhase(), newPhase, moves
                    ));
                    
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
                    
                    // 添加调试日志
                    Log.d("PhaseBalanceActivity", String.format(
                        "支线统计: 回路: %s, 支线: %s, 调整用户数: %d",
                        routeNumber, branchNumber, userCount
                    ));
                    
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
        else if (isOptimizing && phaseBalancer != null) 
        {
            // 如果正在优化，终止优化
            phaseBalancer.terminate();
            Toast.makeText(this, "正在终止优化...", Toast.LENGTH_SHORT).show();
        }
        else 
        {
            // 否则执行默认的返回操作
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_PHASE_ADJUSTMENT && resultCode == RESULT_OK) {
            Toast.makeText(this, "相位调整已成功应用", Toast.LENGTH_SHORT).show();
            
            // 重置优化结果
            users = null;
            solution = null;
            
            // 清除UI显示
            tvResultStats.setText("");
            optimizedGroups.clear();
            adapter.notifyDataSetChanged();
            
            // 隐藏相关UI元素
            btnApplyResult.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
            rvOptimizedGroups.setVisibility(View.GONE);
            
            // 刷新支线组数据
            loadBranchGroups();
        }
    }
} 