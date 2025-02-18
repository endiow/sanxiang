package com.example.sanxiang.phasebalance.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.model.BranchGroup;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.db.DatabaseHelper;
import java.util.List;

public class BranchGroupAdapter extends RecyclerView.Adapter<BranchGroupAdapter.ViewHolder> 
{
    private List<BranchGroup> branchGroups;
    private OnBranchGroupClickListener listener;
    
    public interface OnBranchGroupClickListener 
    {
        void onBranchGroupClick(BranchGroup group);
    }
    
    public BranchGroupAdapter(List<BranchGroup> branchGroups, OnBranchGroupClickListener listener) 
    {
        this.branchGroups = branchGroups;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) 
    {
        View view = LayoutInflater.from(parent.getContext())
                                  .inflate(R.layout.item_branch_group, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) 
    {
        BranchGroup group = branchGroups.get(position);
        // 获取用户数量
        DatabaseHelper dbHelper = new DatabaseHelper(holder.itemView.getContext());
        List<User> users = dbHelper.getUsersByRouteBranch(group.getRouteNumber(), group.getBranchNumber());
        
        holder.tvBranchGroup.setText(String.format("回路%s支线%s（%d个用户）", 
            group.getRouteNumber(), group.getBranchNumber(), users.size()));
            
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBranchGroupClick(group);
            }
        });
    }
    
    @Override
    public int getItemCount() 
    {
        return branchGroups.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder 
    {
        TextView tvBranchGroup;
        
        ViewHolder(View itemView) 
        {
            super(itemView);
            tvBranchGroup = itemView.findViewById(R.id.tvBranchGroup);
        }
    }
} 