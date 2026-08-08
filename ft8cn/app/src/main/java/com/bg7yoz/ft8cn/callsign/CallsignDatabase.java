package com.bg7yoz.ft8cn.callsign;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.bg7yoz.ft8cn.AppExecutors;
import com.bg7yoz.ft8cn.Ft8Message;
import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 呼号数据库操作类
 * 已优化：移除 AsyncTask，使用 ExecutorService 和 Handler。
 */
public class CallsignDatabase extends android.database.sqlite.SQLiteOpenHelper {
    private static final String TAG = "CallsignDatabase";
    private static CallsignDatabase instance;
    private final Context context;
    private SQLiteDatabase db;

    private static final ExecutorService dbExecutor = AppExecutors.getInstance().diskIO();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static CallsignDatabase getInstance(@Nullable Context context, @Nullable String databaseName, int version) {
        if (instance == null) {
            instance = new CallsignDatabase(context, databaseName, null, version);
        }
        return instance;
    }

    public CallsignDatabase(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        this.context = context != null ? context.getApplicationContext() : null;
        db = getWritableDatabase();
    }

    public SQLiteDatabase getDb() {
        return db;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        Log.d(TAG, "Create database.");
        db = sqLiteDatabase;
        createTables();
        dbExecutor.execute(new InitDatabase(context, db));
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    public void createTables() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS callsigns (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "countryId INTEGER," +
                "callsign TEXT" +
                ")";
        db.execSQL(createTableSQL);
        db.execSQL("CREATE INDEX IF NOT EXISTS callsign_index ON callsigns (callsign)");

        createTableSQL = "CREATE TABLE IF NOT EXISTS countries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "CountryNameEn TEXT," +
                "CountryNameCN TEXT," +
                "CQZone INTEGER," +
                "ITUZone INTEGER," +
                "Continent TEXT," +
                "Latitude REAL," +
                "Longitude REAL," +
                "GMT_offset REAL," +
                "DXCC TEXT" +
                ")";
        db.execSQL(createTableSQL);
    }

    public void getCallsignInformation(String callsign, OnAfterQueryCallsignLocation afterQueryCallsignLocation) {
        dbExecutor.execute(new QueryCallsignInformation(db, callsign, afterQueryCallsignLocation));
    }

    public CallsignInfo getCallInfo(String callsign) {
        return getCallsignInfo(db, callsign);
    }

    public static synchronized void getMessagesLocation(SQLiteDatabase db, ArrayList<Ft8Message> ft8Messages) {
        getMessagesLocation(db, ft8Messages, true);
    }

    public static synchronized void getMessagesLocationWithoutPriority(
            SQLiteDatabase db, ArrayList<Ft8Message> ft8Messages) {
        getMessagesLocation(db, ft8Messages, false);
    }

    public static synchronized void getMessagesPriority(SQLiteDatabase db,
                                                         ArrayList<Ft8Message> ft8Messages) {
        if (ft8Messages == null) return;
        ArrayList<Ft8Message> messages = new ArrayList<>(ft8Messages);//防止线程访问冲突

        for (Ft8Message msg : messages) {
            if (msg.i3 == 0 && msg.n3 == 0) continue;//如果是自由文本，就不查了
            if (msg.callsignFrom != null) {
                CallsignInfo fromCallsignInfo = getCallsignInfo(db,
                        msg.callsignFrom.replace("<", "").replace(">", ""));
                if (fromCallsignInfo != null) {
                    updateMessagePriority(msg, fromCallsignInfo);
                }
            }
        }
    }

    private static synchronized void getMessagesLocation(SQLiteDatabase db,
                                                           ArrayList<Ft8Message> ft8Messages,
                                                           boolean calculatePriority) {
        if (ft8Messages == null) return;
        ArrayList<Ft8Message> messages = new ArrayList<>(ft8Messages);//防止线程访问冲突

        for (Ft8Message msg : messages) {
            if (msg.i3 == 0 && msg.n3 == 0) continue;//如果是自由文本，就不查了
            if (msg.callsignFrom != null) {
                CallsignInfo fromCallsignInfo = getCallsignInfo(db,
                        msg.callsignFrom.replace("<", "").replace(">", ""));
                if (fromCallsignInfo != null) {
                    msg.fromDxcc = !GeneralVariables.getDxccByPrefix(fromCallsignInfo.DXCC);
                    msg.fromItu = !GeneralVariables.getItuZoneById(fromCallsignInfo.ITUZone);
                    msg.fromCq = !GeneralVariables.getCqZoneById(fromCallsignInfo.CQZone);
                    if (GeneralVariables.isChina) {
                        msg.fromWhere = fromCallsignInfo.CountryNameCN;
                    } else {
                        msg.fromWhere = fromCallsignInfo.CountryNameEn;
                    }
                    //CTY.DAT的经度是西经为正，所以要取反
                    msg.fromLatLng = new com.google.android.gms.maps.model.LatLng(
                            fromCallsignInfo.Latitude, fromCallsignInfo.Longitude * -1);

                    if (calculatePriority) {
                        updateMessagePriority(msg, fromCallsignInfo);
                    }
                }
            }

            if (msg.checkIsCQ() || msg.getCallsignTo().contains("...")) {//CQ就不查了
                continue;
            }

            if (msg.callsignTo != null) {
                CallsignInfo toCallsignInfo = getCallsignInfo(db,
                        msg.callsignTo.replace("<", "").replace(">", ""));
                if (toCallsignInfo != null) {
                    msg.toDxcc = !GeneralVariables.getDxccByPrefix(toCallsignInfo.DXCC);
                    msg.toItu = !GeneralVariables.getItuZoneById(toCallsignInfo.ITUZone);
                    msg.toCq = !GeneralVariables.getCqZoneById(toCallsignInfo.CQZone);
                    if (GeneralVariables.isChina) {
                        msg.toWhere = toCallsignInfo.CountryNameCN;
                    } else {
                        msg.toWhere = toCallsignInfo.CountryNameEn;
                    }
                    msg.toLatLng = new com.google.android.gms.maps.model.LatLng(
                            toCallsignInfo.Latitude, toCallsignInfo.Longitude * -1);
                }
            }
        }
    }

    private static void updateMessagePriority(Ft8Message msg, CallsignInfo info) {
        String dxcc = info.DXCC;
        String call = msg.getCallsignFrom();
        if (call == null) return;
        String prefix = GeneralVariables.getShortCallsign(call);

        // 1. Session Stats Tracking (For Rarity Calculation)
        if (dxcc != null) {
            Integer dxccCount = GeneralVariables.sessionDxccCount.get(dxcc);
            GeneralVariables.sessionDxccCount.put(dxcc, dxccCount == null ? 1 : dxccCount + 1);
        }
        Integer prefixCount = GeneralVariables.sessionPrefixCount.get(prefix);
        GeneralVariables.sessionPrefixCount.put(prefix, prefixCount == null ? 1 : prefixCount + 1);
        
        Integer callCount = GeneralVariables.sessionCallCount.get(call);
        GeneralVariables.sessionCallCount.put(call, callCount == null ? 1 : callCount + 1);

        // 2. Worked Tracking (Permanent Logs)
        boolean workedDxcc = GeneralVariables.getDxccByPrefix(dxcc);
        boolean workedBand = GeneralVariables.isWorkedDxccOnBand(dxcc, msg.band);
        boolean workedPrefix = GeneralVariables.workedPrefixes.contains(prefix);
        boolean workedCall = GeneralVariables.checkQSLCallsign(call) || GeneralVariables.checkQSLCallsign_OtherBand(call);

        // 3. JTDX Style Priority Logic (Highest first)
        if (dxcc != null && !workedDxcc) {
            msg.priority = Ft8Message.Priority.NEW_DXCC;
        } else if (dxcc != null && !workedBand) {
            msg.priority = Ft8Message.Priority.NEW_BAND;
        } else if (!workedPrefix) {
            msg.priority = Ft8Message.Priority.NEW_PREFIX;
        } else if (!workedCall) {
            msg.priority = Ft8Message.Priority.NEW_CALLSIGN;
        }

        // 4. Dynamic Rarity Fallback (When logs are thin)
        if (msg.priority == Ft8Message.Priority.NONE || msg.priority == Ft8Message.Priority.NEW_CALLSIGN) {
            // Check if this DXCC is rare in this session
            if (dxcc != null) {
                Integer sessionCount = GeneralVariables.sessionDxccCount.get(dxcc);
                if (sessionCount != null && sessionCount <= 2) { 
                    // Calculate distance if grids are available
                    String myGrid = GeneralVariables.getMyMaidenheadGrid();
                    String hisGrid = msg.maidenGrid;
                    if (hisGrid != null && !hisGrid.isEmpty() && !myGrid.isEmpty()) {
                        msg.distanceKm = (float) (MaidenheadGrid.getDist(myGrid, hisGrid) / 1000.0);
                        if (msg.distanceKm > 5000) { // Distance > 5000km and rare in session
                            msg.priority = Ft8Message.Priority.RARE_DX;
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("Range")
    public static CallsignInfo getCallsignInfo(SQLiteDatabase db, String sqlParameter) {
        //callsigns表里存的是CTY.DAT的前缀，必须做前缀匹配（取最长命中），"="开头的是完整呼号精确匹配
        String querySQL = "select a.*,b.* from callsigns as a left join countries as b on a.countryId =b.id \n" +
                "WHERE (SUBSTR(?,1,LENGTH(callsign))=callsign) OR (callsign=\"=\"||?)\n" +
                "order by LENGTH(callsign) desc\n" +
                "LIMIT 1";

        Cursor cursor = db.rawQuery(querySQL, new String[]{sqlParameter.toUpperCase(), sqlParameter.toUpperCase()});
        CallsignInfo callsignInfo = null;
        if (cursor.moveToFirst()) {
            callsignInfo = new CallsignInfo();
            callsignInfo.CallSign = sqlParameter;
            callsignInfo.CountryNameEn = cursor.getString(cursor.getColumnIndex("CountryNameEn"));
            callsignInfo.CountryNameCN = cursor.getString(cursor.getColumnIndex("CountryNameCN"));
            callsignInfo.CQZone = cursor.getInt(cursor.getColumnIndex("CQZone"));
            callsignInfo.ITUZone = cursor.getInt(cursor.getColumnIndex("ITUZone"));
            callsignInfo.Continent = cursor.getString(cursor.getColumnIndex("Continent"));
            callsignInfo.Latitude = cursor.getFloat(cursor.getColumnIndex("Latitude"));
            callsignInfo.Longitude = cursor.getFloat(cursor.getColumnIndex("Longitude"));
            callsignInfo.GMT_offset = cursor.getFloat(cursor.getColumnIndex("GMT_offset"));
            callsignInfo.DXCC = cursor.getString(cursor.getColumnIndex("DXCC"));
        }
        cursor.close();
        return callsignInfo;
    }

    static class QueryCallsignInformation implements Runnable {
        private final SQLiteDatabase db;
        private final String sqlParameter;
        private final OnAfterQueryCallsignLocation afterQueryCallsignLocation;

        public QueryCallsignInformation(SQLiteDatabase db, String sqlParameter, OnAfterQueryCallsignLocation afterQueryCallsignLocation) {
            this.db = db;
            this.sqlParameter = sqlParameter;
            this.afterQueryCallsignLocation = afterQueryCallsignLocation;
        }

        @Override
        public void run() {
            final CallsignInfo callsignInfo = CallsignDatabase.getCallsignInfo(db, sqlParameter);
            if (callsignInfo == null) return;//查不到就不回调，调用方不做null检查
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (afterQueryCallsignLocation != null) {
                        afterQueryCallsignLocation.doOnAfterQueryCallsignLocation(callsignInfo);
                    }
                }
            });
        }
    }

    static class InitDatabase implements Runnable {
        private final Context context;
        private final SQLiteDatabase db;

        public InitDatabase(Context context, SQLiteDatabase db) {
            this.context = context;
            this.db = db;
        }

        @Override
        public void run() {
            Log.d(TAG, "开始导入呼号位置数据...");
            String insertCountriesSQL = "INSERT INTO countries (id,CountryNameEn,CountryNameCN,CQZone" +
                    ",ITUZone,Continent,Latitude,Longitude,GMT_offset,DXCC)\n" +
                    "VALUES(?,?,?,?,?,?,?,?,?,?)";

            ArrayList<CallsignInfo> callsignInfos = CallsignFileOperation.getCallSingInfoFromFile(context);
            ContentValues values = new ContentValues();
            db.beginTransaction();
            try {
                for (int i = 0; i < callsignInfos.size(); i++) {
                    db.execSQL(insertCountriesSQL, new Object[]{
                            i,
                            callsignInfos.get(i).CountryNameEn,
                            callsignInfos.get(i).CountryNameCN,
                            callsignInfos.get(i).CQZone,
                            callsignInfos.get(i).ITUZone,
                            callsignInfos.get(i).Continent,
                            callsignInfos.get(i).Latitude,
                            callsignInfos.get(i).Longitude,
                            callsignInfos.get(i).GMT_offset,
                            callsignInfos.get(i).DXCC});
                    Set<String> calls = CallsignFileOperation.getCallsigns(callsignInfos.get(i).CallSign);

                    for (String s : calls) {
                        values.put("countryId", i);
                        values.put("callsign", s);
                        db.insert("callsigns", null, values);
                        values.clear();
                    }
                }
                db.setTransactionSuccessful();
                Log.d(TAG, "导入呼号位置数据完成。");
            } catch (Exception e) {
                Log.e(TAG, "错误：" + e.getMessage());
            } finally {
                db.endTransaction();
            }
        }
    }
}
