package com.example.sanxiang.phasebalance.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.model.BranchGroup;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.db.DatabaseHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BranchGroupAdapter extends RecyclerView.Adapter<BranchGroupAdapter.ViewHolder> 
{
    private List<BranchGroup> branchGroups;
    private OnBranchGroupClickListener listener;
    private boolean isSelectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangeListener selectionChangeListener;
    
    public interface OnBranchGroupClickListener 
    {
        void onBranchGroupClick(BranchGroup group);
    }
    
    public interface OnSelectionChangeListener 
    {
        void onSelectionChanged(int selectedCount);
    }
    
    public BranchGroupAdapter(List<BranchGroup> branchGroups, OnBranchGroupClickListener listener) 
    {
        this.branchGroups = branchGroups;
        this.listener = listener;
    }
    
    public void setSelectionChangeListener(OnSelectionChangeListener listener) 
    {
        this.selectionChangeListener = listener;
    }
    
    public void setSelectionMode(boolean selectionMode) 
    {
        if (this.isSelectionMode != selectionMode) 
        {
            this.isSelectionMode = selectionMode;
            if (!selectionMode) 
            {
                selectedPositions.clear();
                if (selectionChangeListener != null) 
                {
                    selectionChangeListener.onSelectionChanged(0);
                }
            }
            notifyDataSetChanged();
        }
    }
    
    public boolean isSelectionMode() 
    {
        return isSelectionMode;
    }
    
    public List<BranchGroup> getSelectedGroups() 
    {
        List<BranchGroup> selected = new ArrayList<>();
        for (Integer position : selectedPositions) 
        {
            selected.add(branchGroups.get(position));
        }
        return selected;
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
        
        if (group.getUserCount() > 0) 
        {
            // 优化结果显示调整用户数
            holder.tvBranchGroup.setText(String.format("回路%s支线%s（调整%d个用户）", 
                group.getRouteNumber(), group.getBranchNumber(), group.getUserCount()));
        }
        else 
        {
            // 原始列表显示总用户数
            holder.tvBranchGroup.setText(String.format("回路%s支线%s（%d个用户）", 
                group.getRouteNumber(), group.getBranchNumber(), users.size()));
        }
            
        // 处理选择框的显示状态
        holder.checkbox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selectedPositions.contains(position));
        holder.ivArrow.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
        
        // 处理点击事件
        View.OnClickListener clickListener = v -> {
            if (isSelectionMode) 
            {
                toggleSelection(position);
            } 
            else if (listener != null) 
            {
                listener.onBranchGroupClick(group);
            }
        };
        
        holder.itemView.setOnClickListener(clickListener);
        holder.checkbox.setOnClickListener(v -> toggleSelection(position));
    }
    
    private void toggleSelection(int position) 
    {
        if (selectedPositions.contains(position)) 
        {
            selectedPositions.remove(position);
        } 
        else 
        {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        
        if (selectionChangeListener != null) 
        {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    @Override
    public int getItemCount() 
    {
        return branchGroups.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder 
    {
        TextView tvBranchGroup;
        CheckBox checkbox;
        ImageView ivArrow;
        
        ViewHolder(View itemView) 
        {
            super(itemView);
            tvBranchGroup = itemView.findViewById(R.id.tvBranchGroup);
            checkbox = itemView.findViewById(R.id.checkbox);
            ivArrow = itemView.findViewById(R.id.ivArrow);
        }
    }
} 