package com.example.sanxiang.util;

import android.content.Context;
import android.os.Environment;
import com.example.sanxiang.data.UserData;
import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class CsvHelper
{
    private static final String OLD_DATA_DIR = "old_data";
    private static final String USER_DATA_FILE = "old_user_data.csv";
    private static final String TOTAL_POWER_FILE = "old_total_power.csv";

    /**
     * 获取旧数据目录
     */
    private static File getOldDataDir(Context context)
    {
        File dir = new File(context.getExternalFilesDir(null), OLD_DATA_DIR);
        if (!dir.exists())
        {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 保存用户数据到CSV文件
     */
    public static void saveUserDataToCsv(Context context, List<UserData> dataList)
    {
        File file = new File(getOldDataDir(context), USER_DATA_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true)))
        {
            // 如果是新文件，写入标题行
            if (file.length() == 0)
            {
                writer.println("日期,用户ID,用户名称,回路编号,线路名称,相位,A相电量,B相电量,C相电量");
            }

            // 写入数据
            for (UserData data : dataList)
            {
                writer.printf("%s,%s,%s,%s,%s,%s,%.2f,%.2f,%.2f\n",
                    data.getDate(),
                    data.getUserId(),
                    data.getUserName(),
                    data.getRouteNumber(),
                    data.getRouteName(),
                    data.getPhase(),
                    data.getPhaseAPower(),
                    data.getPhaseBPower(),
                    data.getPhaseCPower()
                );
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 保存总电量数据到CSV文件
     */
    public static void saveTotalPowerToCsv(Context context, String date, 
        double totalA, double totalB, double totalC, double unbalanceRate)
    {
        File file = new File(getOldDataDir(context), TOTAL_POWER_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true)))
        {
            // 如果是新文件，写入标题行
            if (file.length() == 0)
            {
                writer.println("日期,A相总电量,B相总电量,C相总电量,不平衡度");
            }

            // 写入数据
            writer.printf("%s,%.2f,%.2f,%.2f,%.2f\n",
                date, totalA, totalB, totalC, unbalanceRate);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 从CSV文件读取用户数据
     */
    public static List<UserData> readUserDataFromCsv(Context context)
    {
        List<UserData> dataList = new ArrayList<>();
        File file = new File(getOldDataDir(context), USER_DATA_FILE);
        
        if (!file.exists()) return dataList;

        try (BufferedReader reader = new BufferedReader(new FileReader(file)))
        {
            // 跳过标题行
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split(",");
                if (parts.length >= 9)
                {
                    UserData data = new UserData();
                    data.setDate(parts[0].trim());
                    data.setUserId(parts[1].trim());
                    data.setUserName(parts[2].trim());
                    data.setRouteNumber(parts[3].trim());
                    data.setRouteName(parts[4].trim());
                    data.setPhase(parts[5].trim());
                    data.setPhaseAPower(Double.parseDouble(parts[6].trim()));
                    data.setPhaseBPower(Double.parseDouble(parts[7].trim()));
                    data.setPhaseCPower(Double.parseDouble(parts[8].trim()));
                    dataList.add(data);
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return dataList;
    }

    /**
     * 导出CSV文件
     */
    public static void exportOldData(Context context, File targetDir)
    {
        try
        {
            File userDataFile = new File(getOldDataDir(context), USER_DATA_FILE);
            File totalPowerFile = new File(getOldDataDir(context), TOTAL_POWER_FILE);
            
            if (userDataFile.exists())
            {
                File targetUserData = new File(targetDir, USER_DATA_FILE);
                copyFile(userDataFile, targetUserData);
            }
            
            if (totalPowerFile.exists())
            {
                File targetTotalPower = new File(targetDir, TOTAL_POWER_FILE);
                copyFile(totalPowerFile, targetTotalPower);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void copyFile(File source, File target) throws IOException
    {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target))
        {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0)
            {
                out.write(buffer, 0, length);
            }
        }
    }
} 