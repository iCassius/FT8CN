package com.bg7yoz.ft8cn.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.bg7yoz.ft8cn.AppExecutors;
import com.bg7yoz.ft8cn.Ft8Message;
import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.callsign.CallsignInfo;
import com.bg7yoz.ft8cn.ft8signal.FT8Package;
import com.bg7yoz.ft8cn.ft8transmit.GenerateFT8;
import com.bg7yoz.ft8cn.log.OnQueryQSLCallsign;
import com.bg7yoz.ft8cn.log.OnQueryQSLRecordCallsign;
import com.bg7yoz.ft8cn.log.QSLCallsignRecord;
import com.bg7yoz.ft8cn.log.QSLRecord;
import com.bg7yoz.ft8cn.log.QSLRecordStr;
import com.bg7yoz.ft8cn.rigs.BaseRigOperation;
import com.bg7yoz.ft8cn.timer.UtcTimer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 数据库操作类
 * 已加固：全面采用 Try-with-resources 管理 Cursor，防止在高负荷解码时发生内存或连接泄漏。
 */
public class DatabaseOpr extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseOpr";
    private static DatabaseOpr instance;
    private final Context context;
    private final SQLiteDatabase db;

    private static final ExecutorService dbExecutor = AppExecutors.getInstance().diskIO();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static DatabaseOpr getInstance(@Nullable Context context, @Nullable String databaseName) {
        if (instance == null) {
            instance = new DatabaseOpr(context != null ? context.getApplicationContext() : null
                    , databaseName, null, 15);
        }
        return instance;
    }

    public DatabaseOpr(@Nullable Context context, @Nullable String name,
                       @androidx.annotation.Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        this.context = context;
        db = this.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        createTables(sqLiteDatabase);
        createQSLTable(sqLiteDatabase);
        createIndex(sqLiteDatabase);
        createDxccTables(sqLiteDatabase);
        createItuTables(sqLiteDatabase);
        createCqZoneTables(sqLiteDatabase);
        createCallsignQTHTables(sqLiteDatabase);
        createSWLTables(sqLiteDatabase);

        loadDxccDataFromFile(sqLiteDatabase);
        loadItuDataFromFile(sqLiteDatabase);
        loadICqZoneDataFromFile(sqLiteDatabase);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) createQSLTable(sqLiteDatabase);
        if (oldVersion < 3) {
            createDxccTables(sqLiteDatabase);
            loadDxccDataFromFile(sqLiteDatabase);
        }
        if (oldVersion < 4) {
            createItuTables(sqLiteDatabase);
            loadItuDataFromFile(sqLiteDatabase);
            createCqZoneTables(sqLiteDatabase);
            loadICqZoneDataFromFile(sqLiteDatabase);
        }
        if (oldVersion < 5) createCallsignQTHTables(sqLiteDatabase);
        if (oldVersion < 6) createSWLTables(sqLiteDatabase);
        if (oldVersion < 15) sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS swl_messages_band_index ON SWLMessages (BAND)");
    }

    public SQLiteDatabase getDb() {
        return db;
    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS config (KeyName TEXT PRIMARY KEY, Value TEXT)");
    }

    private void createQSLTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS QslCallsigns (id INTEGER PRIMARY KEY AUTOINCREMENT, callsign TEXT, isQSL INTEGER, isLotW_import INTEGER, isLotW_QSL INTEGER, startTime INTEGER, finishTime INTEGER, mode TEXT, grid TEXT, band TEXT, band_i INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS QSLTable (id INTEGER PRIMARY KEY AUTOINCREMENT, call TEXT, isQSL INTEGER, isLotW_import INTEGER, isLotW_QSL INTEGER, gridsquare TEXT, mode TEXT, rst_sent TEXT, rst_rcvd TEXT, qso_date TEXT, time_on TEXT, qso_date_off TEXT, time_off TEXT, band TEXT, freq TEXT, station_callsign TEXT, my_gridsquare TEXT, operator TEXT, comment TEXT)");
    }

    private void createDxccTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dxccList (dxcc INTEGER PRIMARY KEY, name TEXT, aname TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS dxcc_grid (grid TEXT PRIMARY KEY, dxcc INTEGER)");
    }

    private void createItuTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ituList (grid TEXT PRIMARY KEY, itu INTEGER)");
    }

    private void createCqZoneTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cqzoneList (grid TEXT PRIMARY KEY, cqzone INTEGER)");
    }

    private void createCallsignQTHTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS CallsignQTH (callsign TEXT PRIMARY KEY, grid TEXT, updateTime INTEGER)");
    }

    private void createSWLTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS SWLMessages (id INTEGER PRIMARY KEY AUTOINCREMENT, I3 INTEGER, N3 INTEGER, Protocol TEXT, UTC TEXT, SNR INTEGER, TIME_SEC REAL, FREQ INTEGER, CALL_FROM TEXT, CALL_TO TEXT, EXTRAL TEXT, REPORT INTEGER, BAND INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS SWLQSOTable (id INTEGER PRIMARY KEY AUTOINCREMENT, [call] TEXT, gridsquare TEXT, mode TEXT, rst_sent TEXT, rst_rcvd TEXT, qso_date TEXT, time_on TEXT, qso_date_off TEXT, time_off TEXT, band TEXT, freq TEXT, station_callsign TEXT, my_gridsquare TEXT, operator TEXT, comment TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS followCallsigns (callsign TEXT PRIMARY KEY)");
    }

    private void createIndex(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS qsl_call_index ON QslCallsigns (callsign)");
        db.execSQL("CREATE INDEX IF NOT EXISTS qsl_table_call_index ON QSLTable (call)");
    }

    private void loadItuDataFromFile(SQLiteDatabase db) {
        try (InputStream is = context.getAssets().open("itu.json")) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            JSONArray jsonArray = new JSONArray(new String(buffer, StandardCharsets.UTF_8));
            db.beginTransaction();
            try {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("grid", jsonObject.getString("g"));
                    values.put("itu", jsonObject.getInt("i"));
                    db.insertWithOnConflict("ituList", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadItuDataFromFile: " + e.getMessage());
        }
    }

    private void loadICqZoneDataFromFile(SQLiteDatabase db) {
        try (InputStream is = context.getAssets().open("cq.json")) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            JSONArray jsonArray = new JSONArray(new String(buffer, StandardCharsets.UTF_8));
            db.beginTransaction();
            try {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("grid", jsonObject.getString("g"));
                    values.put("cqzone", jsonObject.getInt("c"));
                    db.insertWithOnConflict("cqzoneList", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadICqZoneDataFromFile: " + e.getMessage());
        }
    }

    private void loadDxccDataFromFile(SQLiteDatabase db) {
        try (InputStream is = context.getAssets().open("dxcc.json")) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            JSONObject root = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            JSONArray dxccList = root.getJSONArray("dxcc");
            db.beginTransaction();
            try {
                for (int i = 0; i < dxccList.length(); i++) {
                    JSONObject obj = dxccList.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("dxcc", obj.getInt("id"));
                    values.put("name", obj.getString("n"));
                    values.put("aname", obj.getString("a"));
                    db.insertWithOnConflict("dxccList", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                JSONArray gridList = root.getJSONArray("grid");
                for (int i = 0; i < gridList.length(); i++) {
                    JSONObject obj = gridList.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("grid", obj.getString("g"));
                    values.put("dxcc", obj.getInt("d"));
                    db.insertWithOnConflict("dxcc_grid", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadDxccDataFromFile: " + e.getMessage());
        }
    }

    public void addCallsignQTH(String callsign, String grid) {
        if (grid.trim().length() < 4) return;
        dbExecutor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put("callsign", callsign.toUpperCase());
            values.put("grid", grid.toUpperCase());
            values.put("updateTime", System.currentTimeMillis());
            db.insertWithOnConflict("CallsignQTH", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    public void getConfigByKey(String KeyName, OnAfterQueryConfig onAfterQueryConfig) {
        if (onAfterQueryConfig != null) onAfterQueryConfig.doOnBeforeQueryConfig(KeyName);
        dbExecutor.execute(() -> {
            String value = "";
            try (Cursor cursor = db.rawQuery("select Value from config where KeyName =?", new String[]{KeyName})) {
                if (cursor.moveToFirst()) value = cursor.getString(0);
            }
            final String finalValue = value;
            mainHandler.post(() -> {
                if (onAfterQueryConfig != null) onAfterQueryConfig.doOnAfterQueryConfig(KeyName, finalValue);
            });
        });
    }

    public void getCallSign(String callsign, String fieldName, String tableName, OnGetCallsign getCallsign) {
        dbExecutor.execute(() -> {
            String sql = String.format("select count(%s) FROM %s where %s=? limit 1", fieldName, tableName, fieldName);
            boolean exists = false;
            try (Cursor cursor = db.rawQuery(sql, new String[]{callsign})) {
                if (cursor.moveToFirst()) exists = cursor.getInt(0) > 0;
            }
            final boolean finalExists = exists;
            mainHandler.post(() -> {
                if (getCallsign != null) getCallsign.doOnAfterGetCallSign(finalExists);
            });
        });
    }

    public void writeConfig(String KeyName, String Value, @Nullable OnAfterWriteConfig onAfterWriteConfig) {
        dbExecutor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put("KeyName", KeyName);
            values.put("Value", Value);
            db.insertWithOnConflict("config", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            mainHandler.post(() -> {
                if (onAfterWriteConfig != null) onAfterWriteConfig.doOnAfterWriteConfig(true);
            });
        });
    }

    public void writeMessage(ArrayList<Ft8Message> messages) {
        dbExecutor.execute(() -> {
            db.beginTransaction();
            try {
                for (Ft8Message message : messages) {
                    ContentValues values = new ContentValues();
                    values.put("I3", message.i3);
                    values.put("N3", message.n3);
                    values.put("Protocol", "FT8");
                    values.put("UTC", UtcTimer.getDatetimeYYYYMMDD_HHMMSS(message.utcTime));
                    values.put("SNR", message.snr);
                    values.put("TIME_SEC", message.time_sec);
                    values.put("FREQ", Math.round(message.freq_hz));
                    values.put("CALL_FROM", message.callsignFrom);
                    values.put("CALL_TO", message.callsignTo);
                    values.put("EXTRAL", message.extraInfo);
                    values.put("REPORT", message.report);
                    values.put("BAND", message.band);
                    db.insert("SWLMessages", null, values);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        });
    }

    public void getFollowCallsigns(OnAfterQueryFollowCallsigns onAffterQueryFollowCallsigns) {
        dbExecutor.execute(() -> {
            ArrayList<String> callsigns = new ArrayList<>();
            try (Cursor cursor = db.rawQuery("select callsign from followCallsigns", null)) {
                while (cursor.moveToNext()) callsigns.add(cursor.getString(0));
            }
            mainHandler.post(() -> {
                if (onAffterQueryFollowCallsigns != null) onAffterQueryFollowCallsigns.doOnAfterQueryFollowCallsigns(callsigns);
            });
        });
    }

    public void getMessageLogTotal(OnAfterQueryFollowCallsigns onAfterQueryFollowCallsigns) {
        dbExecutor.execute(() -> {
            String querySQL = "SELECT BAND, count(*) as c from SWLMessages group by BAND order by BAND";
            ArrayList<String> results = new ArrayList<>();
            results.add(GeneralVariables.getStringFromResource(R.string.band_total));
            results.add("---------------------------------------");
            int sum = 0;
            try (Cursor cursor = db.rawQuery(querySQL, null)) {
                while (cursor.moveToNext()) {
                    long band = cursor.getLong(0);
                    int count = cursor.getInt(1);
                    results.add(String.format(Locale.ROOT, "%.3fMHz \t %d", band / 1000000f, count));
                    sum += count;
                }
            }
            results.add(String.format(Locale.ROOT, "-----------Total %d -----------", sum));
            mainHandler.post(() -> {
                if (onAfterQueryFollowCallsigns != null) onAfterQueryFollowCallsigns.doOnAfterQueryFollowCallsigns(results);
            });
        });
    }

    public void getSWLQsoLogTotal(OnAfterQueryFollowCallsigns onAfterQueryFollowCallsigns) {
        dbExecutor.execute(() -> {
            String querySQL = "SELECT strftime('%Y-%m', qso_date) as t, count(*) as c from QSLTable group by t order by t desc";
            ArrayList<String> results = new ArrayList<>();
            results.add("---------------------------------------");
            int sum = 0;
            try (Cursor cursor = db.rawQuery(querySQL, null)) {
                while (cursor.moveToNext()) {
                    results.add(String.format(Locale.ROOT, "%s \t %d", cursor.getString(0), cursor.getInt(1)));
                    sum += cursor.getInt(1);
                }
            }
            results.add(String.format(Locale.ROOT, "-----------Total %d -----------", sum));
            mainHandler.post(() -> {
                if (onAfterQueryFollowCallsigns != null) onAfterQueryFollowCallsigns.doOnAfterQueryFollowCallsigns(results);
            });
        });
    }

    public void addFollowCallsign(String callsign) {
        dbExecutor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put("callsign", callsign);
            db.insertWithOnConflict("followCallsigns", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    public void clearFollowCallsigns() {
        dbExecutor.execute(() -> db.execSQL("delete from followCallsigns"));
    }

    public void clearLogCacheData() {
        dbExecutor.execute(() -> db.execSQL("delete from SWLMessages"));
    }

    public void clearSWLQsoData() {
        dbExecutor.execute(() -> db.execSQL("delete from SWLQSOTable"));
    }

    public void addQSL_Callsign(QSLRecord qslRecord) {
        dbExecutor.execute(() -> doInsertQSLData(qslRecord, null));
    }

    public void addSWL_QSO(QSLRecord record) {
        dbExecutor.execute(() -> {
            db.execSQL("DELETE FROM SWLQSOTable where ([call]=?) and (station_callsign=?) and (qso_date=?) and (time_on=?) and (freq=?)",
                    new Object[]{record.getToCallsign(), record.getMyCallsign(), record.getQso_date(), record.getTime_on(), BaseRigOperation.getFrequencyFloat(record.getBandFreq())});

            ContentValues values = new ContentValues();
            values.put("call", record.getToCallsign());
            values.put("gridsquare", record.getToMaidenGrid());
            values.put("mode", record.getMode());
            values.put("rst_sent", record.getSendReport());
            values.put("rst_rcvd", record.getReceivedReport());
            values.put("qso_date", record.getQso_date());
            values.put("time_on", record.getTime_on());
            values.put("qso_date_off", record.getQso_date_off());
            values.put("time_off", record.getTime_off());
            values.put("band", record.getBandLength());
            values.put("freq", BaseRigOperation.getFrequencyFloat(record.getBandFreq()));
            values.put("station_callsign", record.getMyCallsign());
            values.put("my_gridsquare", record.getMyMaidenGrid());
            values.put("operator", GeneralVariables.myCallsign);
            values.put("comment", record.getComment());
            db.insert("SWLQSOTable", null, values);
        });
    }

    public void deleteFollowCallsign(String callsign) {
        dbExecutor.execute(() -> db.delete("followCallsigns", "callsign=?", new String[]{callsign}));
    }

    public void getAllConfigParameter(OnAfterQueryConfig onAfterQueryConfig) {
        dbExecutor.execute(() -> {
            try (Cursor cursor = db.rawQuery("select KeyName, Value from config", null)) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String value = cursor.getString(1);
                    if (value == null) value = "";

                    try {
                        if (name.equalsIgnoreCase("grid")) GeneralVariables.setMyMaidenheadGrid(value);
                        if (name.equalsIgnoreCase("callsign")) {
                            GeneralVariables.myCallsign = value;
                            if (!value.isEmpty()) {
                                Ft8Message.hashList.addHash(FT8Package.getHash22(value), value);
                                Ft8Message.hashList.addHash(FT8Package.getHash12(value), value);
                                Ft8Message.hashList.addHash(FT8Package.getHash10(value), value);
                                if (value.contains("/")) {
                                    String shortCall = GeneralVariables.getShortCallsign(value);
                                    Ft8Message.hashList.addHash(FT8Package.getHash22(shortCall), shortCall);
                                    Ft8Message.hashList.addHash(FT8Package.getHash12(shortCall), shortCall);
                                    Ft8Message.hashList.addHash(FT8Package.getHash10(shortCall), shortCall);
                                }
                            }
                        }
                        if (name.equalsIgnoreCase("toModifier")) GeneralVariables.toModifier = value;
                        if (name.equalsIgnoreCase("freq"))
                            GeneralVariables.setBaseFrequency(parseFloatConfig(name, value, 1000f));
                        if (name.equalsIgnoreCase("synFreq")) GeneralVariables.synFrequency = !value.equals("0");
                        if (name.equalsIgnoreCase("transDelay"))
                            GeneralVariables.transmitDelay = parseIntConfig(name, value, 500);
                        if (name.equalsIgnoreCase("civ"))
                            GeneralVariables.civAddress = parseIntConfig(name, value, 0xa4, 16);
                        if (name.equalsIgnoreCase("baudRate"))
                            GeneralVariables.baudRate = parseIntConfig(name, value, 19200);
                        if (name.equalsIgnoreCase("bandFreq")) {
                            GeneralVariables.band = parseLongConfig(name, value, 14074000L);
                            GeneralVariables.bandListIndex = OperationBand.getIndexByFreq(GeneralVariables.band);
                        }
                        if (name.equalsIgnoreCase("msgMode")) GeneralVariables.simpleCallItemMode = value.equals("1");
                        if (name.equalsIgnoreCase("ctrMode"))
                            GeneralVariables.controlMode = parseIntConfig(name, value, 0);
                        if (name.equalsIgnoreCase("model"))
                            GeneralVariables.modelNo = parseIntConfig(name, value, 0);
                        if (name.equalsIgnoreCase("instruction"))
                            GeneralVariables.instructionSet = parseIntConfig(name, value, 0);
                        if (name.equalsIgnoreCase("launchSupervision"))
                            GeneralVariables.launchSupervision = parseIntConfig(name, value, 600000);
                        if (name.equalsIgnoreCase("noReplyLimit"))
                            GeneralVariables.noReplyLimit = parseIntConfig(name, value, 0);
                        if (name.equalsIgnoreCase("autoFollowCQ")) GeneralVariables.autoFollowCQ = !value.equals("0");
                        if (name.equalsIgnoreCase("autoCallFollow")) GeneralVariables.autoCallFollow = !value.equals("0");
                        if (name.equalsIgnoreCase("pttDelay"))
                            GeneralVariables.pttDelay = parseIntConfig(name, value, 100);
                        if (name.equalsIgnoreCase("icomIp")) GeneralVariables.icomIp = value.isEmpty() ? "255.255.255.255" : value;
                        if (name.equalsIgnoreCase("icomPort"))
                            GeneralVariables.icomUdpPort = parseIntConfig(name, value, 50001);
                        if (name.equalsIgnoreCase("icomUserName")) GeneralVariables.icomUserName = value.isEmpty() ? "ic705" : value;
                        if (name.equalsIgnoreCase("icomPassword")) GeneralVariables.icomPassword = value;
                        if (name.equalsIgnoreCase("volumeValue"))
                            GeneralVariables.volumePercent = parseFloatConfig(name, value, 100f) / 100f;
                        if (name.equalsIgnoreCase("excludedCallsigns")) GeneralVariables.addExcludedCallsigns(value);
                        if (name.equalsIgnoreCase("flexMaxRfPower"))
                            GeneralVariables.flexMaxRfPower = parseIntConfig(name, value, 10);
                        if (name.equalsIgnoreCase("flexMaxTunePower"))
                            GeneralVariables.flexMaxTunePower = parseIntConfig(name, value, 10);
                        if (name.equalsIgnoreCase("saveSWL")) GeneralVariables.saveSWLMessage = value.equals("1");
                        if (name.equalsIgnoreCase("saveSWLQSO")) GeneralVariables.saveSWL_QSO = value.equals("1");
                        if (name.equalsIgnoreCase("audioBits")) GeneralVariables.audioOutput32Bit = value.equals("1");
                        if (name.equalsIgnoreCase("audioRate"))
                            GeneralVariables.audioSampleRate = parseIntConfig(name, value, 12000);
                        if (name.equalsIgnoreCase("deepMode")) GeneralVariables.deepDecodeMode = value.equals("1");
                        if (name.equalsIgnoreCase("dataBits"))
                            GeneralVariables.serialDataBits = parseIntConfig(name, value, 8);
                        if (name.equalsIgnoreCase("stopBits"))
                            GeneralVariables.serialStopBits = parseIntConfig(name, value, 1);
                        if (name.equalsIgnoreCase("parityBits"))
                            GeneralVariables.serialParity = parseIntConfig(name, value, 0);
                        if (name.equalsIgnoreCase("enableCloudlog")) GeneralVariables.enableCloudlog = value.equals("1");
                        if (name.equalsIgnoreCase("cloudlogServerAddress")) GeneralVariables.cloudlogServerAddress = value;
                        if (name.equalsIgnoreCase("cloudlogApiKey")) GeneralVariables.cloudlogApiKey = value;
                        if (name.equalsIgnoreCase("cloudlogStationID")) GeneralVariables.cloudlogStationID = value;
                        if (name.equalsIgnoreCase("enableQRZ")) GeneralVariables.enableQRZ = value.equals("1");
                        if (name.equalsIgnoreCase("qrzApiKey")) GeneralVariables.qrzApiKey = value;
                        if (name.equalsIgnoreCase("swrSwitch")) GeneralVariables.swr_switch_on = value.equals("1");
                        if (name.equalsIgnoreCase("alcSwitch")) GeneralVariables.alc_switch_on = value.equals("1");
                        if (name.equalsIgnoreCase("connectMode"))
                            GeneralVariables.connectMode = parseIntConfig(name, value, 0);
                        if (name.equalsIgnoreCase("usbVendorId"))
                            GeneralVariables.usbVendorId = parseIntConfig(name, value, -1);
                        if (name.equalsIgnoreCase("usbProductId"))
                            GeneralVariables.usbProductId = parseIntConfig(name, value, -1);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Config field '" + name + "' could not be applied; keeping its default");
                    }

                    final String fName = name;
                    final String fValue = value;
                    mainHandler.post(() -> {
                        if (onAfterQueryConfig != null) {
                            try {
                                onAfterQueryConfig.doOnAfterQueryConfig(fName, fValue);
                            } catch (RuntimeException e) {
                                Log.w(TAG, "Config row callback failed for field '" + fName + "'");
                            }
                        }
                    });
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Config query failed; continuing with available defaults");
            } finally {
                try {
                    getAllQSLCallsignsSync();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Config-dependent cache load failed; continuing");
                }
                mainHandler.post(() -> {
                    if (onAfterQueryConfig != null) {
                        try {
                            onAfterQueryConfig.doOnConfigLoadComplete();
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Config completion callback failed");
                        }
                    }
                });
            }
        });
    }

    private static int parseIntConfig(String fieldName, String value, int defaultValue) {
        return parseIntConfig(fieldName, value, defaultValue, 10);
    }

    private static int parseIntConfig(String fieldName, String value, int defaultValue, int radix) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value, radix);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid numeric config field '" + fieldName + "'; using default");
            return defaultValue;
        }
    }

    private static long parseLongConfig(String fieldName, String value, long defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid numeric config field '" + fieldName + "'; using default");
            return defaultValue;
        }
    }

    private static float parseFloatConfig(String fieldName, String value, float defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid numeric config field '" + fieldName + "'; using default");
            return defaultValue;
        }
    }

    private void getAllQSLCallsignsSync() {
        String bandName = BaseRigOperation.getMeterFromFreq(GeneralVariables.band);
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("select distinct call from QSLTable where band=?", new String[]{bandName})) {
            while (cursor.moveToNext()) list.add(cursor.getString(0));
        }
        GeneralVariables.QSL_Callsign_list = list;

        ArrayList<String> otherList = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("select distinct call from QSLTable where band<>?", new String[]{bandName})) {
            while (cursor.moveToNext()) otherList.add(cursor.getString(0));
        }
        GeneralVariables.QSL_Callsign_list_other_band = otherList;
    }

    public void getAllQSLCallsigns() {
        dbExecutor.execute(this::getAllQSLCallsignsSync);
    }

    public void getQSLCallsignsByCallsign(boolean showAll, int offset, String callsign, int filter, OnQueryQSLCallsign callback) {
        dbExecutor.execute(() -> {
            String filterStr = "";
            if (filter == 1) filterStr = "and((q.isQSL =1)or(q.isLotW_QSL =1))";
            else if (filter == 2) filterStr = "and((q.isQSL =0)and(q.isLotW_QSL =0))";

            String limitStr = showAll ? "" : "limit 100 offset " + offset;
            String sql = "select call, gridsquare, band, freq, qso_date, mode, isQSL, isLotW_QSL from QSLTable where (call like ?) " + filterStr + " order by qso_date desc " + limitStr;
            ArrayList<QSLCallsignRecord> records = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(sql, new String[]{"%" + callsign + "%"})) {
                while (cursor.moveToNext()) {
                    QSLCallsignRecord r = new QSLCallsignRecord();
                    r.setCallsign(cursor.getString(0));
                    r.setGrid(cursor.getString(1));
                    r.setBand(cursor.getString(2) + "(" + cursor.getString(3) + " MHz)");
                    r.setLastTime(cursor.getString(4));
                    r.setMode(cursor.getString(5));
                    r.isQSL = cursor.getInt(6) == 1;
                    r.isLotW_QSL = cursor.getInt(7) == 1;
                    records.add(r);
                }
            }
            mainHandler.post(() -> callback.afterQuery(records));
        });
    }

    public void getQsoGridQuery(OnGetQsoGrids callback) {
        dbExecutor.execute(() -> {
            HashMap<String, Boolean> grids = new HashMap<>();
            try (Cursor cursor = db.rawQuery("select gridsquare, max(isQSL + isLotW_QSL) as confirmed from QSLTable where length(gridsquare) > 2 group by gridsquare", null)) {
                while (cursor.moveToNext()) {
                    grids.put(cursor.getString(0), cursor.getInt(1) > 0);
                }
            }
            mainHandler.post(() -> callback.onAfterQuery(grids));
        });
    }

    @SuppressLint("Range")
    public void getQSLRecordByCallsign(boolean showAll, int offset, String callsign, int filter, OnQueryQSLRecordCallsign callback) {
        dbExecutor.execute(() -> {
            String filterStr = "";
            if (filter == 1) filterStr = "and((isQSL =1)or(isLotW_QSL =1))";
            else if (filter == 2) filterStr = "and((isQSL =0)and(isLotW_QSL =0))";

            String limitStr = showAll ? "" : "limit 100 offset " + offset;
            String sql = "select * from QSLTable where (call like ?) " + filterStr + " order by qso_date desc, time_off desc " + limitStr;
            ArrayList<QSLRecordStr> records = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(sql, new String[]{"%" + callsign + "%"})) {
                while (cursor.moveToNext()) {
                    QSLRecordStr r = new QSLRecordStr();
                    r.id = cursor.getInt(cursor.getColumnIndex("id"));
                    r.setCall(cursor.getString(cursor.getColumnIndex("call")));
                    r.isQSL = cursor.getInt(cursor.getColumnIndex("isQSL")) == 1;
                    r.isLotW_import = cursor.getInt(cursor.getColumnIndex("isLotW_import")) == 1;
                    r.isLotW_QSL = cursor.getInt(cursor.getColumnIndex("isLotW_QSL")) == 1;
                    r.setGridsquare(cursor.getString(cursor.getColumnIndex("gridsquare")));
                    r.setMode(cursor.getString(cursor.getColumnIndex("mode")));
                    r.setRst_sent(cursor.getString(cursor.getColumnIndex("rst_sent")));
                    r.setRst_rcvd(cursor.getString(cursor.getColumnIndex("rst_rcvd")));
                    r.setTime_on(cursor.getString(cursor.getColumnIndex("qso_date")) + "-" + cursor.getString(cursor.getColumnIndex("time_on")));
                    r.setTime_off(cursor.getString(cursor.getColumnIndex("qso_date_off")) + "-" + cursor.getString(cursor.getColumnIndex("time_off")));
                    r.setBand(cursor.getString(cursor.getColumnIndex("band")));
                    r.setFreq(cursor.getString(cursor.getColumnIndex("freq")));
                    r.setComment(cursor.getString(cursor.getColumnIndex("comment")));
                    records.add(r);
                }
            }
            mainHandler.post(() -> callback.afterQuery(records));
        });
    }

    public void deleteQSLCallsign(int id) {
        dbExecutor.execute(() -> db.delete("QslCallsigns", "id=?", new String[]{String.valueOf(id)}));
    }

    public void deleteQSLByID(int id) {
        dbExecutor.execute(() -> db.delete("QSLTable", "id=?", new String[]{String.valueOf(id)}));
    }

    public void setQSLTableIsQSL(boolean isQSL, int id) {
        dbExecutor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put("isQSL", isQSL ? 1 : 0);
            db.update("QSLTable", values, "id=?", new String[]{String.valueOf(id)});
        });
    }

    public void setQSLCallsignIsQSL(boolean isQSL, int id) {
        dbExecutor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put("isQSL", isQSL ? 1 : 0);
            db.update("QslCallsigns", values, "id=?", new String[]{String.valueOf(id)});
        });
    }

    public void getCallsignQTH(String callsign) {
        dbExecutor.execute(() -> {
            try (Cursor cursor = db.rawQuery("select grid from CallsignQTH where callsign=?", new String[]{callsign.toUpperCase()})) {
                if (cursor.moveToFirst()) {
                    GeneralVariables.addCallsignAndGrid(callsign, cursor.getString(0));
                }
            }
        });
    }

    public void getCallsignMapGrid() {
        dbExecutor.execute(() -> {
            try (Cursor cursor = db.rawQuery("select distinct callsign, grid from QslCallsigns where length(grid) > 3", null)) {
                while (cursor.moveToNext()) {
                    GeneralVariables.addCallsignAndGrid(cursor.getString(0), cursor.getString(1));
                }
            }
        });
    }

    public void getQslDxccToMap() {
        dbExecutor.execute(() -> {
            try (Cursor cursor = db.rawQuery("select distinct callsign, grid, band_i from QslCallsigns where length(grid)>=4", null)) {
                if (cursor == null) return;
                while (cursor.moveToNext()) {
                    String callsign = cursor.getString(0);
                    String gridStr = cursor.getString(1);
                    long workedBand = cursor.getLong(2);

                    if (callsign != null) {
                        GeneralVariables.workedPrefixes.add(GeneralVariables.getShortCallsign(callsign));
                    }

                    if (gridStr == null || gridStr.length() < 4) continue;
                    String grid = gridStr.toUpperCase().substring(0, 4);

                    try (Cursor cItu = db.rawQuery("select itu from ituList where grid=?", new String[]{grid})) {
                        if (cItu.moveToFirst()) GeneralVariables.addItuZone(cItu.getInt(0));
                    }

                    try (Cursor cCq = db.rawQuery("select cqzone from cqzoneList where grid=?", new String[]{grid})) {
                        if (cCq.moveToFirst()) GeneralVariables.addCqZone(cCq.getInt(0));
                    }

                    try (Cursor cDxcc = db.rawQuery("select dxcc from dxcc_grid where grid=?", new String[]{grid})) {
                        if (cDxcc.moveToFirst()) {
                            int dxccId = cDxcc.getInt(0);
                            try (Cursor cName = db.rawQuery("select name from dxccList where dxcc=?", new String[]{String.valueOf(dxccId)})) {
                                if (cName.moveToFirst()) {
                                    String dxccName = cName.getString(0);
                                    GeneralVariables.addDxcc(dxccName);
                                    GeneralVariables.addWorkedDxccOnBand(dxccName, workedBand);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    @SuppressLint("Range")
    public String downQSLTable(Cursor cursor, boolean isSWL) {
        StringBuilder logStr = new StringBuilder();
        logStr.append("FT8CN ADIF Export<eoh>\n");
        int oldPos = cursor.getPosition();
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String call = cursor.getString(cursor.getColumnIndex("call"));
            if (call == null) continue;
            logStr.append(String.format(Locale.ROOT, "<call:%d>%s ", call.length(), call));

            if (!isSWL) {
                logStr.append(cursor.getInt(cursor.getColumnIndex("isLotW_QSL")) == 1 ? "<QSL_RCVD:1>Y " : "<QSL_RCVD:1>N ");
                logStr.append(cursor.getInt(cursor.getColumnIndex("isQSL")) == 1 ? "<QSL_MANUAL:1>Y " : "<QSL_MANUAL:1>N ");
            } else {
                logStr.append("<swl:1>Y ");
            }

            String grid = cursor.getString(cursor.getColumnIndex("gridsquare"));
            if (grid != null) logStr.append(String.format(Locale.ROOT, "<gridsquare:%d>%s ", grid.length(), grid));

            String mode = cursor.getString(cursor.getColumnIndex("mode"));
            if (mode != null) logStr.append(String.format(Locale.ROOT, "<mode:%d>%s ", mode.length(), mode));

            String rst_sent = cursor.getString(cursor.getColumnIndex("rst_sent"));
            if (rst_sent != null) logStr.append(String.format(Locale.ROOT, "<rst_sent:%d>%s ", rst_sent.length(), rst_sent));

            String rst_rcvd = cursor.getString(cursor.getColumnIndex("rst_rcvd"));
            if (rst_rcvd != null) logStr.append(String.format(Locale.ROOT, "<rst_rcvd:%d>%s ", rst_rcvd.length(), rst_rcvd));

            String qso_date = cursor.getString(cursor.getColumnIndex("qso_date"));
            if (qso_date != null) logStr.append(String.format(Locale.ROOT, "<qso_date:%d>%s ", qso_date.length(), qso_date));

            String time_on = cursor.getString(cursor.getColumnIndex("time_on"));
            if (time_on != null) logStr.append(String.format(Locale.ROOT, "<time_on:%d>%s ", time_on.length(), time_on));

            String qso_date_off = cursor.getString(cursor.getColumnIndex("qso_date_off"));
            if (qso_date_off != null) logStr.append(String.format(Locale.ROOT, "<qso_date_off:%d>%s ", qso_date_off.length(), qso_date_off));

            String time_off = cursor.getString(cursor.getColumnIndex("time_off"));
            if (time_off != null) logStr.append(String.format(Locale.ROOT, "<time_off:%d>%s ", time_off.length(), time_off));

            String band = cursor.getString(cursor.getColumnIndex("band"));
            if (band != null) logStr.append(String.format(Locale.ROOT, "<band:%d>%s ", band.length(), band));

            String freq = cursor.getString(cursor.getColumnIndex("freq"));
            if (freq != null) logStr.append(String.format(Locale.ROOT, "<freq:%d>%s ", freq.length(), freq));

            String station_call = cursor.getString(cursor.getColumnIndex("station_callsign"));
            if (station_call != null) logStr.append(String.format(Locale.ROOT, "<station_callsign:%d>%s ", station_call.length(), station_call));

            String my_grid = cursor.getString(cursor.getColumnIndex("my_gridsquare"));
            if (my_grid != null) logStr.append(String.format(Locale.ROOT, "<my_gridsquare:%d>%s ", my_grid.length(), my_grid));

            int opIdx = cursor.getColumnIndex("operator");
            if (opIdx != -1) {
                String operator = cursor.getString(opIdx);
                if (operator != null) logStr.append(String.format(Locale.ROOT, "<operator:%d>%s ", operator.length(), operator));
            }

            String comment = cursor.getString(cursor.getColumnIndex("comment"));
            if (comment == null) comment = "";
            logStr.append(String.format(Locale.ROOT, "<comment:%d>%s <eor>\n", comment.length(), comment));
        }
        cursor.moveToPosition(oldPos);
        return logStr.toString();
    }

    private boolean checkQSLCallsign(QSLRecord record) {
        String sql = "SELECT count(*) FROM QslCallsigns WHERE (callsign=?) AND (startTime=?) AND (finishTime=?) AND (mode=?)";
        boolean exists = false;
        try (Cursor cursor = db.rawQuery(sql, new String[]{record.getToCallsign(), String.valueOf(record.getStartTime()), String.valueOf(record.getEndTime()), record.getMode()})) {
            if (cursor.moveToFirst()) exists = cursor.getInt(0) > 0;
        }
        return exists;
    }

    private boolean checkIsQSL(QSLRecord record) {
        String sql = "SELECT count(*) FROM QSLTable WHERE (call=?) AND (qso_date=?) AND (time_on=?) AND (mode=?)";
        boolean exists = false;
        try (Cursor cursor = db.rawQuery(sql, new String[]{record.getToCallsign(), record.getQso_date(), record.getTime_on(), record.getMode()})) {
            if (cursor.moveToFirst()) exists = cursor.getInt(0) > 0;
        }
        return exists;
    }

    public boolean doInsertQSLData(QSLRecord record, @Nullable AfterInsertQSLData callback) {
        if (record.getToCallsign() == null) return false;

        db.beginTransaction();
        try {
            boolean isNewQsl = false;
            // 1. 处理 QslCallsigns (呼号记录表)
            if (!checkQSLCallsign(record)) {
                ContentValues values = new ContentValues();
                values.put("callsign", record.getToCallsign());
                values.put("isQSL", record.isQSL ? 1 : 0);
                values.put("isLotW_import", record.isLotW_import ? 1 : 0);
                values.put("isLotW_QSL", record.isLotW_QSL ? 1 : 0);
                values.put("startTime", record.getStartTime());
                values.put("finishTime", record.getEndTime());
                values.put("mode", record.getMode());
                values.put("grid", record.getToMaidenGrid());
                values.put("band", BaseRigOperation.getFrequencyAllInfo(record.getBandFreq()));
                values.put("band_i", record.getBandFreq());
                db.insert("QslCallsigns", null, values);
            } else {
                if (record.isQSL) db.execSQL("UPDATE QslCallsigns SET isQSL=1 WHERE (callsign=?) AND (startTime=?) AND (finishTime=?) AND (mode=?)",
                        new Object[]{record.getToCallsign(), record.getStartTime(), record.getEndTime(), record.getMode()});
                if (record.isLotW_QSL) db.execSQL("UPDATE QslCallsigns SET isLotW_QSL=1 WHERE (callsign=?) AND (startTime=?) AND (finishTime=?) AND (mode=?)",
                        new Object[]{record.getToCallsign(), record.getStartTime(), record.getEndTime(), record.getMode()});
            }

            // 2. 处理 QSLTable (通联日志表)
            if (!checkIsQSL(record)) {
                isNewQsl = true;
                ContentValues values = new ContentValues();
                values.put("call", record.getToCallsign());
                values.put("isQSL", record.isQSL ? 1 : 0);
                values.put("isLotW_import", record.isLotW_import ? 1 : 0);
                values.put("isLotW_QSL", record.isLotW_QSL ? 1 : 0);
                values.put("gridsquare", record.getToMaidenGrid());
                values.put("mode", record.getMode());
                values.put("rst_sent", record.getSendReport());
                values.put("rst_rcvd", record.getReceivedReport());
                values.put("qso_date", record.getQso_date());
                values.put("time_on", record.getTime_on());
                values.put("qso_date_off", record.getQso_date_off());
                values.put("time_off", record.getTime_off());
                values.put("band", record.getBandLength());
                values.put("freq", BaseRigOperation.getFrequencyFloat(record.getBandFreq()));
                values.put("station_callsign", record.getMyCallsign());
                values.put("my_gridsquare", record.getMyMaidenGrid());
                values.put("operator", GeneralVariables.myCallsign);
                values.put("comment", record.getComment());
                db.insert("QSLTable", null, values);
            } else {
                if (record.isQSL) db.execSQL("UPDATE QSLTable SET isQSL=1 WHERE (call=?) AND (qso_date=?) AND (time_on=?) AND (mode=?)",
                        new Object[]{record.getToCallsign(), record.getQso_date(), record.getTime_on(), record.getMode()});
                if (record.isLotW_QSL) db.execSQL("UPDATE QSLTable SET isLotW_QSL=1 WHERE (call=?) AND (qso_date=?) AND (time_on=?) AND (mode=?)",
                        new Object[]{record.getToCallsign(), record.getQso_date(), record.getTime_on(), record.getMode()});
            }

            db.setTransactionSuccessful();
            if (callback != null) {
                final boolean finalIsNew = isNewQsl;
                mainHandler.post(() -> callback.doAfterInsert(false, finalIsNew));
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "doInsertQSLData: " + e.getMessage());
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public interface OnGetQsoGrids {
        void onAfterQuery(HashMap<String, Boolean> grids);
    }
}
