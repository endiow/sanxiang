package com.example.sanxiang.phasebalance;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.R;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.phasebalance.adapter.BranchGroupAdapter;
import com.example.sanxiang.phasebalance.algorithm.PhaseBalancer;
import com.example.sanxiang.phasebalance.model.BranchGroup;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.util.UnbalanceCalculator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhaseAdjustmentStrategyActivity extends AppCompatActivity 
{
    private static final String TAG = "PhaseAdjustStrategy";
    
    // 列名常量
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_PHASE = "phase";
    private static final String COLUMN_OLD_PHASE = "old_phase";
    private static final String COLUMN_NEW_PHASE = "new_phase";
    private static final String COLUMN_PHASE_A_POWER = "phase_a_power";
    private static final String COLUMN_PHASE_B_POWER = "phase_b_power";
    private static final String COLUMN_PHASE_C_POWER = "phase_c_power";
    private static final String COLUMN_TOTAL_PHASE_A = "total_phase_a";
    private static final String COLUMN_TOTAL_PHASE_B = "total_phase_b";
    private static final String COLUMN_TOTAL_PHASE_C = "total_phase_c";
    private static final String COLUMN_UNBALANCE_RATE = "unbalance_rate";
    
    // 传递参数的键
    public static final String EXTRA_USERS = "extra_users";
    public static final String EXTRA_SOLUTION_PHASES = "extra_solution_phases";
    public static final String EXTRA_SOLUTION_MOVES = "extra_solution_moves";
    public static final String EXTRA_PHASE_POWERS = "extra_phase_powers";
    public static final String EXTRA_UNBALANCE_RATE = "extra_unbalance_rate";
    
    private DatabaseHelper dbHelper;
    private RecyclerView rvAdjustmentGroups;
    private TextView tvAdjustmentStats;
    private Button btnApplyAdjustment;
    private FloatingActionButton fabBack;
    
    private List<User> users;
    private byte[] solutionPhases;
    private byte[] solutionMoves;
    private double[] phasePowers;
    private double unbalanceRate;
    
    private BranchGroupAdapter adapter;
    private List<BranchGroup> adjustmentGroups;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phase_adjustment_strategy);
        
        dbHelper = new DatabaseHelper(this);
        adjustmentGroups = new ArrayList<>();
        
        // 初始化视图
        initViews();
        
        // 从Intent获取数据
        getDataFromIntent();
        
        // 设置统计信息
        setupStatistics();
        
        // 加载需要调整的支线组
        loadAdjustmentGroups();
    }
    
    private void initViews() 
    {
        rvAdjustmentGroups = findViewById(R.id.rvAdjustmentGroups);
        tvAdjustmentStats = findViewById(R.id.tvAdjustmentStats);
        btnApplyAdjustment = findViewById(R.id.btnApplyAdjustment);
        fabBack = findViewById(R.id.fabBack);
        
        // 设置支线组适配器
        adapter = new BranchGroupAdapter(adjustmentGroups, group -> {
            try 
            {
                if (users == null || users.isEmpty()) 
                {
                    Toast.makeText(this, "无有效用户数据", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                Intent intent = new Intent(this, OptimizedBranchUsersActivity.class);
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_ROUTE_NUMBER, group.getRouteNumber());
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_BRANCH_NUMBER, group.getBranchNumber());
                
                // 获取需要调整的用户ID列表
                ArrayList<String> adjustedUserIds = new ArrayList<>();
                
                for (int i = 0; i < users.size(); i++) 
                {
                    User user = users.get(i);
                    if (user != null && 
                        group.getRouteNumber().equals(user.getRouteNumber()) && 
                        group.getBranchNumber().equals(user.getBranchNumber())) 
                    {
                        byte newPhase = solutionPhases[i];
                        byte moves = solutionMoves[i];
                        boolean isChanged = false;
                        
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
                        }
                    }
                }
                
                if (adjustedUserIds.isEmpty()) 
                {
                    Toast.makeText(this, "该支线组没有需要调整的用户", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 获取该支线组所有用户的优化后相位
                List<User> branchUsers = dbHelper.getUsersByRouteBranch(group.getRouteNumber(), group.getBranchNumber());
                byte[] optimizedPhases = new byte[branchUsers.size()];
                byte[] phaseMoves = new byte[branchUsers.size()];
                
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
                        optimizedPhases[i] = solutionPhases[userIndex];
                        phaseMoves[i] = solutionMoves[userIndex];
                    } 
                    else 
                    {
                        optimizedPhases[i] = branchUser.getCurrentPhase();
                        phaseMoves[i] = 0;
                    }
                }
                
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_NEW_PHASES, optimizedPhases);
                intent.putExtra(OptimizedBranchUsersActivity.EXTRA_PHASE_MOVES, phaseMoves);
                intent.putStringArrayListExtra(OptimizedBranchUsersActivity.EXTRA_ADJUSTED_USERS, adjustedUserIds);
                startActivity(intent);
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
                Toast.makeText(this, "跳转失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        rvAdjustmentGroups.setLayoutManager(new LinearLayoutManager(this));
        rvAdjustmentGroups.setAdapter(adapter);
        
        // 返回按钮点击监听
        fabBack.setOnClickListener(v -> finish());
        
        // 确认应用调整按钮点击监听
        btnApplyAdjustment.setOnClickListener(v -> {
            showConfirmDialog();
        });
    }
    
    private void getDataFromIntent() 
    {
        Intent intent = getIntent();
        if (intent != null) 
        {
            try {
                // 使用强制类型转换，确保正确处理序列化对象
                users = new ArrayList<>((ArrayList<User>) intent.getSerializableExtra(EXTRA_USERS));
                solutionPhases = intent.getByteArrayExtra(EXTRA_SOLUTION_PHASES);
                solutionMoves = intent.getByteArrayExtra(EXTRA_SOLUTION_MOVES);
                phasePowers = intent.getDoubleArrayExtra(EXTRA_PHASE_POWERS);
                unbalanceRate = intent.getDoubleExtra(EXTRA_UNBALANCE_RATE, 0.0);
                
                // 验证数据有效性
                if (users == null || users.isEmpty() || solutionPhases == null || solutionMoves == null) 
                {
                    Toast.makeText(this, "无效的优化结果数据", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } catch (ClassCastException e) {
                Log.e(TAG, "数据类型转换失败: " + e.getMessage());
                Toast.makeText(this, "数据类型转换失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            } catch (Exception e) {
                Log.e(TAG, "获取Intent数据失败: " + e.getMessage());
                Toast.makeText(this, "获取数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        else 
        {
            Toast.makeText(this, "无法获取优化数据", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void setupStatistics() 
    {
        StringBuilder stats = new StringBuilder();
        
        // 统计变化的用户数
        int changedUsers = 0;
        int changedPowerUsers = 0;
        
        for (int i = 0; i < users.size(); i++) 
        {
            User user = users.get(i);
            byte newPhase = solutionPhases[i];
            byte moves = solutionMoves[i];
            
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
                changedUsers++;
                if (user.isPowerPhase()) 
                {
                    changedPowerUsers++;
                }
            }
        }
        
        // 显示总体统计信息
        stats.append(String.format("总调整用户数：%d（其中动力用户：%d）\n\n", changedUsers, changedPowerUsers));
        
        stats.append(String.format("三相总电量：\n" +
            "A相：%.2f\n" +
            "B相：%.2f\n" +
            "C相：%.2f\n\n",
            phasePowers[0], phasePowers[1], phasePowers[2]));
        
        // 显示不平衡度
        String status = UnbalanceCalculator.getUnbalanceStatus(unbalanceRate);
        stats.append(String.format("三相不平衡度：%.2f%% (%s)", unbalanceRate, status));
        
        tvAdjustmentStats.setText(stats.toString());
    }
    
    private void loadAdjustmentGroups() 
    {
        try 
        {
            // 按支线组分组显示用户
            Map<String, Map<String, Integer>> groupedUsers = new HashMap<>();
            
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                byte newPhase = solutionPhases[i];
                byte moves = solutionMoves[i];
                
                String routeNumber = user.getRouteNumber();
                String branchNumber = user.getBranchNumber();
                
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
            
            // 更新适配器数据
            adjustmentGroups.clear();
            adjustmentGroups.addAll(tempGroups);
            adapter.notifyDataSetChanged();
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            Toast.makeText(this, "加载支线组数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showConfirmDialog() 
    {
        new AlertDialog.Builder(this)
            .setTitle("确认应用调整")
            .setMessage("确定要应用这些相位调整吗？此操作将修改用户的相位配置。")
            .setPositiveButton("确定", (dialog, which) -> {
                applyPhaseAdjustments();
            })
            .setNegativeButton("取消", null)
            .create()
            .show();
    }
    
    private void applyPhaseAdjustments() 
    {
        // 显示进度对话框
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("正在应用调整")
            .setMessage("正在更新用户相位数据...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        // 在后台线程中处理数据更新
        new Thread(() -> {
            try {
                // 获取最新的日期
                String latestDate = dbHelper.getLatestDate();
                if (latestDate == null || latestDate.isEmpty()) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "无法获取最新日期数据", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // 获取需要调整的用户列表
                List<User> adjustedUsers = new ArrayList<>();
                for (int i = 0; i < users.size(); i++) {
                    User user = users.get(i);
                    byte newPhase = solutionPhases[i];
                    byte moves = solutionMoves[i];
                    
                    boolean isChanged = false;
                    if (user.isPowerPhase()) {
                        // 动力用户通过moves判断
                        isChanged = moves > 0;
                    } else {
                        // 普通用户通过相位变化判断
                        isChanged = newPhase != user.getCurrentPhase();
                    }
                    
                    if (isChanged) {
                        adjustedUsers.add(user);
                    }
                }
                
                // 更新SQLite数据库
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                
                try {
                    // 逐个用户更新相位
                    for (int i = 0; i < adjustedUsers.size(); i++) {
                        User user = adjustedUsers.get(i);
                        String userId = user.getUserId();
                        
                        // 找到该用户在原始用户列表中的索引
                        int userIndex = -1;
                        for (int j = 0; j < users.size(); j++) {
                            if (users.get(j).getUserId().equals(userId)) {
                                userIndex = j;
                                break;
                            }
                        }
                        
                        if (userIndex == -1) continue;
                        
                        // 获取新相位
                        byte newPhase = solutionPhases[userIndex];
                        
                        // 用户数据表名
                        String userDataTable = "user_data_" + userId;
                        
                        // 查询最新日期的用户数据
                        Cursor cursor = db.query(
                            userDataTable,
                            new String[]{COLUMN_PHASE, COLUMN_PHASE_A_POWER, COLUMN_PHASE_B_POWER, COLUMN_PHASE_C_POWER},
                            "date=?",
                            new String[]{latestDate},
                            null, null, null
                        );
                        
                        if (cursor.moveToFirst()) {
                            // 获取旧相位和电量数据
                            String oldPhase = cursor.getString(cursor.getColumnIndex("phase"));
                            // 格式化相位名称，确保只有A/B/C，去掉可能存在的"相"字
                            oldPhase = oldPhase.replace("相", "");
                            double oldPhaseA = cursor.getDouble(cursor.getColumnIndex("phase_a_power"));
                            double oldPhaseB = cursor.getDouble(cursor.getColumnIndex("phase_b_power"));
                            double oldPhaseC = cursor.getDouble(cursor.getColumnIndex("phase_c_power"));
                            
                            // 判断是否为动力用户
                            boolean isPowerUser = oldPhaseA > 0 && oldPhaseB > 0 && oldPhaseC > 0;
                            
                            // 构建新相位的字符串表示
                            String newPhaseStr;
                            switch (newPhase) {
                                case 1: newPhaseStr = "A"; break;
                                case 2: newPhaseStr = "B"; break;
                                case 3: newPhaseStr = "C"; break;
                                default: newPhaseStr = oldPhase; break;
                            }
                            
                            // 存储旧数据到旧数据表
                            ContentValues oldDataValues = new ContentValues();
                            oldDataValues.put("date", latestDate);
                            oldDataValues.put("user_id", userId);
                            oldDataValues.put("old_phase", oldPhase);
                            oldDataValues.put("new_phase", newPhaseStr);
                            oldDataValues.put("phase_a_power", oldPhaseA);
                            oldDataValues.put("phase_b_power", oldPhaseB);
                            oldDataValues.put("phase_c_power", oldPhaseC);
                            oldDataValues.put("is_power_user", isPowerUser ? 1 : 0);
                            
                            // 插入或替换旧数据记录
                            db.insertWithOnConflict(
                                "old_data",
                                null,
                                oldDataValues,
                                SQLiteDatabase.CONFLICT_REPLACE
                            );
                            
                            // 调整后的相位数据
                            ContentValues newDataValues = new ContentValues();
                            newDataValues.put("phase", newPhaseStr);
                            
                            // 动力用户相位调整 - 交换三相电量
                            if (isPowerUser) {
                                byte moves = solutionMoves[userIndex];
                                if (moves > 0) {
                                    // moves=1表示顺时针旋转, moves=2表示逆时针旋转
                                    if (moves == 1) { // 顺时针: A->B, B->C, C->A
                                        newDataValues.put("phase_a_power", oldPhaseC);
                                        newDataValues.put("phase_b_power", oldPhaseA);
                                        newDataValues.put("phase_c_power", oldPhaseB);
                                    } else { // 逆时针: A->C, B->A, C->B
                                        newDataValues.put("phase_a_power", oldPhaseB);
                                        newDataValues.put("phase_b_power", oldPhaseC);
                                        newDataValues.put("phase_c_power", oldPhaseA);
                                    }
                                }
                            } 
                            // 普通用户相位调整 - 交换单相到新相位
                            else {
                                double powerValue = 0;
                                
                                // 计算旧相位的功率
                                switch (user.getCurrentPhase()) {
                                    case 1: powerValue = oldPhaseA; break;
                                    case 2: powerValue = oldPhaseB; break;
                                    case 3: powerValue = oldPhaseC; break;
                                }
                                
                                // 将功率值设置到新相位
                                newDataValues.put("phase_a_power", newPhase == 1 ? powerValue : 0);
                                newDataValues.put("phase_b_power", newPhase == 2 ? powerValue : 0);
                                newDataValues.put("phase_c_power", newPhase == 3 ? powerValue : 0);
                            }
                            
                            // 更新用户数据表
                            db.update(
                                userDataTable,
                                newDataValues,
                                "date=?",
                                new String[]{latestDate}
                            );
                            
                            Log.d(TAG, String.format(
                                "已更新用户[%s]的相位: %s -> %s", 
                                userId, oldPhase, newPhaseStr
                            ));
                        }
                        cursor.close();
                        
                        // 更新进度对话框
                        final int progress = i + 1;
                        final int total = adjustedUsers.size();
                        runOnUiThread(() -> {
                            progressDialog.setMessage(String.format("正在更新用户相位数据...(%d/%d)", progress, total));
                        });
                    }
                    
                    // 重新计算总电量表数据
                    updateTotalPower(db, latestDate);
                    
                    // 提交事务
                    db.setTransactionSuccessful();
                    
                    // 在UI线程显示成功信息
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "相位调整已成功应用", Toast.LENGTH_SHORT).show();
                        
                        // 返回上一个界面
                        setResult(RESULT_OK);
                        finish();
                    });
                } catch (Exception e) {
                    // 在UI线程显示错误信息
                    Log.e(TAG, "应用相位调整失败", e);
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "应用相位调整失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                } finally {
                    // 结束事务
                    db.endTransaction();
                }
            } catch (Exception e) {
                Log.e(TAG, "应用相位调整出错", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "应用相位调整出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    // 更新指定日期的总电量数据
    private void updateTotalPower(SQLiteDatabase db, String date) {
        // 获取所有用户最新日期的数据
        List<UserData> allUserData = dbHelper.getUserDataByDate(date);
        if (allUserData == null || allUserData.isEmpty()) return;
        
        // 计算三相总电量
        double totalA = 0, totalB = 0, totalC = 0;
        for (UserData userData : allUserData) {
            totalA += userData.getPhaseAPower();
            totalB += userData.getPhaseBPower();
            totalC += userData.getPhaseCPower();
        }
        
        // 计算新的不平衡度
        double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(totalA, totalB, totalC);
        
        // 更新总电量表
        ContentValues totalValues = new ContentValues();
        totalValues.put("total_phase_a", totalA);
        totalValues.put("total_phase_b", totalB);
        totalValues.put("total_phase_c", totalC);
        totalValues.put("unbalance_rate", unbalanceRate);
        
        db.update(
            "total_power",
            totalValues,
            "date=?",
            new String[]{date}
        );
        
        Log.d(TAG, String.format(
            "已更新总电量数据: A=%.2f, B=%.2f, C=%.2f, 不平衡度=%.2f%%",
            totalA, totalB, totalC, unbalanceRate
        ));
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