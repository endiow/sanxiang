package com.example.sanxiang;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.algorithm.BranchGroup;
import java.util.List;

public class BranchGroupAdapter extends RecyclerView.Adapter<BranchGroupAdapter.ViewHolder> 
{
    private List<BranchGroup> branchGroups;
    
    public BranchGroupAdapter(List<BranchGroup> branchGroups) 
    {
        this.branchGroups = branchGroups;
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
        holder.tvBranchGroup.setText(group.toString());
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