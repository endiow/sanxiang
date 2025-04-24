package com.example.sanxiang.util;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

/**
 * 电力损耗计算工具类
 * 用于计算三相电力系统中的损耗，包括线路损耗和不平衡损耗
 */
public class PowerLossCalculator 
{
    // 线路电阻（欧姆/千米），根据实际线路特性调整
    private static final double LINE_RESISTANCE = 0.35;  
    // 平均线路长度（千米）
    private static final double AVG_LINE_LENGTH = 0.5;  
    // 系统额定电压（伏特）
    private static final double RATED_VOLTAGE = 380.0;
    // 假设的平均负载持续时间（小时）- 用于将电能换算为平均功率
    private static final double AVG_LOAD_HOURS = 24.0;
    
    /**
     * 将日用电量转换为平均功率
     * @param energyConsumption 日用电量（kWh）
     * @return 平均功率（kW）
     */
    private static double convertEnergyToPower(double energyConsumption) {
        // 假设数据为日用电量，将其转换为平均功率
        return energyConsumption / AVG_LOAD_HOURS;
    }
    
    /**
     * 计算三相不平衡导致的额外损耗
     * @param phaseAEnergy A相用电量
     * @param phaseBEnergy B相用电量
     * @param phaseCEnergy C相用电量
     * @return 不平衡导致的额外损耗百分比
     */
    public static double calculateUnbalanceLoss(double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        // 使用UnbalanceCalculator计算不平衡度
        double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(phaseAEnergy, phaseBEnergy, phaseCEnergy);
        
        // 根据经验公式，不平衡度的平方与额外损耗近似成正比
        return 0.15 * Math.pow(unbalanceRate, 2);
    }
    
    /**
     * 计算线路损耗（基于电能数据）
     * @param phaseAEnergy A相用电量
     * @param phaseBEnergy B相用电量
     * @param phaseCEnergy C相用电量
     * @return 线路损耗（电能单位）
     */
    public static double calculateLineLoss(double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        // 将电能转换为平均功率
        double powerA = convertEnergyToPower(phaseAEnergy);
        double powerB = convertEnergyToPower(phaseBEnergy);
        double powerC = convertEnergyToPower(phaseCEnergy);
        
        // 假设电压为额定电压，计算每相电流
        double currentA = powerA / RATED_VOLTAGE;
        double currentB = powerB / RATED_VOLTAGE;
        double currentC = powerC / RATED_VOLTAGE;
        
        // 计算功率损耗（I²R损耗）
        double resistance = LINE_RESISTANCE * AVG_LINE_LENGTH;
        double powerLoss = resistance * (Math.pow(currentA, 2) + Math.pow(currentB, 2) + Math.pow(currentC, 2));
        
        // 将功率损耗转换为电能损耗
        return powerLoss * AVG_LOAD_HOURS;
    }
    
    /**
     * 计算总损耗率
     * @param phaseAEnergy A相用电量
     * @param phaseBEnergy B相用电量
     * @param phaseCEnergy C相用电量
     * @return 总损耗率（百分比）
     */
    public static double calculateTotalLossRate(double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        double totalEnergy = phaseAEnergy + phaseBEnergy + phaseCEnergy;
        
        // 线路基本损耗率（与负载大小有关）
        double basicLossRate = 2.5 + (totalEnergy / 10000.0); // 基础损耗2.5%，加上与负载相关的部分
        
        // 不平衡导致的额外损耗
        double unbalanceLossRate = calculateUnbalanceLoss(phaseAEnergy, phaseBEnergy, phaseCEnergy);
        
        return basicLossRate + unbalanceLossRate;
    }
    
    /**
     * 显示损耗计算过程的详细对话框
     * @param context 上下文
     * @param phaseAEnergy A相用电量
     * @param phaseBEnergy B相用电量
     * @param phaseCEnergy C相用电量
     */
    public static void showLossCalculationDetails(Context context, double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        double totalEnergy = phaseAEnergy + phaseBEnergy + phaseCEnergy;
        double avgEnergy = totalEnergy / 3.0;
        
        // 计算不平衡度 - 使用UnbalanceCalculator
        double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(phaseAEnergy, phaseBEnergy, phaseCEnergy);
        
        // 计算各种损耗
        double unbalanceLoss = calculateUnbalanceLoss(phaseAEnergy, phaseBEnergy, phaseCEnergy);
        double basicLossRate = 2.5 + (totalEnergy / 10000.0);
        double totalLossRate = basicLossRate + unbalanceLoss;
        double totalLossEnergy = totalEnergy * totalLossRate / 100.0;
        
        // 计算理想情况下的损耗
        double idealLossEnergy = totalEnergy * basicLossRate / 100.0;
        
        // 计算不平衡导致的额外损耗
        double extraLoss = totalLossEnergy - idealLossEnergy;
        
        // 计算最大偏差（与UnbalanceCalculator保持一致）
        double maxDeviation = Math.max(
            Math.max(
                Math.abs(phaseAEnergy - avgEnergy),
                Math.abs(phaseBEnergy - avgEnergy)
            ),
            Math.abs(phaseCEnergy - avgEnergy)
        );
        
        // 格式化消息
        String message = String.format(
            "电力损耗计算详情：\n\n" +
            "三相用电量：\n" +
            "A相：%.2f kWh\n" +
            "B相：%.2f kWh\n" +
            "C相：%.2f kWh\n" +
            "总用电量：%.2f kWh\n\n" +
            
            "三相不平衡度计算：\n" +
            "平均用电量 = %.2f kWh\n" +
            "最大偏差 = %.2f kWh\n" +
            "不平衡度 = (最大偏差/平均用电量) × 100%% = %.2f%%\n" +
            "不平衡状态：%s\n\n" +
            
            "损耗计算：\n" +
            "1. 基本线路损耗率：%.2f%%\n" +
            "   (基础2.5%% + 负载相关%.2f%%)\n\n" +
            
            "2. 不平衡导致的额外损耗率：%.2f%%\n" +
            "   (计算公式：0.15 × 不平衡度²)\n\n" +
            
            "3. 总损耗率：%.2f%%\n\n" +
            
            "损耗电量：\n" +
            "1. 实际总损耗：%.2f kWh\n" +
            "2. 理想情况损耗：%.2f kWh\n" +
            "3. 不平衡导致的额外损耗：%.2f kWh\n\n" +
            
            "优化建议：\n" +
            "1. 通过相位平衡可以减少约%.2f%%的损耗\n" +
            "2. 相位平衡每年可以节约约%.2f kWh的电能",
            
            phaseAEnergy, phaseBEnergy, phaseCEnergy, totalEnergy,
            
            avgEnergy,
            maxDeviation,
            unbalanceRate,
            UnbalanceCalculator.getUnbalanceStatus(unbalanceRate),
            
            basicLossRate,
            totalEnergy / 10000.0,
            
            unbalanceLoss,
            
            totalLossRate,
            
            totalLossEnergy,
            idealLossEnergy,
            extraLoss,
            
            (unbalanceLoss / totalLossRate) * 100,
            (extraLoss * 365)  // 年度节约的电能
        );
        
        // 显示对话框
        new AlertDialog.Builder(context)
            .setTitle("电力损耗分析")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }
} 