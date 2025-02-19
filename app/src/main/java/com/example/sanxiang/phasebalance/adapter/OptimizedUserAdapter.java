package com.example.sanxiang.phasebalance.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.db.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class OptimizedUserAdapter extends RecyclerView.Adapter<OptimizedUserAdapter.ViewHolder> 
{
    private List<User> users;
    private List<Byte> newPhases;
    private List<Byte> phaseMoves;  // 添加移动次数列表
    private DatabaseHelper dbHelper;
    
    public OptimizedUserAdapter(List<User> users, List<Byte> newPhases, List<Byte> phaseMoves, DatabaseHelper dbHelper) 
    {
        this.users = users;
        this.newPhases = newPhases;
        this.phaseMoves = phaseMoves;
        this.dbHelper = dbHelper;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) 
    {
        View view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_optimized_user, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) 
    {
        User user = users.get(position);
        byte newPhase = newPhases.get(position);
        byte moves = phaseMoves.get(position);
        
        // 设置用户基本信息
        holder.tvUserInfo.setText(String.format("%d. %s - %s", 
            position + 1, user.getUserId(), user.getUserName()));
        
        // 获取用户最近一天的电量数据
        List<UserData> userDataList = dbHelper.getUserLastNDaysData(user.getUserId(), 1);
        if (!userDataList.isEmpty()) 
        {
            UserData userData = userDataList.get(0);
            
            if (user.isPowerPhase()) 
            {
                // 动力用户显示三相电量和相位变化
                holder.tvPowerType.setVisibility(View.VISIBLE);
                holder.tvPowerType.setText("[动力用户]");
                
                String phaseInfo;
                if (moves == 1) 
                {
                    // 调整一次：A->B, B->C, C->A
                    phaseInfo = String.format(
                        "当前三相功率：A相=%.2f, B相=%.2f, C相=%.2f\n" +
                        "相位调整：A相->B相, B相->C相, C相->A相",
                        userData.getPhaseAPower(), 
                        userData.getPhaseBPower(), 
                        userData.getPhaseCPower()
                    );
                } 
                else 
                {
                    // 调整两次：A->C, B->A, C->B
                    phaseInfo = String.format(
                        "当前三相功率：A相=%.2f, B相=%.2f, C相=%.2f\n" +
                        "相位调整：A相->C相, B相->A相, C相->B相",
                        userData.getPhaseAPower(), 
                        userData.getPhaseBPower(), 
                        userData.getPhaseCPower()
                    );
                }
                
                holder.tvPhaseChange.setText(phaseInfo);
            } 
            else 
            {
                // 普通用户显示当前相位和电量
                holder.tvPowerType.setVisibility(View.GONE);
                
                double currentPower = user.getCurrentPhase() == 1 ? userData.getPhaseAPower() :
                                    user.getCurrentPhase() == 2 ? userData.getPhaseBPower() :
                                    userData.getPhaseCPower();
                                    
                holder.tvPhaseChange.setText(String.format(
                    "当前功率：%s相=%.2f\n相位调整：%s相",
                    getPhaseString(user.getCurrentPhase()), 
                    currentPower,
                    getPhaseString(newPhase)
                ));
            }
        }
    }
    
    private String getPhaseString(byte phase) 
    {
        switch (phase) 
        {
            case 1: return "A";
            case 2: return "B";
            case 3: return "C";
            default: return "未知";
        }
    }
    
    @Override
    public int getItemCount() 
    {
        return users.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder 
    {
        TextView tvUserInfo;
        TextView tvPowerType;
        TextView tvPhaseChange;
        
        ViewHolder(View itemView) 
        {
            super(itemView);
            tvUserInfo = itemView.findViewById(R.id.tvUserInfo);
            tvPowerType = itemView.findViewById(R.id.tvPowerType);
            tvPhaseChange = itemView.findViewById(R.id.tvPhaseChange);
        }
    }
} 