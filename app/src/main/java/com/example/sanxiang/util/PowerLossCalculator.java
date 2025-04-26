package com.example.sanxiang.util;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

/**
 * 电力损耗计算工具类
 * 用于计算三相电力系统中的线路损耗（以kWh为单位）
 */
public class PowerLossCalculator 
{
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
     * 计算线路损耗系数（不包含电阻R的部分）
     * @param phaseAEnergy A相用电量（kWh）
     * @param phaseBEnergy B相用电量（kWh）
     * @param phaseCEnergy C相用电量（kWh）
     * @return 线路损耗系数（kWh/Ω）
     */
    public static double calculateLineLossCoefficient(double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        // 将电能转换为平均功率
        double powerA = convertEnergyToPower(phaseAEnergy);
        double powerB = convertEnergyToPower(phaseBEnergy);
        double powerC = convertEnergyToPower(phaseCEnergy);
        
        // 假设电压为额定电压，计算每相电流
        double currentA = powerA / RATED_VOLTAGE;
        double currentB = powerB / RATED_VOLTAGE;
        double currentC = powerC / RATED_VOLTAGE;
        
        // 计算不包含电阻R的功率损耗系数 (I²)
        double powerLossCoefficient = (Math.pow(currentA, 2) + Math.pow(currentB, 2) + Math.pow(currentC, 2));
        
        // 将功率损耗系数转换为电能损耗系数
        return powerLossCoefficient * AVG_LOAD_HOURS;
    }
    
    /**
     * 显示损耗计算过程的详细对话框
     * @param context 上下文
     * @param phaseAEnergy A相用电量（kWh）
     * @param phaseBEnergy B相用电量（kWh）
     * @param phaseCEnergy C相用电量（kWh）
     */
    public static void showLossCalculationDetails(Context context, double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        double totalEnergy = phaseAEnergy + phaseBEnergy + phaseCEnergy;
        
        // 计算中间值 - 功率
        double powerA = convertEnergyToPower(phaseAEnergy);
        double powerB = convertEnergyToPower(phaseBEnergy);
        double powerC = convertEnergyToPower(phaseCEnergy);
        
        // 计算电流
        double currentA = powerA / RATED_VOLTAGE;
        double currentB = powerB / RATED_VOLTAGE;
        double currentC = powerC / RATED_VOLTAGE;
        
        // 计算电流平方和
        double currentSquaredSum = Math.pow(currentA, 2) + Math.pow(currentB, 2) + Math.pow(currentC, 2);
        
        // 计算最终损耗系数
        double lossCoefficient = currentSquaredSum * AVG_LOAD_HOURS;
        
        // 格式化消息
        String message = String.format(
            "电力损耗计算详情：\n\n" +
            "一、输入数据：\n" +
            "A相：%.2f kWh\n" +
            "B相：%.2f kWh\n" +
            "C相：%.2f kWh\n" +
            "总用电量：%.2f kWh\n\n" +
            
            "二、计算过程：\n" +
            "1. 将电能转换为平均功率（P = 电能/24小时）：\n" +
            "   PA = %.2f kWh ÷ 24h = %.4f kW\n" +
            "   PB = %.2f kWh ÷ 24h = %.4f kW\n" +
            "   PC = %.2f kWh ÷ 24h = %.4f kW\n\n" +
            
            "2. 计算每相电流（I = P ÷ U，额定电压U = %.1f V）：\n" +
            "   IA = %.4f kW ÷ %.1f V = %.6f kA\n" +
            "   IB = %.4f kW ÷ %.1f V = %.6f kA\n" +
            "   IC = %.4f kW ÷ %.1f V = %.6f kA\n\n" +
            
            "3. 计算电流平方和（I²A + I²B + I²C）：\n" +
            "   I² = (%.6f)² + (%.6f)² + (%.6f)²\n" +
            "   I² = %.8f + %.8f + %.8f\n" +
            "   I² = %.8f kA²\n\n" +
            
            "4. 计算损耗系数（I² × 时间）：\n" +
            "   损耗系数 = %.8f kA² × 24h = %.8f kA²·h\n\n" +
            
            "三、结果：\n" +
            "线路损耗 = %.8f × R kWh\n\n" +
            
            "说明：R表示线路电阻（Ω），最终损耗值需将此结果乘以实际线路电阻。",
            
            // 输入数据
            phaseAEnergy, phaseBEnergy, phaseCEnergy, totalEnergy,
            
            // 计算过程 - 功率
            phaseAEnergy, powerA,
            phaseBEnergy, powerB,
            phaseCEnergy, powerC,
            
            // 计算过程 - 电流
            RATED_VOLTAGE,
            powerA, RATED_VOLTAGE, currentA,
            powerB, RATED_VOLTAGE, currentB,
            powerC, RATED_VOLTAGE, currentC,
            
            // 计算过程 - 电流平方
            currentA, currentB, currentC,
            Math.pow(currentA, 2), Math.pow(currentB, 2), Math.pow(currentC, 2),
            currentSquaredSum,
            
            // 结果
            currentSquaredSum, lossCoefficient,
            lossCoefficient
        );
        
        // 显示对话框
        new AlertDialog.Builder(context)
            .setTitle("电力损耗计算详情")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }
    
    /**
     * 兼容性方法，为保持接口一致而保留
     * 由于规定不再考虑不平衡损耗，此方法始终返回0
     */
    public static double calculateUnbalanceLoss(double phaseAEnergy, double phaseBEnergy, double phaseCEnergy) 
    {
        return 0.0;
    }
} 