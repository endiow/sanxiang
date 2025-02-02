package com.example.sanxiang.util;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

public class UnbalanceCalculator {
    public static double calculateUnbalanceRate(double phaseA, double phaseB, double phaseC) {
        double avgPower = (phaseA + phaseB + phaseC) / 3.0;
        if (avgPower == 0) return 0;

        double maxDeviation = Math.max(
            Math.max(
                Math.abs(phaseA - avgPower),
                Math.abs(phaseB - avgPower)
            ),
            Math.abs(phaseC - avgPower)
        );

        return (maxDeviation / avgPower) * 100;
    }

    public static String getUnbalanceStatus(double unbalanceRate) {
        if (unbalanceRate <= 15) return "正常";
        else if (unbalanceRate <= 30) return "轻度不平衡";
        else if (unbalanceRate <= 50) return "中度不平衡";
        else return "严重不平衡";
    }

    public static void showCalculationProcess(Context context, double phaseA, double phaseB, double phaseC) {
        double avgPower = (phaseA + phaseB + phaseC) / 3.0;
        double maxDeviation = Math.max(
            Math.max(
                Math.abs(phaseA - avgPower),
                Math.abs(phaseB - avgPower)
            ),
            Math.abs(phaseC - avgPower)
        );
        double unbalanceRate = avgPower > 0 ? (maxDeviation / avgPower) * 100 : 0;

        String message = String.format(
            "三相不平衡度计算过程：\n\n" +
            "1. 计算平均值：\n" +
            "   平均值 = (A相 + B相 + C相) / 3\n" +
            "   平均值 = (%.2f + %.2f + %.2f) / 3 = %.2f\n\n" +
            "2. 计算最大偏差：\n" +
            "   |A相-平均值| = |%.2f - %.2f| = %.2f\n" +
            "   |B相-平均值| = |%.2f - %.2f| = %.2f\n" +
            "   |C相-平均值| = |%.2f - %.2f| = %.2f\n" +
            "   最大偏差 = %.2f\n\n" +
            "3. 计算不平衡度：\n" +
            "   不平衡度 = (最大偏差/平均值) × 100%%\n" +
            "   不平衡度 = (%.2f / %.2f) × 100%% = %.2f%%\n\n" +
            "4. 判定结果：%s",
            phaseA, phaseB, phaseC, avgPower,
            phaseA, avgPower, Math.abs(phaseA - avgPower),
            phaseB, avgPower, Math.abs(phaseB - avgPower),
            phaseC, avgPower, Math.abs(phaseC - avgPower),
            maxDeviation,
            maxDeviation, avgPower, unbalanceRate,
            getUnbalanceStatus(unbalanceRate)
        );

        new AlertDialog.Builder(context)
                .setTitle("三相不平衡度计算")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }
} 