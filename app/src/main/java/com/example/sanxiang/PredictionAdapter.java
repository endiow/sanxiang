package com.example.sanxiang;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.data.PredictionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PredictionAdapter extends RecyclerView.Adapter<PredictionAdapter.ViewHolder>
{
    private List<PredictionResult> predictions = new ArrayList<>();
    private List<PredictionResult> allPredictions = new ArrayList<>();  // 用于搜索

    public void setPredictions(List<PredictionResult> predictions)
    {
        this.predictions = new ArrayList<>(predictions);
        this.allPredictions = new ArrayList<>(predictions);
        notifyDataSetChanged();
    }

    public void filter(String userId)
    {
        if (userId == null || userId.trim().isEmpty())
        {
            predictions = new ArrayList<>(allPredictions);
        }
        else
        {
            predictions = allPredictions.stream()
                .filter(p -> p.getUserId().contains(userId.trim()))
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
        PredictionResult prediction = predictions.get(position);
        holder.tvUserInfo.setText(String.format("用户编号：%s  用户名称：%s", 
                prediction.getUserId(), prediction.getUserName()));
        holder.tvRouteInfo.setText(String.format("回路编号：%s  线路名称：%s", 
                prediction.getRouteNumber(), prediction.getRouteName()));
        holder.tvPhaseInfo.setText(String.format("相位：%s", prediction.getPhase()));
        holder.tvPowerInfo.setText(String.format("A相电量：%.2f  B相电量：%.2f  C相电量：%.2f",
                prediction.getPredictedPhaseAPower(),
                prediction.getPredictedPhaseBPower(),
                prediction.getPredictedPhaseCPower()));
        
        // 显示所有信息
        holder.tvRouteInfo.setVisibility(View.VISIBLE);
        holder.tvPhaseInfo.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount()
    {
        return predictions.size();
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