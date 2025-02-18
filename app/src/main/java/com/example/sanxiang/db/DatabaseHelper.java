package com.example.sanxiang.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.sanxiang.userdata.model.UserData;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.phasebalance.model.BranchGroup;
import com.example.sanxiang.util.UnbalanceCalculator;
import android.content.ContentValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "user_data.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_USER_INFO = "user_info";  // 用户信息表
    private static final String TABLE_TOTAL_POWER = "total_power";  // 总电量表
    private static final String TABLE_PREDICTION = "prediction";  // 预测结果表
    private static final String TABLE_LAST_MODIFIED = "last_modified";  // 最后修改时间表
    private static final String TABLE_BRANCH_GROUP = "branch_group";  // 支线组表
    private final Context context;

    // 列名常量
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_USER_NAME = "user_name";
    private static final String COLUMN_ROUTE_NUMBER = "route_number";
    private static final String COLUMN_BRANCH_NUMBER = "branch_number";  // 支线编号，0表示主干线，其他表示支线编号
    private static final String COLUMN_PHASE = "phase";
    private static final String COLUMN_PHASE_A_POWER = "phase_a_power";
    private static final String COLUMN_PHASE_B_POWER = "phase_b_power";
    private static final String COLUMN_PHASE_C_POWER = "phase_c_power";
    private static final String COLUMN_TOTAL_PHASE_A = "total_phase_a";
    private static final String COLUMN_TOTAL_PHASE_B = "total_phase_b";
    private static final String COLUMN_TOTAL_PHASE_C = "total_phase_c";
    private static final String COLUMN_UNBALANCE_RATE = "unbalance_rate";
    private static final String COLUMN_PREDICT_TIME = "predict_time";  // 预测时间
    private static final String COLUMN_PREDICTED_PHASE_A = "predicted_phase_a";  // 预测的A相电量
    private static final String COLUMN_PREDICTED_PHASE_B = "predicted_phase_b";  // 预测的B相电量
    private static final String COLUMN_PREDICTED_PHASE_C = "predicted_phase_c";  // 预测的C相电量
    private static final String COLUMN_MODIFIED_TIME = "modified_time";  // 修改时间

    // 创建用户信息表的SQL语句
    private static final String CREATE_USER_INFO_TABLE = "CREATE TABLE " + TABLE_USER_INFO + " (" +
            COLUMN_USER_ID + " TEXT PRIMARY KEY, " +  // 用户编号作为主键
            COLUMN_USER_NAME + " TEXT, " +
            COLUMN_ROUTE_NUMBER + " TEXT, " +
            COLUMN_BRANCH_NUMBER + " TEXT)";  // 支线编号

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

    // 创建预测结果表的SQL语句
    private static final String CREATE_PREDICTION_TABLE = 
            "CREATE TABLE " + TABLE_PREDICTION + " (" +
            COLUMN_USER_ID + " TEXT, " +    // 用户编号
            COLUMN_PREDICT_TIME + " TEXT, " +    // 预测时间
            COLUMN_PHASE + " TEXT, " +    // 相位
            COLUMN_PREDICTED_PHASE_A + " REAL, " +
            COLUMN_PREDICTED_PHASE_B + " REAL, " +
            COLUMN_PREDICTED_PHASE_C + " REAL, " +
            "PRIMARY KEY (" + COLUMN_USER_ID + ", " + COLUMN_PREDICT_TIME + "))";

    // 创建最后修改时间表的SQL语句
    private static final String CREATE_LAST_MODIFIED_TABLE = 
            "CREATE TABLE " + TABLE_LAST_MODIFIED + " (" + COLUMN_MODIFIED_TIME + " TEXT)";

    // 创建支线组表的SQL语句
    private static final String CREATE_BRANCH_GROUP_TABLE = 
            "CREATE TABLE " + TABLE_BRANCH_GROUP + " (" +
            COLUMN_ROUTE_NUMBER + " TEXT, " +
            COLUMN_BRANCH_NUMBER + " TEXT, " +
            "PRIMARY KEY (" + COLUMN_ROUTE_NUMBER + ", " + COLUMN_BRANCH_NUMBER + "))";


    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        try
        {
            db.execSQL(CREATE_USER_INFO_TABLE);
            db.execSQL(CREATE_TOTAL_POWER_TABLE);
            db.execSQL(CREATE_PREDICTION_TABLE);
            db.execSQL(CREATE_LAST_MODIFIED_TABLE);
            db.execSQL(CREATE_BRANCH_GROUP_TABLE);
            
            // 初始化最后修改时间
            ContentValues values = new ContentValues();
            values.put(COLUMN_MODIFIED_TIME, getCurrentTime());
            db.insert(TABLE_LAST_MODIFIED, null, values);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // 空实现
    }

    //-----------------------------------修改时间表函数-----------------------------------
    // 获取当前时间的辅助方法
    private String getCurrentTime()
    {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }

    // 更新最后修改时间
    private void updateLastModifiedTime(SQLiteDatabase db)
    {
        try
        {
            db.delete(TABLE_LAST_MODIFIED, null, null);
            ContentValues values = new ContentValues();
            values.put(COLUMN_MODIFIED_TIME, getCurrentTime());
            db.insert(TABLE_LAST_MODIFIED, null, values);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // 获取最后修改时间
    public String getLastModifiedTime()
    {
        try
        {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query(TABLE_LAST_MODIFIED, new String[]{COLUMN_MODIFIED_TIME}, null, null, null, null, null);
                
            if (cursor != null && cursor.moveToFirst())
            {
                String time = cursor.getString(0);
                cursor.close();
                return time;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    //-----------------------------------预测表函数-----------------------------------
    // 保存预测结果
    public void savePredictionResult(String userId, String phase, double phaseA, double phaseB, double phaseC)
    {
        try
        {
            SQLiteDatabase db = this.getWritableDatabase();
            
            // 获取当前时间作为预测时间
            String predictTime = getCurrentTime();
            
            // 删除该用户之前的预测结果
            db.delete(TABLE_PREDICTION, COLUMN_USER_ID + "=?", new String[]{userId});
            
            // 插入新的预测结果
            ContentValues values = new ContentValues();
            values.put(COLUMN_USER_ID, userId);
            values.put(COLUMN_PREDICT_TIME, predictTime);
            values.put(COLUMN_PHASE, phase);
            values.put(COLUMN_PREDICTED_PHASE_A, phaseA);
            values.put(COLUMN_PREDICTED_PHASE_B, phaseB);
            values.put(COLUMN_PREDICTED_PHASE_C, phaseC);

            db.insert(TABLE_PREDICTION, null, values);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // 获取用户的预测结果
    public Map<String, Object> getUserPrediction(String userId)
    {
        Map<String, Object> result = new HashMap<>();
        try
        {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query(TABLE_PREDICTION,
                new String[]{COLUMN_PHASE, COLUMN_PREDICTED_PHASE_A, COLUMN_PREDICTED_PHASE_B, COLUMN_PREDICTED_PHASE_C},
                COLUMN_USER_ID + "=?",
                new String[]{userId},
                null, null, COLUMN_PREDICT_TIME + " DESC LIMIT 1");
                
            if (cursor != null && cursor.moveToFirst())
            {
                result.put("phase", cursor.getString(0));
                result.put("phase_a", cursor.getDouble(1));
                result.put("phase_b", cursor.getDouble(2));
                result.put("phase_c", cursor.getDouble(3));
                cursor.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return result;
    }

    // 获取预测时间
    public String getPredictionTime(String userId)
    {
        try
        {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query(TABLE_PREDICTION,
                new String[]{COLUMN_PREDICT_TIME},
                COLUMN_USER_ID + "=?",
                new String[]{userId},
                null, null, COLUMN_PREDICT_TIME + " DESC LIMIT 1");
                
            if (cursor != null && cursor.moveToFirst())
            {
                String time = cursor.getString(0);
                cursor.close();
                return time;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    //-----------------------------------支线组表函数-----------------------------------
    // 添加支线组
    public boolean addBranchGroup(String routeNumber, String branchNumber) 
    {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try 
        {
            db = this.getWritableDatabase();
            
            // 检查是否有用户数据
            String query = "SELECT COUNT(*) FROM " + TABLE_USER_INFO + 
                         " WHERE " + COLUMN_ROUTE_NUMBER + " = ? AND " + 
                         COLUMN_BRANCH_NUMBER + " = ?";
            
            cursor = db.rawQuery(query, new String[]{routeNumber, branchNumber});
            
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) 
            {
                // 如果有用户数据，则添加支线组
                ContentValues values = new ContentValues();
                values.put(COLUMN_ROUTE_NUMBER, routeNumber);
                values.put(COLUMN_BRANCH_NUMBER, branchNumber);
                
                long result = db.insertWithOnConflict(
                    TABLE_BRANCH_GROUP,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                );
                
                return result != -1;
            }
            
            return false;  // 没有找到用户数据
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return false;
        } 
        finally 
        {
            if (cursor != null) 
            {
                cursor.close();
            }
            if (db != null) 
            {
                db.close();
            }
        }
    }
    
    // 删除支线组
    public boolean deleteBranchGroup(String routeNumber, String branchNumber) 
    {
        SQLiteDatabase db = null;
        try 
        {
            db = this.getWritableDatabase();
            String whereClause = COLUMN_ROUTE_NUMBER + " = ? AND " + 
                               COLUMN_BRANCH_NUMBER + " = ?";
            String[] whereArgs = {routeNumber, branchNumber};
            
            int result = db.delete(TABLE_BRANCH_GROUP, whereClause, whereArgs);
            return result > 0;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return false;
        } 
        finally 
        {
            if (db != null) 
            {
                db.close();
            }
        }
    }

    // 获取所有支线组
    public List<BranchGroup> getAllBranchGroups() 
    {
        List<BranchGroup> groups = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try 
        {
            db = this.getReadableDatabase();
            cursor = db.query(
                TABLE_BRANCH_GROUP,
                new String[]{COLUMN_ROUTE_NUMBER, COLUMN_BRANCH_NUMBER},
                null, null, null, null, null
            );
            
            while (cursor.moveToNext()) 
            {
                String routeNumber = cursor.getString(
                    cursor.getColumnIndex(COLUMN_ROUTE_NUMBER)
                );
                String branchNumber = cursor.getString(
                    cursor.getColumnIndex(COLUMN_BRANCH_NUMBER)
                );
                
                groups.add(new BranchGroup(routeNumber, branchNumber));
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            if (cursor != null) 
            {
                cursor.close();
            }
            if (db != null) 
            {
                db.close();
            }
        }
        
        return groups;
    }

    // 检查支线组是否存在
    public boolean branchGroupExists(String routeNumber, String branchNumber) 
    {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try 
        {
            db = this.getReadableDatabase();
            String query = "SELECT COUNT(*) FROM " + TABLE_BRANCH_GROUP + 
                         " WHERE " + COLUMN_ROUTE_NUMBER + " = ? AND " + 
                         COLUMN_BRANCH_NUMBER + " = ?";
            cursor = db.rawQuery(query, new String[]{routeNumber, branchNumber});
            
            if (cursor.moveToFirst()) 
            {
                return cursor.getInt(0) > 0;
            }
            return false;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return false;
        } 
        finally 
        {
            if (cursor != null) 
            {
                cursor.close();
            }
            if (db != null) 
            {
                db.close();
            }
        }
    }

    // 获取支线组的用户列表
    public List<User> getUsersByRouteBranch(String routeNumber, String branchNumber)
    {
        List<User> users = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try 
        {
            db = this.getReadableDatabase();
            
            // 获取用户基本信息
            String query = "SELECT " + COLUMN_USER_ID + ", " + COLUMN_USER_NAME + 
                         " FROM " + TABLE_USER_INFO + 
                         " WHERE " + COLUMN_ROUTE_NUMBER + " = ? AND " + 
                         COLUMN_BRANCH_NUMBER + " = ?";
            
            cursor = db.rawQuery(query, new String[]{routeNumber, branchNumber});
            
            while (cursor.moveToNext()) 
            {
                String userId = cursor.getString(cursor.getColumnIndex(COLUMN_USER_ID));
                String userName = cursor.getString(cursor.getColumnIndex(COLUMN_USER_NAME));
                
                // 获取用户最近一天的数据
                List<UserData> userDataList = getUserLastNDaysData(userId, 1);
                if (!userDataList.isEmpty()) 
                {
                    UserData userData = userDataList.get(0);
                    
                    // 计算总功率
                    double totalPower = userData.getPhaseAPower() + 
                                      userData.getPhaseBPower() + 
                                      userData.getPhaseCPower();
                    
                    // 确定当前相位
                    byte currentPhase = 0;
                    if (userData.getPhaseAPower() > 0) currentPhase = 1;
                    else if (userData.getPhaseBPower() > 0) currentPhase = 2;
                    else if (userData.getPhaseCPower() > 0) currentPhase = 3;
                    
                    // 判断是否为动力相
                    boolean isPowerPhase = userData.getPhaseAPower() > 0 && 
                                         userData.getPhaseBPower() > 0 && 
                                         userData.getPhaseCPower() > 0;
                    
                    User user = new User(
                        userId,
                        userName,
                        routeNumber,
                        branchNumber,
                        totalPower,
                        currentPhase,
                        isPowerPhase
                    );
                    users.add(user);
                }
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            if (cursor != null) 
            {
                cursor.close();
            }
            if (db != null) 
            {
                db.close();
            }
        }
        
        return users;
    }

    //-----------------------------------导入数据函数-----------------------------------
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
            
            // 更新最后修改时间
            updateLastModifiedTime(db);
            
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
        userInfo.put(COLUMN_BRANCH_NUMBER, userData.getBranchNumber());  // 支线编号
        
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
        double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(totalA, totalB, totalC);

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

    //-----------------------------------清空数据函数-----------------------------------
    // 清空所有数据
    public void clearAllData()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // 先获取所有用户ID
        List<String> userIds = getAllUserIds();
        
        db.beginTransaction();
        try
        {
            // 删除所有用户数据表
            for (String userId : userIds)
            {
                String userDataTable = "user_data_" + userId;
                db.execSQL("DROP TABLE IF EXISTS " + userDataTable);
            }
            
            // 删除用户信息表中的所有数据
            db.delete(TABLE_USER_INFO, null, null);
            
            // 删除总电量表中的所有数据
            db.delete(TABLE_TOTAL_POWER, null, null);
            
            // 删除预测结果表中的所有数据
            db.delete(TABLE_PREDICTION, null, null);
            
            // 更新最后修改时间
            updateLastModifiedTime(db);
            
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    //-----------------------------------辅助函数-----------------------------------

    //-----------------------------------日期相关函数-----------------------------------
    // 获取最近n天的日期列表
    public List<String> getLastNDays(int n)

    {
        List<String> dates = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try
        {
            db = getReadableDatabase();
            
            // 从总电量表中获取日期
            String query = "SELECT DISTINCT " + COLUMN_DATE + 
                          " FROM " + TABLE_TOTAL_POWER + 
                          " ORDER BY " + COLUMN_DATE + " DESC" +
                          " LIMIT " + n;
            
            cursor = db.rawQuery(query, null);
            if (cursor != null && cursor.moveToFirst())
            {
                do
                {
                    dates.add(cursor.getString(0));
                }
                while (cursor.moveToNext());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return dates;
    }

    // 获取最新日期
    public String getLatestDate()
    {
        try
        {
            List<String> dates = getLastNDays(1);
            return dates != null && !dates.isEmpty() ? dates.get(0) : null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    // 获取currentDate前一天的日期
    public String getPrevDate(String currentDate)
    {
        if (currentDate == null || currentDate.isEmpty())
        {
            return null;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;
        String date = null;
        
        try
        {
            db = getReadableDatabase();
            String query = "SELECT " + COLUMN_DATE + 
                          " FROM " + TABLE_TOTAL_POWER + 
                          " WHERE " + COLUMN_DATE + " < ?" +
                          " ORDER BY " + COLUMN_DATE + " DESC" +
                          " LIMIT 1";
                          
            cursor = db.rawQuery(query, new String[]{currentDate});
            if (cursor != null && cursor.moveToFirst())
            {
                date = cursor.getString(0);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return date;
    }

    // 获取currentDate下一天的日期
    public String getNextDate(String currentDate)
    {
        if (currentDate == null || currentDate.isEmpty())
        {
            return null;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;
        String date = null;
        
        try
        {
            db = getReadableDatabase();
            String query = "SELECT " + COLUMN_DATE + 
                          " FROM " + TABLE_TOTAL_POWER + 
                          " WHERE " + COLUMN_DATE + " > ?" +
                          " ORDER BY " + COLUMN_DATE + " ASC" +
                          " LIMIT 1";
                          
            cursor = db.rawQuery(query, new String[]{currentDate});
            if (cursor != null && cursor.moveToFirst())
            {
                date = cursor.getString(0);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
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
        if (date == null || date.isEmpty())
        {
            return new ArrayList<>();
        }

        List<UserData> dataList = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor userCursor = null;
        Cursor dataCursor = null;
        
        try
        {
            db = getReadableDatabase();
            
            // 获取所有用户ID
            List<String> userIds = getAllUserIds();
            
            for (String userId : userIds)
            {
                try
                {
                    // 获取用户信息
                    String userQuery = "SELECT * FROM " + TABLE_USER_INFO + " WHERE " + COLUMN_USER_ID + " = ?";
                    userCursor = db.rawQuery(userQuery, new String[]{userId});
                    
                    if (userCursor != null && userCursor.moveToFirst())
                    {
                        // 先获取列索引
                        int userNameIndex = userCursor.getColumnIndexOrThrow(COLUMN_USER_NAME);
                        int routeNumberIndex = userCursor.getColumnIndexOrThrow(COLUMN_ROUTE_NUMBER);
                        int branchNumberIndex = userCursor.getColumnIndexOrThrow(COLUMN_BRANCH_NUMBER);
                            
                        // 使用获取到的索引
                        String userName = userCursor.getString(userNameIndex);
                        String routeNumber = userCursor.getString(routeNumberIndex);
                        String branchNumber = userCursor.getString(branchNumberIndex);
                        
                        // 检查用户数据表是否存在
                        String userDataTable = "user_data_" + userId;
                        String tableExistsQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
                        Cursor tableCheckCursor = db.rawQuery(tableExistsQuery, new String[]{userDataTable});
                        boolean tableExists = tableCheckCursor != null && tableCheckCursor.moveToFirst();
                        if (tableCheckCursor != null)
                        {
                            tableCheckCursor.close();
                        }
                        
                        if (tableExists)
                        {
                            // 获取用户当天数据
                            String dataQuery = "SELECT * FROM " + userDataTable + " WHERE " + COLUMN_DATE + " = ?";
                            dataCursor = db.rawQuery(dataQuery, new String[]{date});
                            
                            if (dataCursor != null && dataCursor.moveToFirst())
                            {
                                UserData userData = new UserData();
                                userData.setDate(date);
                                userData.setUserId(userId);
                                userData.setUserName(userName);
                                userData.setRouteNumber(routeNumber);
                                userData.setBranchNumber(branchNumber);
                                
                                int phaseIndex = dataCursor.getColumnIndex(COLUMN_PHASE);
                                int phaseAPowerIndex = dataCursor.getColumnIndex(COLUMN_PHASE_A_POWER);
                                int phaseBPowerIndex = dataCursor.getColumnIndex(COLUMN_PHASE_B_POWER);
                                int phaseCPowerIndex = dataCursor.getColumnIndex(COLUMN_PHASE_C_POWER);
                                
                                if (phaseIndex >= 0 && phaseAPowerIndex >= 0 && 
                                    phaseBPowerIndex >= 0 && phaseCPowerIndex >= 0)
                                {
                                    userData.setPhase(dataCursor.getString(phaseIndex));
                                    userData.setPhaseAPower(dataCursor.getDouble(phaseAPowerIndex));
                                    userData.setPhaseBPower(dataCursor.getDouble(phaseBPowerIndex));
                                    userData.setPhaseCPower(dataCursor.getDouble(phaseCPowerIndex));
                                    dataList.add(userData);
                                }
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    if (userCursor != null)
                    {
                        userCursor.close();
                        userCursor = null;
                    }
                    if (dataCursor != null)
                    {
                        dataCursor.close();
                        dataCursor = null;
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (userCursor != null)
            {
                userCursor.close();
            }
            if (dataCursor != null)
            {
                dataCursor.close();
            }
        }
        
        return dataList;
    }

    //-----------------------------------用户相关函数-----------------------------------
    // 获取所有用户ID
    public List<String> getAllUserIds()
    {
        List<String> userIds = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try
        {
            db = getReadableDatabase();
            String query = "SELECT " + COLUMN_USER_ID + " FROM " + TABLE_USER_INFO;
            cursor = db.rawQuery(query, null);
            
            if (cursor != null && cursor.moveToFirst())
            {
                int userIdIndex = cursor.getColumnIndex(COLUMN_USER_ID);
                if (userIdIndex >= 0)
                {
                    do
                    {
                        String userId = cursor.getString(userIdIndex);
                        if (userId != null && !userId.isEmpty())
                        {
                            userIds.add(userId);
                        }
                    }
                    while (cursor.moveToNext());
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        
        return userIds;
    }

    // 获取指定用户信息的格式化字符串
    public String getUserInfo(String userId)
    {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USER_INFO + " WHERE " + COLUMN_USER_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{userId});
        
        if (cursor != null && cursor.moveToFirst())
        {
            int userNameIndex = cursor.getColumnIndex(COLUMN_USER_NAME);
            int routeNumberIndex = cursor.getColumnIndex(COLUMN_ROUTE_NUMBER);
            int branchNumberIndex = cursor.getColumnIndex(COLUMN_BRANCH_NUMBER);
            
            if (userNameIndex >= 0 && routeNumberIndex >= 0 && branchNumberIndex >= 0)
            {
                String userName = cursor.getString(userNameIndex);
                String routeNumber = cursor.getString(routeNumberIndex);
                String branchNumber = cursor.getString(branchNumberIndex);
                
                cursor.close();
                return String.format(
                    "用户编号：%s\n" +
                    "用户名称：%s\n" +
                    "回路编号：%s\n" +
                    "线路名称：%s",
                    userId, userName, routeNumber, branchNumber
                );
            }
        }
        if (cursor != null)
        {
            cursor.close();
        }
        return null;
    }

    // 获取指定用户最近n天的用电量数据
    public List<UserData> getUserLastNDaysData(String userId, int n)
    {
        List<UserData> userDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        try
        {
            // 检查用户是否存在
            String userQuery = "SELECT * FROM " + TABLE_USER_INFO + " WHERE " + COLUMN_USER_ID + " = ?";
            Cursor userCursor = db.rawQuery(userQuery, new String[]{userId});
            
            if (userCursor != null && userCursor.moveToFirst())
            {
                // 先获取列索引
                int userNameIndex = userCursor.getColumnIndexOrThrow(COLUMN_USER_NAME);
                int routeNumberIndex = userCursor.getColumnIndexOrThrow(COLUMN_ROUTE_NUMBER);
                int branchNumberIndex = userCursor.getColumnIndexOrThrow(COLUMN_BRANCH_NUMBER);

                // 使用获取到的索引
                String userName = userCursor.getString(userNameIndex);
                String routeNumber = userCursor.getString(routeNumberIndex);
                String branchNumber = userCursor.getString(branchNumberIndex);
                userCursor.close();
                
                // 获取用户电量数据
                String userDataTable = "user_data_" + userId;
                String dataQuery = "SELECT * FROM " + userDataTable + 
                                 " ORDER BY " + COLUMN_DATE + " DESC" +
                                 " LIMIT " + n;
                
                Cursor dataCursor = db.rawQuery(dataQuery, null);
                
                if (dataCursor != null)
                {
                    // 获取列索引
                    int dateIndex = dataCursor.getColumnIndex(COLUMN_DATE);
                    int phaseIndex = dataCursor.getColumnIndex(COLUMN_PHASE);
                    int phaseAPowerIndex = dataCursor.getColumnIndex(COLUMN_PHASE_A_POWER);
                    int phaseBPowerIndex = dataCursor.getColumnIndex(COLUMN_PHASE_B_POWER);
                    int phaseCPowerIndex = dataCursor.getColumnIndex(COLUMN_PHASE_C_POWER);
                    
                    // 检查所有必需的列是否存在
                    if (dateIndex >= 0 && phaseIndex >= 0 && 
                        phaseAPowerIndex >= 0 && phaseBPowerIndex >= 0 && 
                        phaseCPowerIndex >= 0)
                    {
                        while (dataCursor.moveToNext())
                        {
                            UserData userData = new UserData();
                            userData.setDate(dataCursor.getString(dateIndex));
                            userData.setUserId(userId);
                            userData.setUserName(userName);
                            userData.setRouteNumber(routeNumber);
                            userData.setBranchNumber(branchNumber);
                            userData.setPhase(dataCursor.getString(phaseIndex));
                            userData.setPhaseAPower(dataCursor.getDouble(phaseAPowerIndex));
                            userData.setPhaseBPower(dataCursor.getDouble(phaseBPowerIndex));
                            userData.setPhaseCPower(dataCursor.getDouble(phaseCPowerIndex));
                            
                            userDataList.add(userData);
                        }
                    }
                    dataCursor.close();
                }
            }
            else if (userCursor != null)
            {
                userCursor.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        return userDataList;
    }
} 