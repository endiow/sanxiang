package com.example.sanxiang;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.data.UserData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户数据列表的适配器类
 * 用于在RecyclerView中显示用户数据
 */
public class UserDataAdapter extends RecyclerView.Adapter<UserDataAdapter.ViewHolder>
{
    // 当前显示的数据列表
    private List<UserData> dataList = new ArrayList<>();
    // 所有数据的备份，用于搜索功能
    private List<UserData> allData = new ArrayList<>();

    /**
     * 设置新的数据列表
     */
    public void setData(List<UserData> newData)
    {
        this.dataList = new ArrayList<>(newData);
        this.allData = new ArrayList<>(newData);
        notifyDataSetChanged();
    }

    /**
     * 根据用户ID过滤数据
     */
    public void filter(String userId)
    {
        if (userId == null || userId.trim().isEmpty())
        {
            dataList = new ArrayList<>(allData);
        }
        else
        {
            dataList = allData.stream().filter(d -> d.getUserId().contains(userId.trim())).collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        // 创建列表项视图
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_data, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        // 绑定数据到视图
        UserData data = dataList.get(position);
        holder.tvUserInfo.setText(String.format("用户编号：%s  用户名称：%s", 
                                  data.getUserId(), data.getUserName()));
        holder.tvRouteInfo.setText(String.format("回路编号：%s  线路名称：%s", 
                                   data.getRouteNumber(), data.getRouteName()));
        holder.tvPhaseInfo.setText(String.format("相位：%s", data.getPhase()));
        holder.tvPowerInfo.setText(String.format("A相电量：%.2f  B相电量：%.2f  C相电量：%.2f",
                                   data.getPhaseAPower(), data.getPhaseBPower(), data.getPhaseCPower()));
    }

    @Override
    public int getItemCount()
    {
        return dataList.size();
    }

    /**
     * 列表项的ViewHolder类
     * 用于缓存列表项中的视图引用
     */
    static class ViewHolder extends RecyclerView.ViewHolder
    {
        // 用户信息文本视图
        TextView tvUserInfo;
        // 回路信息文本视图
        TextView tvRouteInfo;
        // 相位信息文本视图
        TextView tvPhaseInfo;
        // 电量信息文本视图
        TextView tvPowerInfo;

        ViewHolder(View view)
        {
            super(view);
            tvUserInfo = view.findViewById(R.id.tvUserInfo);
            tvRouteInfo = view.findViewById(R.id.tvRouteInfo);
            tvPhaseInfo = view.findViewById(R.id.tvPhaseInfo);
            tvPowerInfo = view.findViewById(R.id.tvPowerInfo);
        }
    }
} 