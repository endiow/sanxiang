package com.example.sanxiang.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.sanxiang.data.UserData;
import android.content.ContentValues;


import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "user_data.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_USER_INFO = "user_info";  // 用户信息表
    private static final String TABLE_TOTAL_POWER = "total_power";  // 总电量表
    private final Context context;

    // 列名常量
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_USER_NAME = "user_name";
    private static final String COLUMN_ROUTE_NUMBER = "route_number";
    private static final String COLUMN_ROUTE_NAME = "route_name";
    private static final String COLUMN_PHASE = "phase";
    private static final String COLUMN_PHASE_A_POWER = "phase_a_power";
    private static final String COLUMN_PHASE_B_POWER = "phase_b_power";
    private static final String COLUMN_PHASE_C_POWER = "phase_c_power";
    private static final String COLUMN_TOTAL_PHASE_A = "total_phase_a";
    private static final String COLUMN_TOTAL_PHASE_B = "total_phase_b";
    private static final String COLUMN_TOTAL_PHASE_C = "total_phase_c";
    private static final String COLUMN_UNBALANCE_RATE = "unbalance_rate";

    // 创建用户信息表的SQL语句
    private static final String CREATE_USER_INFO_TABLE = "CREATE TABLE " + TABLE_USER_INFO + " (" +
            COLUMN_USER_ID + " TEXT PRIMARY KEY, " +  // 用户编号作为主键
            COLUMN_USER_NAME + " TEXT, " +
            COLUMN_ROUTE_NUMBER + " TEXT, " +
            COLUMN_ROUTE_NAME + " TEXT)";

    // 创建用户数据表的SQL模板
    private static final String CREATE_USER_DATA_TABLE_TEMPLATE = 
            "CREATE TABLE user_data_%s (" +
            COLUMN_DATE + " TEXT PRIMARY KEY, " +  // 日期作为主键
            COLUMN_PHASE + " TEXT, " +
            COLUMN_PHASE_A_POWER + " REAL, " +
            COLUMN_PHASE_B_POWER + " REAL, " +
            COLUMN_PHASE_C_POWER + " REAL)";

    // 创建总电量表的SQL语句
    private static final String CREATE_TOTAL_POWER_TABLE = 
            "CREATE TABLE " + TABLE_TOTAL_POWER + " (" +
            COLUMN_DATE + " TEXT PRIMARY KEY, " +  // 日期作为主键
            COLUMN_TOTAL_PHASE_A + " REAL, " +
            COLUMN_TOTAL_PHASE_B + " REAL, " +
            COLUMN_TOTAL_PHASE_C + " REAL, " +
            COLUMN_UNBALANCE_RATE + " REAL)";

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // 创建用户信息表
        db.execSQL(CREATE_USER_INFO_TABLE);
        // 创建总电量表
        db.execSQL(CREATE_TOTAL_POWER_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // 空实现
    }

    // 批量导入数据
    public void importBatchData(Map<String, Map<String, List<UserData>>> dateUserGroupedData)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try
        {
            // 按日期顺序处理数据
            List<String> sortedDates = new ArrayList<>(dateUserGroupedData.keySet());
            Collections.sort(sortedDates);  // 按日期升序排序

            // 遍历每个日期
            for (String date : sortedDates)
            {
                Map<String, List<UserData>> userGroupedData = dateUserGroupedData.get(date);
                // 处理每个用户的数据
                processUserData(db, userGroupedData);
                // 更新总电量
                updateDailyTotalPower(db, date, userGroupedData);
            }
            
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    // 处理用户数据
    private void processUserData(SQLiteDatabase db, Map<String, List<UserData>> userGroupedData)
    {
        for (Map.Entry<String, List<UserData>> entry : userGroupedData.entrySet())
        {
            String userId = entry.getKey();
            List<UserData> userDataList = entry.getValue();
            
            if (!userDataList.isEmpty())
            {
                // 更新用户信息
                updateUserInfo(db, userId, userDataList.get(0));
                // 更新用户电量数据
                updateUserPowerData(db, userId, userDataList);
            }
        }
    }

    // 更新用户基本信息
    private void updateUserInfo(SQLiteDatabase db, String userId, UserData userData)
    {
        ContentValues userInfo = new ContentValues();
        userInfo.put(COLUMN_USER_ID, userId);
        userInfo.put(COLUMN_USER_NAME, userData.getUserName());
        userInfo.put(COLUMN_ROUTE_NUMBER, userData.getRouteNumber());
        userInfo.put(COLUMN_ROUTE_NAME, userData.getRouteName());
        
        db.insertWithOnConflict(TABLE_USER_INFO, null, userInfo, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // 更新用户电量数据
    private void updateUserPowerData(SQLiteDatabase db, String userId, List<UserData> userDataList)
    {
        // 确保用户数据表存在
        String userDataTable = "user_data_" + userId;
        String createTableSQL = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            COLUMN_DATE + " TEXT PRIMARY KEY, " +
            COLUMN_PHASE + " TEXT, " +
            COLUMN_PHASE_A_POWER + " REAL, " +
            COLUMN_PHASE_B_POWER + " REAL, " +
            COLUMN_PHASE_C_POWER + " REAL)", 
            userDataTable
        );
        db.execSQL(createTableSQL);
        
        // 插入新数据
        for (UserData userData : userDataList)
        {
            ContentValues values = new ContentValues();
            values.put(COLUMN_DATE, userData.getDate());
            values.put(COLUMN_PHASE, userData.getPhase());
            values.put(COLUMN_PHASE_A_POWER, userData.getPhaseAPower());
            values.put(COLUMN_PHASE_B_POWER, userData.getPhaseBPower());
            values.put(COLUMN_PHASE_C_POWER, userData.getPhaseCPower());
            
            db.insertWithOnConflict(userDataTable, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        // 检查数据行数，如果超过30行，删除最早的数据
        String countQuery = "SELECT COUNT(*) FROM " + userDataTable;
        Cursor cursor = db.rawQuery(countQuery, null);
        if (cursor.moveToFirst())
        {
            int count = cursor.getInt(0);
            if (count > 30)
            {
                // 删除最早的数据，保留最新的30条
                String deleteQuery = String.format(
                    "DELETE FROM %s WHERE " + COLUMN_DATE + " IN " +
                    "(SELECT " + COLUMN_DATE + " FROM %s ORDER BY " + COLUMN_DATE + " ASC LIMIT %d)",
                    userDataTable, userDataTable, count - 30
                );
                db.execSQL(deleteQuery);
            }
        }
        cursor.close();
    }

    // 更新每日总电量
    private void updateDailyTotalPower(SQLiteDatabase db, String date, Map<String, List<UserData>> userGroupedData)
    {
        double totalA = 0, totalB = 0, totalC = 0;
        
        // 计算当天所有用户的总电量
        for (List<UserData> userDataList : userGroupedData.values())
        {
            for (UserData userData : userDataList)
            {
                totalA += userData.getPhaseAPower();
                totalB += userData.getPhaseBPower();
                totalC += userData.getPhaseCPower();
            }
        }
        
        // 计算不平衡度
        double unbalanceRate = calculateUnbalanceRate(totalA, totalB, totalC);

        // 插入新的总电量数据
        ContentValues totalValues = new ContentValues();
        totalValues.put(COLUMN_DATE, date);
        totalValues.put(COLUMN_TOTAL_PHASE_A, totalA);
        totalValues.put(COLUMN_TOTAL_PHASE_B, totalB);
        totalValues.put(COLUMN_TOTAL_PHASE_C, totalC);
        totalValues.put(COLUMN_UNBALANCE_RATE, unbalanceRate);
        
        db.insertWithOnConflict(TABLE_TOTAL_POWER, null, totalValues, SQLiteDatabase.CONFLICT_REPLACE);

        // 检查总电量表的数据行数，如果超过30行，删除最早的数据
        String countQuery = "SELECT COUNT(*) FROM " + TABLE_TOTAL_POWER;
        Cursor cursor = db.rawQuery(countQuery, null);
        if (cursor.moveToFirst())
        {
            int count = cursor.getInt(0);
            if (count > 30)
            {
                // 删除最早的数据，保留最新的30条
                String deleteQuery = String.format(
                    "DELETE FROM %s WHERE " + COLUMN_DATE + " IN " +
                    "(SELECT " + COLUMN_DATE + " FROM %s ORDER BY " + COLUMN_DATE + " ASC LIMIT %d)",
                    TABLE_TOTAL_POWER, TABLE_TOTAL_POWER, count - 30
                );
                db.execSQL(deleteQuery);
            }
        }
        cursor.close();
    }




    //-----------------------------------辅助函数-----------------------------------

    // 计算三相不平衡度
    private double calculateUnbalanceRate(double phaseA, double phaseB, double phaseC)
    {
        double avg = (phaseA + phaseB + phaseC) / 3.0;
        if (avg == 0) return 0;

        double maxDiff = Math.max(Math.abs(phaseA - avg), Math.max(Math.abs(phaseB - avg), Math.abs(phaseC - avg)));
        return (maxDiff / avg) * 100;
    }

    // 获取所有用户ID
    public List<String> getAllUserIds()
    {
        List<String> userIds = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT " + COLUMN_USER_ID + " FROM " + TABLE_USER_INFO;
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
        
        // 从总电量表中获取日期
        String query = "SELECT DISTINCT " + COLUMN_DATE + 
                      " FROM " + TABLE_TOTAL_POWER + 
                      " ORDER BY " + COLUMN_DATE + " DESC" +
                      " LIMIT " + n;
        
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst())
        {
            do
            {
                dates.add(cursor.getString(0));
            }
            while (cursor.moveToNext());
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
        String query = "SELECT " + COLUMN_DATE + 
                      " FROM " + TABLE_TOTAL_POWER + 
                      " WHERE " + COLUMN_DATE + " < ?" +
                      " ORDER BY " + COLUMN_DATE + " DESC" +
                      " LIMIT 1";
                      
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
        String query = "SELECT " + COLUMN_DATE + 
                      " FROM " + TABLE_TOTAL_POWER + 
                      " WHERE " + COLUMN_DATE + " > ?" +
                      " ORDER BY " + COLUMN_DATE + " ASC" +
                      " LIMIT 1";
                      
        Cursor cursor = db.rawQuery(query, new String[]{currentDate});
        String date = null;
        if (cursor.moveToFirst())
        {
            date = cursor.getString(0);
        }
        cursor.close();
        return date;
    }

    // 获取指定日期的总电量数据
    public double[] getTotalPowerByDate(String date)
    {
        double[] totalPower = new double[4];  // [A相总量, B相总量, C相总量, 不平衡度]
        SQLiteDatabase db = getReadableDatabase();
        
        String query = "SELECT " + 
                      COLUMN_TOTAL_PHASE_A + ", " +
                      COLUMN_TOTAL_PHASE_B + ", " +
                      COLUMN_TOTAL_PHASE_C + ", " +
                      COLUMN_UNBALANCE_RATE +
                      " FROM " + TABLE_TOTAL_POWER + 
                      " WHERE " + COLUMN_DATE + " = ?";
                      
        Cursor cursor = db.rawQuery(query, new String[]{date});
        if (cursor.moveToFirst())
        {
            totalPower[0] = cursor.getDouble(0);  // A相总量
            totalPower[1] = cursor.getDouble(1);  // B相总量
            totalPower[2] = cursor.getDouble(2);  // C相总量
            totalPower[3] = cursor.getDouble(3);  // 不平衡度
        }
        cursor.close();
        return totalPower;
    }

    // 获取指定日期的用户数据
    public List<UserData> getUserDataByDate(String date)
    {
        List<UserData> dataList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        // 获取所有用户ID
        List<String> userIds = getAllUserIds();
        
        for (String userId : userIds)
        {
            // 获取用户信息
            String userQuery = "SELECT * FROM " + TABLE_USER_INFO + " WHERE " + COLUMN_USER_ID + " = ?";
            Cursor userCursor = db.rawQuery(userQuery, new String[]{userId});
            
            if (userCursor.moveToFirst())
            {
                String userName = userCursor.getString(userCursor.getColumnIndex(COLUMN_USER_NAME));
                String routeNumber = userCursor.getString(userCursor.getColumnIndex(COLUMN_ROUTE_NUMBER));
                String routeName = userCursor.getString(userCursor.getColumnIndex(COLUMN_ROUTE_NAME));
                userCursor.close();
                
                // 获取用户当天数据
                String userDataTable = "user_data_" + userId;
                String dataQuery = "SELECT * FROM " + userDataTable + " WHERE " + COLUMN_DATE + " = ?";
                Cursor dataCursor = db.rawQuery(dataQuery, new String[]{date});
                
                if (dataCursor.moveToFirst())
                {
                    UserData userData = new UserData();
                    userData.setDate(date);
                    userData.setUserId(userId);
                    userData.setUserName(userName);
                    userData.setRouteNumber(routeNumber);
                    userData.setRouteName(routeName);
                    userData.setPhase(dataCursor.getString(dataCursor.getColumnIndex(COLUMN_PHASE)));
                    userData.setPhaseAPower(dataCursor.getDouble(dataCursor.getColumnIndex(COLUMN_PHASE_A_POWER)));
                    userData.setPhaseBPower(dataCursor.getDouble(dataCursor.getColumnIndex(COLUMN_PHASE_B_POWER)));
                    userData.setPhaseCPower(dataCursor.getDouble(dataCursor.getColumnIndex(COLUMN_PHASE_C_POWER)));
                    dataList.add(userData);
                }
                dataCursor.close();
            }
        }
        
        return dataList;
    }

    // 获取某用户最近20天的用户数据，用于预测
    public List<UserData> getLastTwentyDaysData(String userId)
    {
        List<UserData> dataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 获取用户信息
        String userQuery = "SELECT * FROM " + TABLE_USER_INFO + " WHERE " + COLUMN_USER_ID + " = ?";
        Cursor userCursor = db.rawQuery(userQuery, new String[]{userId});
        
        if (!userCursor.moveToFirst())
        {
            userCursor.close();
            return dataList;
        }
        
        // 读取用户基本信息
        String userName = userCursor.getString(userCursor.getColumnIndex(COLUMN_USER_NAME));
        String routeNumber = userCursor.getString(userCursor.getColumnIndex(COLUMN_ROUTE_NUMBER));
        String routeName = userCursor.getString(userCursor.getColumnIndex(COLUMN_ROUTE_NAME));
        userCursor.close();
        
        // 获取用户数据
        String userDataTable = "user_data_" + userId;
        String dataQuery = "SELECT * FROM " + userDataTable + 
                          " ORDER BY " + COLUMN_DATE + " DESC" +
                          " LIMIT 20";
        
        Cursor cursor = db.rawQuery(dataQuery, null);
        if (cursor.moveToFirst())
        {
            do
            {
                UserData userData = new UserData();
                userData.setDate(cursor.getString(cursor.getColumnIndex(COLUMN_DATE)));
                userData.setUserId(userId);
                userData.setUserName(userName);
                userData.setRouteNumber(routeNumber);
                userData.setRouteName(routeName);
                userData.setPhase(cursor.getString(cursor.getColumnIndex(COLUMN_PHASE)));
                userData.setPhaseAPower(cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_A_POWER)));
                userData.setPhaseBPower(cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_B_POWER)));
                userData.setPhaseCPower(cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_C_POWER)));
                dataList.add(userData);
            }
            while (cursor.moveToNext());
        }
        cursor.close();
        return dataList;
    }

    
} 