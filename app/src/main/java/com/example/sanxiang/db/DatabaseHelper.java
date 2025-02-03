package com.example.sanxiang.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.sanxiang.data.UserData;
import android.content.ContentValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "sanxiang.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "user_data";
    private static final String TABLE_TOTAL_POWER = "total_power";
    private final Context context;

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // 用户数据表
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT,"
                + "user_id TEXT,"
                + "user_name TEXT,"
                + "route_number TEXT,"
                + "route_name TEXT,"
                + "phase TEXT,"
                + "phase_a_power REAL,"
                + "phase_b_power REAL,"
                + "phase_c_power REAL)";
        
        // 总电量和平衡度表
        String createTotalPowerTable = "CREATE TABLE " + TABLE_TOTAL_POWER + " ("
                + "date TEXT PRIMARY KEY,"
                + "total_phase_a REAL,"
                + "total_phase_b REAL,"
                + "total_phase_c REAL,"
                + "unbalance_rate REAL)";

        db.execSQL(createTable);
        db.execSQL(createTotalPowerTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        
    }

    // 导入一天的所有用户数据
    public void importData(List<UserData> dayData)
    {
        if (dayData == null || dayData.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try
        {
            String date = dayData.get(0).getDate();

            // 处理每个用户的数据
            for (UserData data : dayData)
            {
                insertData(data);
            }

            // 所有数据插入完成后，更新该日期的总电量
            updateTotalPower(date, db);
            
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    // 插入单条数据
    public void insertData(UserData data)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try
        {
            // 先删除相同日期和用户ID的数据
            String deleteExisting = "DELETE FROM " + TABLE_NAME + " WHERE user_id = ? AND date = ?";
            db.execSQL(deleteExisting, new String[]{data.getUserId(), data.getDate()});

            // 检查用户记录数
            String countQuery = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE user_id = ?";
            Cursor cursor = db.rawQuery(countQuery, new String[]{data.getUserId()});
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();

            // 如果记录数达到30，删除最早的记录
            if (count >= 30)
            {
                String deleteOldest = "DELETE FROM " + TABLE_NAME 
                    + " WHERE user_id = ? AND date = (SELECT date FROM " + TABLE_NAME 
                    + " WHERE user_id = ? ORDER BY date ASC LIMIT 1)";
                db.execSQL(deleteOldest, new String[]{data.getUserId(), data.getUserId()});
            }

            // 插入新记录
            ContentValues values = new ContentValues();
            values.put("date", data.getDate());
            values.put("user_id", data.getUserId());
            values.put("user_name", data.getUserName());
            values.put("route_number", data.getRouteNumber());
            values.put("route_name", data.getRouteName());
            values.put("phase", data.getPhase());
            values.put("phase_a_power", data.getPhaseAPower());
            values.put("phase_b_power", data.getPhaseBPower());
            values.put("phase_c_power", data.getPhaseCPower());
            
            db.insert(TABLE_NAME, null, values);
            
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    // 更新date的总电量和平衡度
    private void updateTotalPower(String date, SQLiteDatabase db)
    {
        // 计算指定日期的总电量
        String query = "SELECT SUM(phase_a_power) as total_a, "
                + "SUM(phase_b_power) as total_b, "
                + "SUM(phase_c_power) as total_c "
                + "FROM " + TABLE_NAME + " WHERE date = ?";

        Cursor cursor = db.rawQuery(query, new String[]{date});

        if (cursor.moveToFirst())
        {
            double totalA = cursor.getDouble(0);
            double totalB = cursor.getDouble(1);
            double totalC = cursor.getDouble(2);
            double unbalanceRate = calculateUnbalanceRate(totalA, totalB, totalC);

            // 检查总电量记录数
            String countQuery = "SELECT COUNT(*) FROM " + TABLE_TOTAL_POWER;
            Cursor countCursor = db.rawQuery(countQuery, null);
            countCursor.moveToFirst();
            int count = countCursor.getInt(0);
            countCursor.close();

            // 如果记录数达到30，删除最早的记录
            if (count >= 30)
            {
                String deleteOldest = "DELETE FROM " + TABLE_TOTAL_POWER 
                    + " WHERE date = (SELECT date FROM " + TABLE_TOTAL_POWER 
                    + " ORDER BY date ASC LIMIT 1)";
                db.execSQL(deleteOldest);
            }

            // 更新或插入新记录
            ContentValues values = new ContentValues();
            values.put("date", date);
            values.put("total_phase_a", totalA);
            values.put("total_phase_b", totalB);
            values.put("total_phase_c", totalC);
            values.put("unbalance_rate", unbalanceRate);

            db.insertWithOnConflict(TABLE_TOTAL_POWER, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
        cursor.close();
    }

    // 计算三相不平衡度
    private double calculateUnbalanceRate(double phaseA, double phaseB, double phaseC)
    {
        double avg = (phaseA + phaseB + phaseC) / 3.0;
        if (avg == 0) return 0;

        double maxDiff = Math.max(Math.abs(phaseA - avg), Math.max(Math.abs(phaseB - avg), Math.abs(phaseC - avg)));
        return (maxDiff / avg) * 100;
    }



    //-----------------------------------辅助函数-----------------------------------

    // 获取所有用户ID
    public List<String> getAllUserIds()
    {
        List<String> userIds = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT DISTINCT user_id FROM " + TABLE_NAME;
        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext())
        {
            userIds.add(cursor.getString(0));
        }
        cursor.close();
        return userIds;
    }
    
    // 获取最近n天的日期列表
    public List<String> getLastNDays(int n)
    {
        List<String> dates = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT DISTINCT date FROM " + TABLE_TOTAL_POWER + " ORDER BY date DESC LIMIT ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(n)});

        while (cursor.moveToNext())
        {
            dates.add(cursor.getString(0));
        }
        cursor.close();
        return dates;
    }

    // 获取最新日期
    public String getLatestDate()
    {
        List<String> dates = getLastNDays(1);
        return dates.isEmpty() ? null : dates.get(0);
    }

    // 获取currentDate前一天的日期
    public String getPrevDate(String currentDate)
    {
        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT DISTINCT date FROM " + TABLE_NAME + " WHERE date < ? ORDER BY date DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, new String[]{currentDate});

        String date = null;
        if (cursor.moveToFirst())
        {
            date = cursor.getString(0);
        }
        cursor.close();
        return date;
    }

    // 获取currentDate下一天的日期
    public String getNextDate(String currentDate)
    {
        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT DISTINCT date FROM " + TABLE_NAME + " WHERE date > ? ORDER BY date ASC LIMIT 1";
        Cursor cursor = db.rawQuery(query, new String[]{currentDate});

        String date = null;
        if (cursor.moveToFirst())
        {
            date = cursor.getString(0);
        }
        cursor.close();
        return date;
    }

    // 获取指定日期的用户数据
    public List<UserData> getUserDataByDate(String date)
    {
        List<UserData> dataList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_NAME + " WHERE date = ? ORDER BY user_id";
        Cursor cursor = db.rawQuery(query, new String[]{date});

        while (cursor.moveToNext())
        {
            UserData userData = new UserData();
            int dateIndex = cursor.getColumnIndexOrThrow("date");
            int userIdIndex = cursor.getColumnIndexOrThrow("user_id");
            int userNameIndex = cursor.getColumnIndexOrThrow("user_name");
            int routeNumberIndex = cursor.getColumnIndexOrThrow("route_number");
            int routeNameIndex = cursor.getColumnIndexOrThrow("route_name");
            int phaseIndex = cursor.getColumnIndexOrThrow("phase");
            int phaseAPowerIndex = cursor.getColumnIndexOrThrow("phase_a_power");
            int phaseBPowerIndex = cursor.getColumnIndexOrThrow("phase_b_power");
            int phaseCPowerIndex = cursor.getColumnIndexOrThrow("phase_c_power");

            userData.setDate(cursor.getString(dateIndex));
            userData.setUserId(cursor.getString(userIdIndex));
            userData.setUserName(cursor.getString(userNameIndex));
            userData.setRouteNumber(cursor.getString(routeNumberIndex));
            userData.setRouteName(cursor.getString(routeNameIndex));
            userData.setPhase(cursor.getString(phaseIndex));
            userData.setPhaseAPower(cursor.getDouble(phaseAPowerIndex));
            userData.setPhaseBPower(cursor.getDouble(phaseBPowerIndex));
            userData.setPhaseCPower(cursor.getDouble(phaseCPowerIndex));
            dataList.add(userData);
        }
        cursor.close();
        return dataList;
    }

    // 获取指定日期的三相总电量和平衡度
    public double[] getTotalPowerByDate(String date)
    {
        double[] totalPower = new double[4];  
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT total_phase_a, total_phase_b, total_phase_c, unbalance_rate "
                + "FROM " + TABLE_TOTAL_POWER + " WHERE date = ?";
        Cursor cursor = db.rawQuery(query, new String[]{date});

        if (cursor.moveToFirst())
        {
            totalPower[0] = cursor.getDouble(0);
            totalPower[1] = cursor.getDouble(1);
            totalPower[2] = cursor.getDouble(2);
            totalPower[3] = cursor.getDouble(3);  
        }
        cursor.close();
        return totalPower;
    }

    // 获取某用户最近20天的用户数据，用于预测
    public List<UserData> getLastTwentyDaysData(String userId)
    {
        List<UserData> dataList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_NAME + " WHERE user_id = ? ORDER BY date DESC LIMIT 20";
        Cursor cursor = db.rawQuery(query, new String[]{userId});

        while (cursor.moveToNext())
        {
            UserData userData = new UserData();
            int dateIndex = cursor.getColumnIndexOrThrow("date");
            int userIdIndex = cursor.getColumnIndexOrThrow("user_id");
            int userNameIndex = cursor.getColumnIndexOrThrow("user_name");
            int routeNumberIndex = cursor.getColumnIndexOrThrow("route_number");
            int routeNameIndex = cursor.getColumnIndexOrThrow("route_name");
            int phaseIndex = cursor.getColumnIndexOrThrow("phase");
            int phaseAPowerIndex = cursor.getColumnIndexOrThrow("phase_a_power");
            int phaseBPowerIndex = cursor.getColumnIndexOrThrow("phase_b_power");
            int phaseCPowerIndex = cursor.getColumnIndexOrThrow("phase_c_power");

            userData.setDate(cursor.getString(dateIndex));
            userData.setUserId(cursor.getString(userIdIndex));
            userData.setUserName(cursor.getString(userNameIndex));
            userData.setRouteNumber(cursor.getString(routeNumberIndex));
            userData.setRouteName(cursor.getString(routeNameIndex));
            userData.setPhase(cursor.getString(phaseIndex));
            userData.setPhaseAPower(cursor.getDouble(phaseAPowerIndex));
            userData.setPhaseBPower(cursor.getDouble(phaseBPowerIndex));
            userData.setPhaseCPower(cursor.getDouble(phaseCPowerIndex));
            dataList.add(userData);
        }
        cursor.close();
        return dataList;
    }

    

    
} 