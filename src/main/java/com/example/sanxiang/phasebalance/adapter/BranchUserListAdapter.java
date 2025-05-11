package com.example.sanxiang.phasebalance.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sanxiang.R;
import com.example.sanxiang.phasebalance.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BranchUserListAdapter extends RecyclerView.Adapter<BranchUserListAdapter.ViewHolder> 
{
    private List<User> users;
    private List<User> allUsers;  // 用于搜索的完整列表
    
    public BranchUserListAdapter(List<User> users) 
    {
        this.users = new ArrayList<>(users);
        this.allUsers = new ArrayList<>(users);
    }

    public void filter(String searchId) 
    {
        if (searchId == null || searchId.trim().isEmpty()) 
        {
            users = new ArrayList<>(allUsers);
        } 
        else 
        {
            try 
            {
                String trimmedSearchId = searchId.trim();
                users = allUsers.stream()
                    .filter(user -> {
                        if (user == null || user.getUserId() == null) return false;
                        String userId = user.getUserId();
                        // 确保用户ID长度为10位
                        if (userId.length() != 10) return false;
                        // 检查每一位是否匹配
                        for (int i = 0; i < trimmedSearchId.length() && i < 10; i++) 
                        {
                            if (trimmedSearchId.charAt(i) != '_' && // 使用_表示任意字符
                                trimmedSearchId.charAt(i) != userId.charAt(i)) 
                            {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
                users = new ArrayList<>();
            }
        }
        notifyDataSetChanged();
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