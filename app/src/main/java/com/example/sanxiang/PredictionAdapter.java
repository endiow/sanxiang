package com.example.sanxiang;

import android.content.Intent;
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

    public PredictionAdapter()
    {
        predictions = new ArrayList<>();
        allPredictions = new ArrayList<>();
    }

    public void setPredictions(List<PredictionResult> predictions)
    {
        this.predictions = new ArrayList<>(predictions);
        this.allPredictions = new ArrayList<>(predictions);
        notifyDataSetChanged();
    }

    public void filter(String searchId)
    {
        if (searchId == null || searchId.trim().isEmpty())
        {
            predictions = new ArrayList<>(allPredictions);
        }
        else
        {
            try
            {
                String trimmedSearchId = searchId.trim();
                predictions = allPredictions.stream()
                    .filter(p -> {
                        if (p == null || p.getUserId() == null) return false;
                        String userId = p.getUserId();
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
                predictions = new ArrayList<>();
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prediction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        PredictionResult prediction = predictions.get(position);
        
        // 设置用户信息
        holder.tvUserInfo.setText(String.format("用户编号：%s  用户名称：%s", 
                                  prediction.getUserId(), prediction.getUserName()));
        
        // 设置线路信息
        holder.tvRouteInfo.setText(String.format("回路编号：%s  线路名称：%s", 
                                   prediction.getRouteNumber(), prediction.getRouteName()));
        
        // 设置相位信息
        holder.tvPhaseInfo.setText(String.format("相位：%s", prediction.getPhase()));
        
        // 设置电量信息
        holder.tvPowerInfo.setText(String.format("A相电量：%.2f  B相电量：%.2f  C相电量：%.2f",
                                   prediction.getPredictedPhaseAPower(),
                                   prediction.getPredictedPhaseBPower(),
                                   prediction.getPredictedPhaseCPower()));

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> 
        {
            Intent intent = new Intent(v.getContext(), PredictionDetailActivity.class);
            intent.putExtra("userId", prediction.getUserId());
            intent.putExtra("userName", prediction.getUserName());
            intent.putExtra("routeNumber", prediction.getRouteNumber());
            intent.putExtra("routeName", prediction.getRouteName());
            intent.putExtra("phase", prediction.getPhase());
            intent.putExtra("predictedPhaseAPower", prediction.getPredictedPhaseAPower());
            intent.putExtra("predictedPhaseBPower", prediction.getPredictedPhaseBPower());
            intent.putExtra("predictedPhaseCPower", prediction.getPredictedPhaseCPower());
            v.getContext().startActivity(intent);
        });
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