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

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import androidx.annotation.NonNull;

public class UserDataAdapter extends RecyclerView.Adapter<UserDataAdapter.ViewHolder>
{
    private List<UserData> dataList = new ArrayList<>();
    private List<UserData> allData = new ArrayList<>();  // 用于搜索

    public void setData(List<UserData> newData)
    {
        this.dataList = new ArrayList<>(newData);
        this.allData = new ArrayList<>(newData);
        notifyDataSetChanged();
    }

    public void filter(String userId)
    {
        if (userId == null || userId.trim().isEmpty())
        {
            dataList = new ArrayList<>(allData);
        }
        else
        {
            dataList = allData.stream()
                .filter(d -> d.getUserId().contains(userId.trim()))
                .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_data, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
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

    static class ViewHolder extends RecyclerView.ViewHolder
    {
        TextView tvUserInfo;
        TextView tvRouteInfo;
        TextView tvPhaseInfo;
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