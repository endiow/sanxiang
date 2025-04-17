package com.example.sanxiang.userdata.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.R;
import com.example.sanxiang.db.DatabaseHelper;
import com.example.sanxiang.userdata.UserDetailActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    // 数据库帮助类
    private DatabaseHelper dbHelper;
    // 当前日期
    private String currentDate;
    // 上下文
    private Context context;

    public UserDataAdapter(Context context) 
    {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }

    // 设置当前日期
    public void setCurrentDate(String date) 
    {
        this.currentDate = date;
    }

    //设置新的数据列表
    public void setData(List<UserData> newData)
    {
        if (newData == null)
        {
            newData = new ArrayList<>();
        }
        
        synchronized (this)
        {
            this.dataList = new ArrayList<>();
            this.allData = new ArrayList<>();
            
            if (!newData.isEmpty())
            {
                this.dataList.addAll(newData);
                this.allData.addAll(newData);
                
                // 对数据进行排序，将有相位调整的用户排在前面
                if (currentDate != null && !currentDate.isEmpty()) 
                {
                    sortByPhaseAdjustment();
                }
            }
        }
        notifyDataSetChanged();
    }

    // 排序，将有相位调整的用户排在前面
    private void sortByPhaseAdjustment() 
    {
        if (currentDate == null || dbHelper == null) return;
        
        // 获取当日有相位调整的用户列表
        List<String> adjustedUsers = dbHelper.getUsersWithPhaseAdjustmentOnDate(currentDate);
        
        if (!adjustedUsers.isEmpty()) 
        {
            // 使用Comparator进行排序，将有相位调整的用户排在前面
            dataList = dataList.stream()
                .sorted(Comparator.comparing(userData -> 
                    !adjustedUsers.contains(userData.getUserId())))
                .collect(Collectors.toList());
                
            allData = new ArrayList<>(dataList);
        }
    }

    //根据用户ID过滤数据
    public void filter(String searchId)
    {
        synchronized (this)
        {
            if (searchId == null || searchId.trim().isEmpty())
            {
                dataList = new ArrayList<>(allData);
            }
            else
            {
                try
                {
                    String trimmedSearchId = searchId.trim();
                    dataList = allData.stream()
                        .filter(d -> {
                            if (d == null || d.getUserId() == null) return false;
                            String userId = d.getUserId();
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
                    dataList = new ArrayList<>();
                }
            }
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
        try
        {
            // 绑定数据到视图
            UserData data = dataList.get(position);
            if (data != null)
            {
                holder.tvUserInfo.setText(String.format("用户编号：%s  用户名称：%s", 
                                          data.getUserId(), data.getUserName()));
                holder.tvRouteInfo.setText(String.format("回路编号：%s  支线编号：%s", 
                                           data.getRouteNumber(), 
                                           "0".equals(data.getBranchNumber()) ? "主干线" : data.getBranchNumber()));
                
                // 检查该用户在当前日期是否有相位调整
                Map<String, Object> adjustment = null;
                if (currentDate != null && dbHelper != null) 
                {
                    adjustment = dbHelper.getUserPhaseAdjustment(data.getUserId(), currentDate);
                    if (adjustment != null) 
                    {
                        String oldPhase = (String) adjustment.get("oldPhase");
                        String newPhase = (String) adjustment.get("newPhase");
                        boolean isPowerUser = (boolean) adjustment.get("isPowerUser");
                        
                        // 设置相位信息，突出显示相位调整
                        String phaseInfo = String.format("相位：%s → %s", oldPhase, newPhase);
                        String powerUserInfo = isPowerUser ? " [动力用户]" : "";
                        holder.tvPhaseInfo.setText(phaseInfo + powerUserInfo);
                        holder.tvPhaseInfo.setTextColor(Color.RED);
                        holder.tvPhaseInfo.setTypeface(null, Typeface.BOLD);
                        
                        // 为相位信息添加点击事件，显示相位调整详情
                        final Map<String, Object> finalAdjustment = adjustment;
                        holder.tvPhaseInfo.setOnClickListener(v -> showAdjustmentDetails(data, finalAdjustment, false));
                    } 
                    else 
                    {
                        // 正常显示相位
                        String powerUserInfo = dbHelper.isPowerUser(data.getUserId()) ? " [动力用户]" : "";
                        holder.tvPhaseInfo.setText(String.format("相位：%s%s", data.getPhase(), powerUserInfo));
                        holder.tvPhaseInfo.setTextColor(Color.BLACK);
                        holder.tvPhaseInfo.setTypeface(null, Typeface.NORMAL);
                        holder.tvPhaseInfo.setOnClickListener(null);
                    }
                } 
                else 
                {
                    String powerUserInfo = dbHelper.isPowerUser(data.getUserId()) ? " [动力用户]" : "";
                    holder.tvPhaseInfo.setText(String.format("相位：%s%s", data.getPhase(), powerUserInfo));
                    holder.tvPhaseInfo.setOnClickListener(null);
                }
                
                holder.tvPowerInfo.setText(String.format("A相电量：%.2f  B相电量：%.2f  C相电量：%.2f",
                                           data.getPhaseAPower(), data.getPhaseBPower(), data.getPhaseCPower()));
                
                // 设置点击事件，打开用户详情页面
                final Map<String, Object> finalAdjustment = adjustment;
                holder.itemView.setOnClickListener(v -> {
                    if (finalAdjustment != null) {
                        // 如果有相位调整，显示调整详情
                        showAdjustmentDetails(data, finalAdjustment, false);
                    }
                    // 不再跳转到详情页面
                });
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount()
    {
        synchronized (this)
        {
            return dataList != null ? dataList.size() : 0;
        }
    }

    /**
     * 显示相位调整详情对话框
     * @param userData 当前用户数据
     * @param adjustment 相位调整记录
     * @param showOpenDetail 是否显示"查看详情"按钮
     */
    private void showAdjustmentDetails(UserData userData, Map<String, Object> adjustment, boolean showOpenDetail) {
        if (adjustment == null || userData == null) return;
        
        String oldPhase = (String) adjustment.get("oldPhase");
        String newPhase = (String) adjustment.get("newPhase");
        Double oldPhaseAPower = (Double) adjustment.get("phaseAPower");
        Double oldPhaseBPower = (Double) adjustment.get("phaseBPower");
        Double oldPhaseCPower = (Double) adjustment.get("phaseCPower");
        boolean isPowerUser = (boolean) adjustment.get("isPowerUser");
        
        String message = String.format(
            "用户：%s (%s)\n" +
            "日期：%s\n" +
            "相位调整：%s → %s\n" +
            "用户类型：%s\n\n" +
            "调整前电量数据：\n" +
            "A相：%.2f\n" +
            "B相：%.2f\n" +
            "C相：%.2f\n\n" +
            "调整后电量数据：\n" +
            "A相：%.2f\n" +
            "B相：%.2f\n" +
            "C相：%.2f",
            userData.getUserId(), userData.getUserName(),
            currentDate,
            oldPhase, newPhase,
            isPowerUser ? "动力用户" : "普通用户",
            oldPhaseAPower, oldPhaseBPower, oldPhaseCPower,
            userData.getPhaseAPower(), userData.getPhaseBPower(), userData.getPhaseCPower()
        );
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle("相位调整详情")
            .setMessage(message)
            .setPositiveButton("关闭", null);
            
        builder.show();
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