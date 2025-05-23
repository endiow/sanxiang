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
    private static final String TABLE_OLD_DATA = "old_data";  // 旧数据表，记录相位调整信息
    private final Context context;

    // 列名常量
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_USER_NAME = "user_name";
    private static final String COLUMN_ROUTE_NUMBER = "route_number";
    private static final String COLUMN_BRANCH_NUMBER = "branch_number";  // 支线编号，0表示主干线，其他表示支线编号
    private static final String COLUMN_PHASE = "phase";
    private static final String COLUMN_OLD_PHASE = "old_phase";  // 旧相位
    private static final String COLUMN_NEW_PHASE = "new_phase";  // 新相位
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

    // 创建旧数据表的SQL语句，用于记录相位调整信息
    private static final String CREATE_OLD_DATA_TABLE = 
            "CREATE TABLE " + TABLE_OLD_DATA + " (" +
            COLUMN_DATE + " TEXT, " +
            COLUMN_USER_ID + " TEXT, " +
            COLUMN_OLD_PHASE + " TEXT, " +
            COLUMN_NEW_PHASE + " TEXT, " +
            COLUMN_PHASE_A_POWER + " REAL, " +  // 调整前A相电量
            COLUMN_PHASE_B_POWER + " REAL, " +  // 调整前B相电量
            COLUMN_PHASE_C_POWER + " REAL, " +  // 调整前C相电量
            "is_power_user INTEGER, " +         // 是否为动力用户
            "total_a_sum REAL, " +              // 调整前所有用户A相电量和
            "total_b_sum REAL, " +              // 调整前所有用户B相电量和
            "total_c_sum REAL, " +              // 调整前所有用户C相电量和
            "PRIMARY KEY (" + COLUMN_DATE + ", " + COLUMN_USER_ID + "))";


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
            // 创建用户信息表
            db.execSQL(CREATE_USER_INFO_TABLE);
            
            // 创建总电量表
            db.execSQL(CREATE_TOTAL_POWER_TABLE);
            
            // 创建预测结果表
            db.execSQL(CREATE_PREDICTION_TABLE);
            
            // 创建最后修改时间表
            db.execSQL(CREATE_LAST_MODIFIED_TABLE);
            
            // 创建支线组表
            db.execSQL(CREATE_BRANCH_GROUP_TABLE);
            
            // 创建旧数据表
            db.execSQL(CREATE_OLD_DATA_TABLE);
            
            // 初始化最后修改时间
            ContentValues values = new ContentValues();
            values.put(COLUMN_MODIFIED_TIME, getCurrentTime());
            db.insert(TABLE_LAST_MODIFIED, null, values);
            
            Log.d("DatabaseHelper", "数据库表创建成功");
        }
        catch (Exception e)
        {
            Log.e("DatabaseHelper", "数据库创建失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // 留空
        Log.d("DatabaseHelper", "数据库版本从 " + oldVersion + " 升级到 " + newVersion);
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
                        userData.getPhaseAPower(),
                        userData.getPhaseBPower(),
                        userData.getPhaseCPower(),
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

    // 更新用户功率数据，同时记录相位调整信息
    private void updateUserPowerData(SQLiteDatabase db, String userId, List<UserData> userDataList)
    {
        if (userDataList.isEmpty()) return;

        // 创建或使用用户数据表
        String userDataTable = "user_data_" + userId;
        String createTableSQL = String.format("CREATE TABLE IF NOT EXISTS %s (" +
            COLUMN_DATE + " TEXT PRIMARY KEY, " +
            COLUMN_PHASE + " TEXT, " +
            COLUMN_PHASE_A_POWER + " REAL, " +
            COLUMN_PHASE_B_POWER + " REAL, " +
            COLUMN_PHASE_C_POWER + " REAL)", 
            userDataTable
        );
        db.execSQL(createTableSQL);
        
        // 创建或使用旧相位表，如果不存在
        String createOldPhaseTableSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_OLD_DATA + " (" +
            COLUMN_DATE + " TEXT, " +
            COLUMN_USER_ID + " TEXT, " +
            COLUMN_OLD_PHASE + " TEXT, " +
            COLUMN_NEW_PHASE + " TEXT, " +
            COLUMN_PHASE_A_POWER + " REAL, " +  // 调整前A相电量
            COLUMN_PHASE_B_POWER + " REAL, " +  // 调整前B相电量
            COLUMN_PHASE_C_POWER + " REAL, " +  // 调整前C相电量
            "is_power_user INTEGER, " +         // 是否为动力用户
            "total_a_sum REAL, " +              // 调整前所有用户A相电量和
            "total_b_sum REAL, " +              // 调整前所有用户B相电量和
            "total_c_sum REAL, " +              // 调整前所有用户C相电量和
            "PRIMARY KEY (" + COLUMN_DATE + ", " + COLUMN_USER_ID + "))";
        db.execSQL(createOldPhaseTableSQL);
        
        // 插入新数据
        for (UserData userData : userDataList)
        {
            // 判断是否为动力用户
            boolean isPowerUser = userData.getPhaseAPower() > 0 && 
                               userData.getPhaseBPower() > 0 && 
                               userData.getPhaseCPower() > 0;
            
            // 检查是否已存在数据，并获取旧电量信息
            Cursor cursor = db.query(
                userDataTable,
                new String[]{COLUMN_PHASE, COLUMN_PHASE_A_POWER, COLUMN_PHASE_B_POWER, COLUMN_PHASE_C_POWER},
                COLUMN_DATE + "=?",
                new String[]{userData.getDate()},
                null, null, null
            );
            
            if (cursor.moveToFirst())
            {
                // 获取旧相位和旧电量数据
                String oldPhase = cursor.getString(cursor.getColumnIndex(COLUMN_PHASE));
                double oldPhaseA = cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_A_POWER));
                double oldPhaseB = cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_B_POWER));
                double oldPhaseC = cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_C_POWER));
                
                // 获取新数据
                String newPhase = userData.getPhase();
                double newPhaseA = userData.getPhaseAPower();
                double newPhaseB = userData.getPhaseBPower();
                double newPhaseC = userData.getPhaseCPower();
                
                // 计算新旧总电量
                double oldTotal = oldPhaseA + oldPhaseB + oldPhaseC;
                double newTotal = newPhaseA + newPhaseB + newPhaseC;
                
                // 判断旧数据是否为动力用户
                boolean wasOldPowerUser = oldPhaseA > 0 && oldPhaseB > 0 && oldPhaseC > 0;
                
                // 判断是否是相位调整：
                // 1. 相位名称改变
                // 2. 动力用户状态变化
                // 3. 三相电量发生交换（相位调整）
                boolean phaseChanged = !oldPhase.equals(newPhase);
                boolean powerUserStatusChanged = wasOldPowerUser != isPowerUser;
                
                // 判断三相电量是否发生交换
                boolean phaseSwapped = false;
                
                // 判断总电量是否相近（允许5%的误差）
                boolean totalPowerSimilar = Math.abs(oldTotal - newTotal) < 0.05 * oldTotal;
                
                if (totalPowerSimilar) {
                    if (isPowerUser && wasOldPowerUser) {
                        // 动力用户的相位交换模式
                        // 检查是否存在顺时针调整（A->B, B->C, C->A）
                        boolean clockwiseSwap = 
                            (Math.abs(oldPhaseA - newPhaseB) < 0.05 * oldPhaseA) && 
                            (Math.abs(oldPhaseB - newPhaseC) < 0.05 * oldPhaseB) && 
                            (Math.abs(oldPhaseC - newPhaseA) < 0.05 * oldPhaseC);
                        
                        // 检查是否存在逆时针调整（A->C, B->A, C->B）
                        boolean counterClockwiseSwap = 
                            (Math.abs(oldPhaseA - newPhaseC) < 0.05 * oldPhaseA) && 
                            (Math.abs(oldPhaseB - newPhaseA) < 0.05 * oldPhaseB) && 
                            (Math.abs(oldPhaseC - newPhaseB) < 0.05 * oldPhaseC);
                        
                        phaseSwapped = clockwiseSwap || counterClockwiseSwap;
                    } else if (!isPowerUser && !wasOldPowerUser) {
                        // 普通用户的相位交换
                        // 计算A相、B相、C相是否在新旧数据中交换了位置
                        
                        // 检查是否从A相切换
                        if (oldPhaseA > 0) {
                            phaseSwapped = (oldPhaseA > 0.05 && Math.abs(oldPhaseA) > 0.05) && 
                                          ((Math.abs(oldPhaseA - newPhaseB) < 0.05 * oldPhaseA) ||
                                           (Math.abs(oldPhaseA - newPhaseC) < 0.05 * oldPhaseA));
                        }
                        
                        // 检查是否从B相切换
                        else if (oldPhaseB > 0) {
                            phaseSwapped = (oldPhaseB > 0.05 && Math.abs(oldPhaseB) > 0.05) && 
                                          ((Math.abs(oldPhaseB - newPhaseA) < 0.05 * oldPhaseB) ||
                                           (Math.abs(oldPhaseB - newPhaseC) < 0.05 * oldPhaseB));
                        }
                        
                        // 检查是否从C相切换
                        else if (oldPhaseC > 0) {
                            phaseSwapped = (oldPhaseC > 0.05 && Math.abs(oldPhaseC) > 0.05) && 
                                          ((Math.abs(oldPhaseC - newPhaseA) < 0.05 * oldPhaseC) ||
                                           (Math.abs(oldPhaseC - newPhaseB) < 0.05 * oldPhaseC));
                        }
                    }
                }
                
                // 如果满足相位调整的条件，记录到旧数据表
                if (phaseChanged || powerUserStatusChanged || phaseSwapped)
                {
                    String adjustmentReason = phaseChanged ? "相位名称变化" : 
                                              powerUserStatusChanged ? "动力用户状态变化" : 
                                              "三相电量交换";
                    
                    Log.d("DatabaseHelper", String.format(
                        "检测到相位调整 - 用户ID:%s, 日期:%s, 原因:%s, " + 
                        "旧电量:[%.2f, %.2f, %.2f], 新电量:[%.2f, %.2f, %.2f]",
                        userId, userData.getDate(), adjustmentReason,
                        oldPhaseA, oldPhaseB, oldPhaseC, newPhaseA, newPhaseB, newPhaseC
                    ));
                    
                    ContentValues oldDataValues = new ContentValues();
                    oldDataValues.put(COLUMN_DATE, userData.getDate());
                    oldDataValues.put(COLUMN_USER_ID, userId);
                    oldDataValues.put(COLUMN_OLD_PHASE, oldPhase);
                    oldDataValues.put(COLUMN_NEW_PHASE, newPhase);
                    oldDataValues.put(COLUMN_PHASE_A_POWER, oldPhaseA);
                    oldDataValues.put(COLUMN_PHASE_B_POWER, oldPhaseB);
                    oldDataValues.put(COLUMN_PHASE_C_POWER, oldPhaseC);
                    oldDataValues.put("is_power_user", wasOldPowerUser ? 1 : 0);  // 记录旧的动力用户状态
                    
                    // 获取该日期的三相总电量和
                    Cursor totalCursor = db.query(
                        TABLE_TOTAL_POWER,
                        new String[]{COLUMN_TOTAL_PHASE_A, COLUMN_TOTAL_PHASE_B, COLUMN_TOTAL_PHASE_C},
                        COLUMN_DATE + "=?",
                        new String[]{userData.getDate()},
                        null, null, null
                    );
                    
                    if (totalCursor.moveToFirst()) {
                        // 获取到该日期的三相电量总和
                        double totalA = totalCursor.getDouble(totalCursor.getColumnIndex(COLUMN_TOTAL_PHASE_A));
                        double totalB = totalCursor.getDouble(totalCursor.getColumnIndex(COLUMN_TOTAL_PHASE_B));
                        double totalC = totalCursor.getDouble(totalCursor.getColumnIndex(COLUMN_TOTAL_PHASE_C));
                        
                        // 将三相总电量和保存到旧数据记录中
                        oldDataValues.put("total_a_sum", totalA);
                        oldDataValues.put("total_b_sum", totalB);
                        oldDataValues.put("total_c_sum", totalC);
                        
                        Log.d("DatabaseHelper", String.format(
                            "保存调整前三相总电量和 - 用户ID:%s, 日期:%s, A相:%.2f, B相:%.2f, C相:%.2f",
                            userId, userData.getDate(), totalA, totalB, totalC
                        ));
                    }
                    totalCursor.close();
                    
                    // 插入或替换旧数据记录
                    db.insertWithOnConflict(
                        TABLE_OLD_DATA,
                        null,
                        oldDataValues,
                        SQLiteDatabase.CONFLICT_REPLACE
                    );
                }
            }
            cursor.close();
            
            // 更新或插入用户功率数据
            ContentValues values = new ContentValues();
            values.put(COLUMN_DATE, userData.getDate());
            values.put(COLUMN_PHASE, userData.getPhase());
            values.put(COLUMN_PHASE_A_POWER, userData.getPhaseAPower());
            values.put(COLUMN_PHASE_B_POWER, userData.getPhaseBPower());
            values.put(COLUMN_PHASE_C_POWER, userData.getPhaseCPower());
            
            db.insertWithOnConflict(userDataTable, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
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

        // 检查是否有需要更新三相总电量和的旧数据记录
        String checkQuery = "SELECT COUNT(*) FROM " + TABLE_OLD_DATA + " WHERE " + COLUMN_DATE + " = ? AND total_a_sum IS NULL";
        Cursor checkCursor = db.rawQuery(checkQuery, new String[]{date});
        
        if (checkCursor.moveToFirst() && checkCursor.getInt(0) > 0) {
            Log.d("DatabaseHelper", "发现需要更新三相总电量和的旧数据记录，日期: " + date);
            
            // 更新该日期所有旧数据记录的三相总电量和
            ContentValues updateValues = new ContentValues();
            updateValues.put("total_a_sum", totalA);
            updateValues.put("total_b_sum", totalB);
            updateValues.put("total_c_sum", totalC);
            
            int updatedRows = db.update(
                TABLE_OLD_DATA,
                updateValues,
                COLUMN_DATE + " = ?",
                new String[]{date}
            );
            
            Log.d("DatabaseHelper", String.format(
                "已更新%d条旧数据记录的三相总电量和 - A相: %.2f, B相: %.2f, C相: %.2f",
                updatedRows, totalA, totalB, totalC
            ));
        }
        checkCursor.close();

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
            
            // 删除支线组表中的所有数据
            db.delete(TABLE_BRANCH_GROUP, null, null);
            
            // 删除旧相位调整历史表中的所有数据
            db.delete(TABLE_OLD_DATA, null, null);
            
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

    // 判断是否为动力用户
    public boolean isPowerUser(String userId)
    {
        try
        {
            // 获取用户最近一天的数据
            List<UserData> userDataList = getUserLastNDaysData(userId, 1);
            if (!userDataList.isEmpty())
            {
                UserData userData = userDataList.get(0);
                // 判断是否为动力用户：三相都有电量
                return userData.getPhaseAPower() > 0 && 
                       userData.getPhaseBPower() > 0 && 
                       userData.getPhaseCPower() > 0;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }

    // 获取所有可用的支线编号
    public List<String> getAllBranchNumbers() 
    {
        List<String> branchNumbers = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try 
        {
            db = this.getReadableDatabase();
            String query = "SELECT DISTINCT " + COLUMN_BRANCH_NUMBER + 
                         " FROM " + TABLE_USER_INFO + 
                         " WHERE " + COLUMN_BRANCH_NUMBER + " != '0'" +
                         " ORDER BY " + COLUMN_BRANCH_NUMBER + " ASC";
            
            cursor = db.rawQuery(query, null);
            
            while (cursor.moveToNext()) 
            {
                String branchNumber = cursor.getString(0);
                branchNumbers.add(branchNumber);
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
        
        return branchNumbers;
    }

    // 根据支线编号获取所有回路
    public List<String> getRouteNumbersByBranchNumber(String branchNumber) 
    {
        List<String> routeNumbers = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try 
        {
            db = this.getReadableDatabase();
            String query = "SELECT DISTINCT " + COLUMN_ROUTE_NUMBER + 
                         " FROM " + TABLE_USER_INFO + 
                         " WHERE " + COLUMN_BRANCH_NUMBER + " = ?" +
                         " ORDER BY " + COLUMN_ROUTE_NUMBER + " ASC";
            
            cursor = db.rawQuery(query, new String[]{branchNumber});
            
            while (cursor.moveToNext()) 
            {
                String routeNumber = cursor.getString(0);
                routeNumbers.add(routeNumber);
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
        
        return routeNumbers;
    }

    // 添加多个支线组
    public boolean addBranchGroups(String branchNumber, List<String> routeNumbers) 
    {
        SQLiteDatabase db = null;
        boolean success = true;
        
        try 
        {
            db = this.getWritableDatabase();
            db.beginTransaction();
            
            for (String routeNumber : routeNumbers) 
            {
                ContentValues values = new ContentValues();
                values.put(COLUMN_ROUTE_NUMBER, routeNumber);
                values.put(COLUMN_BRANCH_NUMBER, branchNumber);
                
                long result = db.insertWithOnConflict(
                    TABLE_BRANCH_GROUP,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                );
                
                if (result == -1) 
                {
                    success = false;
                    break;
                }
            }
            
            if (success) 
            {
                db.setTransactionSuccessful();
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            success = false;
        } 
        finally 
        {
            if (db != null) 
            {
                db.endTransaction();
                db.close();
            }
        }
        
        return success;
    }

    // 检查日期是否有相位调整记录
    public boolean hasPhaseAdjustmentOnDate(String date)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        
        try
        {
            cursor = db.query(
                TABLE_OLD_DATA,
                new String[]{COLUMN_DATE},
                COLUMN_DATE + "=?",
                new String[]{date},
                null, null, null,
                "1" // 限制结果为1条
            );
            
            return cursor != null && cursor.moveToFirst();
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
    }
    
    // 检查特定用户在指定日期是否有相位调整记录
    public boolean hasUserPhaseAdjustmentOnDate(String userId, String date)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        
        try
        {
            cursor = db.query(
                TABLE_OLD_DATA,
                new String[]{COLUMN_DATE},
                COLUMN_DATE + "=? AND " + COLUMN_USER_ID + "=?",
                new String[]{date, userId},
                null, null, null,
                "1" // 限制结果为1条
            );
            
            return cursor != null && cursor.moveToFirst();
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
    }
    
    // 获取用户的相位调整历史记录，包含三相电量
    public List<Map<String, Object>> getUserPhaseAdjustmentHistory(String userId)
    {
        List<Map<String, Object>> history = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        
        try
        {
            cursor = db.query(
                TABLE_OLD_DATA,
                new String[]{
                    COLUMN_DATE, COLUMN_OLD_PHASE, COLUMN_NEW_PHASE,
                    COLUMN_PHASE_A_POWER, COLUMN_PHASE_B_POWER, COLUMN_PHASE_C_POWER,
                    "is_power_user", "total_a_sum", "total_b_sum", "total_c_sum"
                },
                COLUMN_USER_ID + "=?",
                new String[]{userId},
                null, null,
                COLUMN_DATE + " DESC" // 按日期降序排序
            );
            
            if (cursor != null && cursor.moveToFirst())
            {
                do
                {
                    Map<String, Object> record = new HashMap<>();
                    record.put("date", cursor.getString(cursor.getColumnIndex(COLUMN_DATE)));
                    record.put("oldPhase", cursor.getString(cursor.getColumnIndex(COLUMN_OLD_PHASE)));
                    record.put("newPhase", cursor.getString(cursor.getColumnIndex(COLUMN_NEW_PHASE)));
                    record.put("phaseAPower", cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_A_POWER)));
                    record.put("phaseBPower", cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_B_POWER)));
                    record.put("phaseCPower", cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_C_POWER)));
                    record.put("isPowerUser", cursor.getInt(cursor.getColumnIndex("is_power_user")) == 1);
                    
                    // 获取调整前三相电量和
                    int totalAIndex = cursor.getColumnIndex("total_a_sum");
                    int totalBIndex = cursor.getColumnIndex("total_b_sum");
                    int totalCIndex = cursor.getColumnIndex("total_c_sum");
                    
                    if (totalAIndex >= 0 && totalBIndex >= 0 && totalCIndex >= 0)
                    {
                        record.put("totalASum", cursor.getDouble(totalAIndex));
                        record.put("totalBSum", cursor.getDouble(totalBIndex));
                        record.put("totalCSum", cursor.getDouble(totalCIndex));
                    }
                    
                    history.add(record);
                } while (cursor.moveToNext());
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
        
        return history;
    }
    
    // 获取指定日期有相位调整的用户列表
    public List<String> getUsersWithPhaseAdjustmentOnDate(String date)
    {
        List<String> users = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        
        try
        {
            cursor = db.query(
                TABLE_OLD_DATA,
                new String[]{COLUMN_USER_ID},
                COLUMN_DATE + "=?",
                new String[]{date},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst())
            {
                do
                {
                    users.add(cursor.getString(cursor.getColumnIndex(COLUMN_USER_ID)));
                } while (cursor.moveToNext());
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
        
        return users;
    }

    // 获取用户在指定日期的相位调整记录
    public Map<String, Object> getUserPhaseAdjustment(String userId, String date)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        Map<String, Object> result = new HashMap<>();
        
        try
        {
            cursor = db.query(
                TABLE_OLD_DATA,
                new String[]{
                    COLUMN_OLD_PHASE, COLUMN_NEW_PHASE, 
                    COLUMN_PHASE_A_POWER, COLUMN_PHASE_B_POWER, COLUMN_PHASE_C_POWER,
                    "is_power_user", "total_a_sum", "total_b_sum", "total_c_sum"
                },
                COLUMN_DATE + "=? AND " + COLUMN_USER_ID + "=?",
                new String[]{date, userId},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst())
            {
                result.put("oldPhase", cursor.getString(cursor.getColumnIndex(COLUMN_OLD_PHASE)));
                result.put("newPhase", cursor.getString(cursor.getColumnIndex(COLUMN_NEW_PHASE)));
                result.put("phaseAPower", cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_A_POWER)));
                result.put("phaseBPower", cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_B_POWER)));
                result.put("phaseCPower", cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_C_POWER)));
                result.put("isPowerUser", cursor.getInt(cursor.getColumnIndex("is_power_user")) == 1);
                
                // 获取调整前三相电量和
                int totalAIndex = cursor.getColumnIndex("total_a_sum");
                int totalBIndex = cursor.getColumnIndex("total_b_sum");
                int totalCIndex = cursor.getColumnIndex("total_c_sum");
                
                if (totalAIndex >= 0 && totalBIndex >= 0 && totalCIndex >= 0)
                {
                    result.put("totalASum", cursor.getDouble(totalAIndex));
                    result.put("totalBSum", cursor.getDouble(totalBIndex));
                    result.put("totalCSum", cursor.getDouble(totalCIndex));
                }
                
                return result;
            }
            return null;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
    }
    
    // 获取指定日期调整前的三相电量和
    public double[] getAdjustmentTotalPowers(String date)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        double[] totalPowers = new double[3]; // [A相总和, B相总和, C相总和]
        
        try
        {
            // 获取该日期任意一条记录即可，因为所有记录都存储了相同的三相电量和
            cursor = db.query(
                TABLE_OLD_DATA,
                new String[]{"total_a_sum", "total_b_sum", "total_c_sum"},
                COLUMN_DATE + "=?",
                new String[]{date},
                null, null, null,
                "1" // 限制结果为1条
            );
            
            if (cursor != null && cursor.moveToFirst())
            {
                int totalAIndex = cursor.getColumnIndex("total_a_sum");
                int totalBIndex = cursor.getColumnIndex("total_b_sum");
                int totalCIndex = cursor.getColumnIndex("total_c_sum");
                
                if (totalAIndex >= 0 && totalBIndex >= 0 && totalCIndex >= 0)
                {
                    totalPowers[0] = cursor.getDouble(totalAIndex);
                    totalPowers[1] = cursor.getDouble(totalBIndex);
                    totalPowers[2] = cursor.getDouble(totalCIndex);
                }
            }
            
            return totalPowers;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
    }
    
    // 获取用户在指定日期的动力用户状态
    public boolean getPowerUserStatus(String userId, String date)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        
        try
        {
            // 首先检查是否有相位调整记录
            Map<String, Object> adjustment = getUserPhaseAdjustment(userId, date);
            if (adjustment != null)
            {
                return (boolean) adjustment.get("isPowerUser");
            }
            
            // 如果没有调整记录，从用户数据表中查询
            String userDataTable = "user_data_" + userId;
            cursor = db.query(
                userDataTable,
                new String[]{COLUMN_PHASE_A_POWER, COLUMN_PHASE_B_POWER, COLUMN_PHASE_C_POWER},
                COLUMN_DATE + "=?",
                new String[]{date},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst())
            {
                double phaseA = cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_A_POWER));
                double phaseB = cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_B_POWER));
                double phaseC = cursor.getDouble(cursor.getColumnIndex(COLUMN_PHASE_C_POWER));
                
                // 判断是否为动力用户：三相都有电量
                return phaseA > 0 && phaseB > 0 && phaseC > 0;
            }
            
            return false;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            db.close();
        }
    }
} 