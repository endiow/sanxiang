package com.example.sanxiang.util;

import android.widget.EditText;

/**
 * 日期格式验证工具类
 */
public class DateValidator 
{
    /**
     * 验证日期格式并转换为标准格式
     * 支持的格式：
     * 1. yyyy-MM-dd
     * 2. yyyy/MM/dd
     * 3. yyyy.MM.dd
     * 4. yyyyMMdd
     * 5. yy-MM-dd
     * 6. yy/MM/dd
     * 7. yy.MM.dd
     * 8. yyyy-M-d
     * 9. yyyy/M/d
     * 10. yyyy.M.d
     * 11. yy-M-d
     * 12. yy/M/d
     * 13. yy.M.d
     * 
     * @param date 输入的日期字符串
     * @param etDate 日期输入框（用于更新标准格式）
     * @return 是否为有效日期
     */
    public static boolean isValidDateFormat(String date, EditText etDate)
    {
        if (date == null || date.trim().isEmpty())
        {
            return false;
        }

        // 移除所有空格
        date = date.trim().replace(" ", "");
        
        try
        {
            String[] parts;
            // 尝试不同的分隔符
            if (date.contains("-"))
            {
                parts = date.split("-");
            }
            else if (date.contains("/"))
            {
                parts = date.split("/");
            }
            else if (date.contains("."))
            {
                parts = date.split("\\.");
            }
            else if (date.length() == 8)  // 处理 yyyyMMdd 格式
            {
                parts = new String[]
                {
                    date.substring(0, 4),
                    date.substring(4, 6),
                    date.substring(6, 8)
                };
            }
            else if (date.length() >= 6 && date.length() <= 8)  // 处理 yyMMdd 格式
            {
                // 尝试解析为 yyMMdd 格式
                String yearStr = date.substring(0, 2);
                String monthStr = date.substring(2, date.length() - 2);
                String dayStr = date.substring(date.length() - 2);
                
                parts = new String[]{yearStr, monthStr, dayStr};
            }
            else
            {
                return false;
            }

            // 确保有年月日三个部分
            if (parts.length != 3)
            {
                return false;
            }

            // 解析年月日
            int year, month, day;
            
            // 处理两位数年份
            if (parts[0].length() == 2)
            {
                year = Integer.parseInt("20" + parts[0]);
            }
            else
            {
                year = Integer.parseInt(parts[0]);
            }
            
            // 处理月份，支持个位数
            month = Integer.parseInt(parts[1]);
            // 处理日期，支持个位数
            day = Integer.parseInt(parts[2]);

            // 验证年月日的范围
            if (year < 2000 || year > 2100)
            {
                return false;
            }
            if (month < 1 || month > 12)
            {
                return false;
            }
            
            // 根据月份判断日期范围
            int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            // 处理闰年
            if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
            {
                daysInMonth[1] = 29;
            }
            
            if (day < 1 || day > daysInMonth[month - 1])
            {
                return false;
            }

            // 转换为标准格式并更新输入框
            String standardDate = String.format("%04d-%02d-%02d", year, month, day);
            if (!standardDate.equals(date) && etDate != null)
            {
                etDate.setText(standardDate);
            }
            
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 将日期标准化为yyyy-MM-dd格式
     * @param date 输入的日期字符串
     * @return 标准化后的日期字符串，如果输入无效则返回null
     */
    public static String standardizeDate(String date)
    {
        if (date == null || date.trim().isEmpty())
        {
            return null;
        }

        // 移除所有空格
        date = date.trim().replace(" ", "");
        
        try
        {
            String[] parts;
            // 尝试不同的分隔符
            if (date.contains("-"))
            {
                parts = date.split("-");
            }
            else if (date.contains("/"))
            {
                parts = date.split("/");
            }
            else if (date.contains("."))
            {
                parts = date.split("\\.");
            }
            else if (date.length() == 8)  // 处理 yyyyMMdd 格式
            {
                parts = new String[]
                {
                    date.substring(0, 4),
                    date.substring(4, 6),
                    date.substring(6, 8)
                };
            }
            else if (date.length() >= 6 && date.length() <= 8)  // 处理 yyMMdd 格式
            {
                // 尝试解析为 yyMMdd 格式
                String yearStr = date.substring(0, 2);
                String monthStr = date.substring(2, date.length() - 2);
                String dayStr = date.substring(date.length() - 2);
                
                parts = new String[]{yearStr, monthStr, dayStr};
            }
            else
            {
                return null;
            }

            // 确保有年月日三个部分
            if (parts.length != 3)
            {
                return null;
            }

            // 解析年月日
            int year, month, day;
            
            // 处理两位数年份
            if (parts[0].length() == 2)
            {
                year = Integer.parseInt("20" + parts[0]);
            }
            else
            {
                year = Integer.parseInt(parts[0]);
            }
            
            // 处理月份，支持个位数
            month = Integer.parseInt(parts[1]);
            // 处理日期，支持个位数
            day = Integer.parseInt(parts[2]);

            // 验证年月日的范围
            if (year < 2000 || year > 2100)
            {
                return null;
            }
            if (month < 1 || month > 12)
            {
                return null;
            }
            
            // 根据月份判断日期范围
            int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            // 处理闰年
            if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
            {
                daysInMonth[1] = 29;
            }
            
            if (day < 1 || day > daysInMonth[month - 1])
            {
                return null;
            }

            // 返回标准格式
            return String.format("%04d-%02d-%02d", year, month, day);
        }
        catch (Exception e)
        {
            return null;
        }
    }
} 