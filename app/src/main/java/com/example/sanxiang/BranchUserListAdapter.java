package com.example.sanxiang;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.algorithm.User;
import java.util.List;

public class BranchUserListAdapter extends RecyclerView.Adapter<BranchUserListAdapter.ViewHolder> 
{
    private List<User> users;
    
    public BranchUserListAdapter(List<User> users) 
    {
        this.users = users;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) 
    {
        View view = LayoutInflater.from(parent.getContext())
                                  .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) 
    {
        User user = users.get(position);
        holder.tvUserInfo.setText(String.format(
            "用户编号：%s\n用户名称：%s",
            user.getUserId(),
            user.getUserName()
        ));
    }
    
    @Override
    public int getItemCount() 
    {
        return users.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder 
    {
        TextView tvUserInfo;
        
        ViewHolder(View itemView) 
        {
            super(itemView);
            tvUserInfo = itemView.findViewById(R.id.tvUserInfo);
        }
    }
} 