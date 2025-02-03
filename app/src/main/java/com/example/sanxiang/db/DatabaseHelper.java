package com.example.sanxiang.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.sanxiang.data.UserData;
import android.content.ContentValues;
import com.example.sanxiang.util.CsvHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "sanxiang.db";
    private static final int DATABASE_VERSION = 2;
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
        
        // 总电量表（添加平衡度字段）
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
        if (oldVersion < 2)
        {
            // 添加平衡度字段
            db.execSQL("ALTER TABLE " + TABLE_TOTAL_POWER + " ADD COLUMN unbalance_rate REAL");
            // 更新所有已有数据的平衡度
            updateAllUnbalanceRates(db);
        }
    }

    // 更新所有已有数据的平衡度
    private void updateAllUnbalanceRates(SQLiteDatabase db)
    {
        String query = "SELECT date, total_phase_a, total_phase_b, total_phase_c FROM " + TABLE_TOTAL_POWER;
        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext())
        {
            String date = cursor.getString(0);
            double phaseA = cursor.getDouble(1);
            double phaseB = cursor.getDouble(2);
            double phaseC = cursor.getDouble(3);

            double unbalanceRate = calculateUnbalanceRate(phaseA, phaseB, phaseC);

            ContentValues values = new ContentValues();
            values.put("unbalance_rate", unbalanceRate);
            db.update(TABLE_TOTAL_POWER, values, "date = ?", new String[]{date});
        }
        cursor.close();
    }

    // 计算三相不平衡度
    private double calculateUnbalanceRate(double phaseA, double phaseB, double phaseC)
    {
        double avg = (phaseA + phaseB + phaseC) / 3.0;
        if (avg == 0) return 0;

        double maxDiff = Math.max(Math.abs(phaseA - avg), 
            Math.max(Math.abs(phaseB - avg), Math.abs(phaseC - avg)));
        return (maxDiff / avg) * 100;
    }

    // 更新或插入总电量（包括平衡度）
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

            // 如果记录数达到30，将最早的记录移动到CSV文件
            if (count >= 30)
            {
                String oldestQuery = "SELECT * FROM " + TABLE_TOTAL_POWER + 
                        " ORDER BY date ASC LIMIT 1";
                Cursor oldestCursor = db.rawQuery(oldestQuery, null);
                
                if (oldestCursor.moveToFirst())
                {
                    String oldDate = oldestCursor.getString(0);
                    double oldTotalA = oldestCursor.getDouble(1);
                    double oldTotalB = oldestCursor.getDouble(2);
                    double oldTotalC = oldestCursor.getDouble(3);
                    double oldUnbalanceRate = oldestCursor.getDouble(4);

                    // 保存到CSV文件
                    CsvHelper.saveTotalPowerToCsv(context, oldDate, 
                        oldTotalA, oldTotalB, oldTotalC, oldUnbalanceRate);

                    // 删除最早的记录
                    db.delete(TABLE_TOTAL_POWER, "date = ?", new String[]{oldDate});
                }
                oldestCursor.close();
            }

            // 更新或插入新记录
            ContentValues values = new ContentValues();
            values.put("date", date);
            values.put("total_phase_a", totalA);
            values.put("total_phase_b", totalB);
            values.put("total_phase_c", totalC);
            values.put("unbalance_rate", unbalanceRate);

            db.insertWithOnConflict(TABLE_TOTAL_POWER, null, values, 
                SQLiteDatabase.CONFLICT_REPLACE);
        }
        cursor.close();
    }

    // 插入数据，超过30条的旧数据移动到CSV文件
    public void insertData(UserData data)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try
        {
            // 检查用户记录数
            String countQuery = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE user_id = ?";
            Cursor cursor = db.rawQuery(countQuery, new String[]{data.getUserId()});
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();

            // 如果记录数达到30，将最早的记录移动到CSV文件
            if (count >= 30)
            {
                // 获取最早的记录
                String oldestQuery = "SELECT * FROM " + TABLE_NAME + 
                        " WHERE user_id = ? ORDER BY date ASC LIMIT 1";
                cursor = db.rawQuery(oldestQuery, new String[]{data.getUserId()});
                
                if (cursor.moveToFirst())
                {
                    // 创建UserData对象
                    UserData oldData = new UserData();
                    oldData.setDate(cursor.getString(cursor.getColumnIndexOrThrow("date")));
                    oldData.setUserId(cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
                    oldData.setUserName(cursor.getString(cursor.getColumnIndexOrThrow("user_name")));
                    oldData.setRouteNumber(cursor.getString(cursor.getColumnIndexOrThrow("route_number")));
                    oldData.setRouteName(cursor.getString(cursor.getColumnIndexOrThrow("route_name")));
                    oldData.setPhase(cursor.getString(cursor.getColumnIndexOrThrow("phase")));
                    oldData.setPhaseAPower(cursor.getDouble(cursor.getColumnIndexOrThrow("phase_a_power")));
                    oldData.setPhaseBPower(cursor.getDouble(cursor.getColumnIndexOrThrow("phase_b_power")));
                    oldData.setPhaseCPower(cursor.getDouble(cursor.getColumnIndexOrThrow("phase_c_power")));

                    // 保存到CSV文件
                    List<UserData> oldDataList = new ArrayList<>();
                    oldDataList.add(oldData);
                    CsvHelper.saveUserDataToCsv(context, oldDataList);

                    // 删除最早的记录
                    String deleteOldest = "DELETE FROM " + TABLE_NAME + 
                            " WHERE user_id = ? AND date = ?";
                    db.execSQL(deleteOldest, 
                        new String[]{data.getUserId(), oldData.getDate()});
                }
                cursor.close();
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
            
            db.insertWithOnConflict(TABLE_NAME, null, values, 
                SQLiteDatabase.CONFLICT_REPLACE);

            // 更新该日期的总电量
            updateTotalPower(data.getDate(), db);

            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    // 获取最新日期
    public String getLatestDate()
    {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT date FROM " + TABLE_NAME + " ORDER BY date DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, null);


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

    // 获取前一天的日期
    public String getPrevDate(String currentDate)
    {
        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT DISTINCT date FROM " + TABLE_NAME 
                + " WHERE date < ? ORDER BY date DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, new String[]{currentDate});

        String date = null;
        if (cursor.moveToFirst())
        {
            date = cursor.getString(0);
        }
        cursor.close();
        return date;
    }

    // 获取下一天的日期
    public String getNextDate(String currentDate)
    {
        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT DISTINCT date FROM " + TABLE_NAME 
                + " WHERE date > ? ORDER BY date ASC LIMIT 1";
        Cursor cursor = db.rawQuery(query, new String[]{currentDate});

        String date = null;
        if (cursor.moveToFirst())
        {
            date = cursor.getString(0);
        }
        cursor.close();
        return date;
    }

    // 获取指定日期的三相总电量和平衡度
    public double[] getTotalPowerByDate(String date)
    {
        double[] totalPower = new double[4];  // 增加一个元素存储平衡度
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT total_phase_a, total_phase_b, total_phase_c, unbalance_rate "
                + "FROM " + TABLE_TOTAL_POWER + " WHERE date = ?";
        Cursor cursor = db.rawQuery(query, new String[]{date});

        if (cursor.moveToFirst())
        {
            totalPower[0] = cursor.getDouble(0);
            totalPower[1] = cursor.getDouble(1);
            totalPower[2] = cursor.getDouble(2);
            totalPower[3] = cursor.getDouble(3);  // 平衡度
        }
        cursor.close();
        return totalPower;
    }

    // 获取最近7天的日期列表
    public List<String> getLastSevenDays()
    {
        List<String> dates = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT DISTINCT date FROM " + TABLE_TOTAL_POWER +
                " ORDER BY date DESC LIMIT 7";

        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext())
        {
            dates.add(cursor.getString(0));
        }

        cursor.close();
        return dates;
    }

    // 获取某用户最近30天的用户数据
    public List<UserData> getLastThirtyDaysData(String userId)
    {
        List<UserData> dataList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_NAME + 
                " WHERE user_id = ? ORDER BY date DESC LIMIT 30";
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

    
} 