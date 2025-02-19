package com.example.sanxiang.phasebalance.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import com.example.sanxiang.phasebalance.model.User;
import java.util.ArrayList;
import java.util.List;

public class CircuitView extends View 
{
    private Paint linePaint;      // 线路画笔
    private Paint textPaint;      // 文字画笔
    private Paint redPaint;       // 红色画笔（用于标记需要调整的用户）
    private Paint nodePaint;      // 节点画笔
    private List<User> users;     // 用户列表
    private List<User> adjustedUsers;  // 需要调整的用户列表
    private float startX;         // 起始X坐标
    private float startY;         // 起始Y坐标
    private float nodeRadius;     // 节点半径
    private float spacing;        // 间距
    
    public CircuitView(Context context) 
    {
        super(context);
        init();
    }
    
    public CircuitView(Context context, AttributeSet attrs) 
    {
        super(context, attrs);
        init();
    }
    
    private void init() 
    {
        users = new ArrayList<>();
        adjustedUsers = new ArrayList<>();
        
        // 初始化画笔
        linePaint = new Paint();
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(4);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        textPaint.setAntiAlias(true);
        
        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(4);
        redPaint.setStyle(Paint.Style.STROKE);
        redPaint.setAntiAlias(true);
        
        nodePaint = new Paint();
        nodePaint.setStyle(Paint.Style.FILL);
        nodePaint.setAntiAlias(true);
        
        // 设置基本参数
        nodeRadius = 10;
        spacing = 100;
    }
    
    public void setData(List<User> users, List<User> adjustedUsers) 
    {
        this.users = users;
        this.adjustedUsers = adjustedUsers;
        invalidate();  // 刷新视图
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int viewWidth, int viewHeight) 
    {
        super.onSizeChanged(w, h, viewWidth, viewHeight);
        startX = getPaddingLeft() + 100;  // 留出左边距
        startY = getPaddingTop() + 100;   // 留出上边距
    }
    
    @Override
    protected void onDraw(Canvas canvas) 
    {
        super.onDraw(canvas);
        
        if (users.isEmpty()) return;
        
        // 绘制主干线
        canvas.drawLine(startX, startY, startX + spacing * (users.size() + 1), startY, linePaint);
        
        // 绘制用户节点和连接线
        float currentX = startX + spacing;
        for (User user : users) 
        {
            boolean needsAdjustment = adjustedUsers.contains(user);
            Paint currentPaint = needsAdjustment ? redPaint : linePaint;
            
            // 绘制垂直连接线
            canvas.drawLine(currentX, startY, currentX, startY + spacing, currentPaint);
            
            // 绘制用户节点
            nodePaint.setColor(needsAdjustment ? Color.RED : Color.BLACK);
            canvas.drawCircle(currentX, startY + spacing, nodeRadius, nodePaint);
            
            // 绘制用户信息
            String userInfo = String.format("%s\n%s相", 
                user.getUserName(), 
                user.getCurrentPhase() == 1 ? "A" : 
                user.getCurrentPhase() == 2 ? "B" : "C");
            
            // 计算文本位置
            float textX = currentX - textPaint.measureText(user.getUserName()) / 2;
            canvas.drawText(user.getUserName(), textX, startY + spacing * 1.5f, textPaint);
            
            String phase = user.getCurrentPhase() == 1 ? "A相" : 
                         user.getCurrentPhase() == 2 ? "B相" : "C相";
            textX = currentX - textPaint.measureText(phase) / 2;
            canvas.drawText(phase, textX, startY + spacing * 1.8f, textPaint);
            
            currentX += spacing;
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) 
    {
        int minWidth = getPaddingLeft() + getPaddingRight() + 
                      (int)(spacing * (users.size() + 2));  // 额外空间用于边距
        int minHeight = getPaddingTop() + getPaddingBottom() + 
                       (int)(spacing * 3);  // 足够的高度显示文本
        
        int width = resolveSize(minWidth, widthMeasureSpec);
        int height = resolveSize(minHeight, heightMeasureSpec);
        
        setMeasuredDimension(width, height);
    }
} 