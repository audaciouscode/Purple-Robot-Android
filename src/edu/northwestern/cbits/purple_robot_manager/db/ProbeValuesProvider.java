package edu.northwestern.cbits.purple_robot_manager.db;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;

import edu.northwestern.cbits.purple_robot_manager.db.filters.Filter;
import edu.northwestern.cbits.purple_robot_manager.db.filters.FrequencyThrottleFilter;
import edu.northwestern.cbits.purple_robot_manager.db.filters.ValueDeltaFilter;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.AccelerometerProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.GyroscopeProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.LightProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.MagneticFieldProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.PressureProbe;

public class ProbeValuesProvider
{
    public static final String INTEGER_TYPE = "integer";
    public static final String REAL_TYPE = "real";
    public static final String TEXT_TYPE = "text";

    public static final String TIMESTAMP = "timestamp";
    private static final String ID = "_id";
    private Context _context = null;

    protected static final int FIELD_TYPE_UNKNOWN = -1;
    protected static final int FIELD_TYPE_NULL = 0;
    protected static final int FIELD_TYPE_INTEGER = 1;
    protected static final int FIELD_TYPE_FLOAT = 2;
    protected static final int FIELD_TYPE_STRING = 3;
    protected static final int FIELD_TYPE_BLOB = 4;

    private ArrayList<Filter> _filters = new ArrayList<>();

    private long _lastCleanup = 0;

    private static ProbeValuesProvider _instance = null;

    private HashMap<String, Long> _lastUpdates = new HashMap<>();

    public static ProbeValuesProvider getProvider(Context context)
    {
        if (ProbeValuesProvider._instance == null)
            ProbeValuesProvider._instance = new ProbeValuesProvider(context.getApplicationContext());

        return ProbeValuesProvider._instance;
    }

    public ProbeValuesProvider(Context context)
    {
        this._context = context.getApplicationContext();

        HashSet<String> highFreq = new HashSet<>();
        highFreq.add(AccelerometerProbe.DB_TABLE);
        highFreq.add(GyroscopeProbe.DB_TABLE);
        highFreq.add(MagneticFieldProbe.DB_TABLE);

        this._filters.add(new FrequencyThrottleFilter(1000, null, highFreq)); // Don't
                                                                              // save
                                                                              // any
                                                                              // readings
                                                                              // at
                                                                              // a
                                                                              // larger
                                                                              // than
                                                                              // 1
                                                                              // second
                                                                              // interval
                                                                              // for
                                                                              // most
                                                                              // sensors...
        this._filters.add(new FrequencyThrottleFilter(100, highFreq, null)); // Only
                                                                             // save
                                                                             // high-frequency
                                                                             // data
                                                                             // at
                                                                             // 0.1s
                                                                             // intervals...

        // Proximity: Identical values

        HashSet<String> quarterDelta = new HashSet<>();
        quarterDelta.add(AccelerometerProbe.DB_TABLE);
        quarterDelta.add(PressureProbe.DB_TABLE);
        quarterDelta.add(GyroscopeProbe.DB_TABLE);

        this._filters.add(new ValueDeltaFilter(0.25, quarterDelta));

        HashSet<String> fullDelta = new HashSet<>();
        fullDelta.add(MagneticFieldProbe.DB_TABLE);

        this._filters.add(new ValueDeltaFilter(1.0, fullDelta));

        HashSet<String> fiveDelta = new HashSet<>();
        fiveDelta.add(LightProbe.DB_TABLE);

        this._filters.add(new ValueDeltaFilter(5.0, fiveDelta));
    }

    public void close()
    {
//        this._dbHelper.close();
    }

    private String tableName(Context context, String name, Map<String, String> schema)
    {
        String tableName = name;

        ArrayList<String> columns = new ArrayList<>(schema.keySet());
        Collections.sort(columns);

        for (String key : columns)
        {
            tableName += (key + schema.get(key));
        }

        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(tableName.getBytes("UTF-8"));

            tableName = "table_" + (new BigInteger(1, digest)).toString(16);
        }
        catch (NoSuchAlgorithmException | UnsupportedEncodingException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        return tableName;
    }

    private boolean tableExists(Context context, String tableName)
    {
        Cursor c = null;

        boolean tableExists = false;

        ProbeValuesSqlHelper helper = new ProbeValuesSqlHelper(context);

        SQLiteDatabase database = helper.getWritableDatabase();

        try
        {
            c = database.query(tableName, null, null, null, null, null, null);

            tableExists = true;
        }
        catch (SQLiteException e)
        {
            // LogManager.getInstance(context).logException(e);
        }
        finally
        {
            if (c != null)
                c.close();
        }

        database.close();

        return tableExists;
    }

    private boolean isValidColumn(String key)
    {
        // TODO: Add more checks...

        return true;
    }

    private boolean createTable(String name, Map<String, String> schema)
    {
        String createSql = "create table " + name + " (" + ProbeValuesProvider.ID
                + " integer primary key autoincrement";

        createSql += (", " + ProbeValuesProvider.TIMESTAMP + " real");

        for (String key : schema.keySet())
        {
            if (this.isValidColumn(key))
            {
                String dbType = null;

                String type = schema.get(key);

                if (ProbeValuesProvider.REAL_TYPE.equals(type))
                    dbType = ProbeValuesProvider.REAL_TYPE;
                else if (ProbeValuesProvider.INTEGER_TYPE.equals(type))
                    dbType = ProbeValuesProvider.INTEGER_TYPE;
                if (ProbeValuesProvider.TEXT_TYPE.equals(type))
                    dbType = ProbeValuesProvider.TEXT_TYPE;

                if (dbType != null)
                    createSql += (", " + key + " " + dbType);
            }
        }

        createSql += ");";

        ProbeValuesSqlHelper helper = new ProbeValuesSqlHelper(this._context);

        SQLiteDatabase database = helper.getWritableDatabase();

        database.execSQL(createSql);

        database.close();

        return true;
    }

    public void insertValue(final Context context, final String name, final Map<String, String> schema,
            final Map<String, Object> values)
    {
        long now = System.currentTimeMillis();

        long lastUpdate = 0;

        if (this._lastUpdates.containsKey(name))
            lastUpdate = this._lastUpdates.get(name);

        if (now - lastUpdate < 5000)
            return;

        this._lastUpdates.put(name, now);

        final ProbeValuesProvider me = this;

        final HashSet<Long> attempts = new HashSet<>();

        Runnable r = new Runnable()
        {
            public void run()
            {
                try
                {
                    ProbeValuesSqlHelper helper = new ProbeValuesSqlHelper(context);

                    SQLiteDatabase database = helper.getWritableDatabase();

                    synchronized (database) {
                        for (Filter f : me._filters) {
                            if (f.allow(name, values) == false)
                                return;
                        }

                        long now = System.currentTimeMillis();

                        if (now - me._lastCleanup > 300000) // Flush old entries
                            me.cleanup(context);

                        String localName = me.tableName(context, name, schema);

                        if (me.tableExists(context, localName) == false)
                            me.createTable(localName, schema);

                        ContentValues toInsert = new ContentValues();

                        for (String key : schema.keySet()) {
                            String type = schema.get(key);

                            if (ProbeValuesProvider.REAL_TYPE.equals(type)) {
                                try {
                                    Double d = (Double) values.get(key);
                                    toInsert.put(key, d);
                                } catch (ClassCastException e) {
                                    try {
                                        Float f = (Float) values.get(key);
                                        toInsert.put(key, f.doubleValue());
                                    } catch (ClassCastException ee) {
                                        Integer i = (Integer) values.get(key);
                                        toInsert.put(key, i.doubleValue());
                                    }
                                }
                            } else if (ProbeValuesProvider.INTEGER_TYPE.equals(type)) {
                                Integer i = (Integer) values.get(key);

                                toInsert.put(key, i);
                            } else if (ProbeValuesProvider.TEXT_TYPE.equals(type))
                                toInsert.put(key, values.get(key).toString());
                        }

                        toInsert.put(ProbeValuesProvider.TIMESTAMP, (Double) values.get(ProbeValuesProvider.TIMESTAMP));

                        database.insert(localName, null, toInsert);
                    }

                    database.close();
                }
                catch (SQLiteException e)
                {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e1) {

                    }

                    attempts.add(System.currentTimeMillis());

                    if (attempts.size() < 10)
                        this.run();
                }
            }
        };

        try
        {
            Thread t = new Thread(r);
            t.start();
        }
        catch (OutOfMemoryError e)
        {
            LogManager.getInstance(context).logException(e);
        }
    }

    private void cleanup(Context context)
    {
        try
        {
            this._lastCleanup = System.currentTimeMillis();

            String tableSelect = "select name from sqlite_master where type='table';";

            ProbeValuesSqlHelper helper = new ProbeValuesSqlHelper(context);

            SQLiteDatabase database = helper.getWritableDatabase();

            Cursor c = database.rawQuery(tableSelect, null);

            while (c.moveToNext())
            {
                String tableName = c.getString(c.getColumnIndex("name"));

                try
                {
                    if (tableName.startsWith("table_"))
                    {
                        Cursor cursor = database.query(tableName, null, null, null, null, null, null);

                        cursor.close();

                        SQLiteStatement delete = database.compileStatement("delete from " + tableName + " where "
                                + ProbeValuesProvider.ID + " not in (select " + ProbeValuesProvider.ID + " from "
                                + tableName + " order by " + ProbeValuesProvider.TIMESTAMP + " desc limit 2048);");
                        delete.execute();

                        cursor = database.query(tableName, null, null, null, null, null, null);

                        cursor.close();
                    }
                }
                catch (SQLException e)
                {
                    LogManager.getInstance(context).logException(e);
                }
            }

            c.close();

            database.close();
        }
        catch (RuntimeException e)
        {
            LogManager.getInstance(context).logException(e);
        }
    }

    public void clear(Context context)
    {
        ProbeValuesSqlHelper helper = new ProbeValuesSqlHelper(context);

        String tableSelect = "select name from sqlite_master where type='table';";

        SQLiteDatabase database = helper.getWritableDatabase();

        Cursor c = database.rawQuery(tableSelect, null);

        ArrayList<String> names = new ArrayList<>();

        while (c.moveToNext())
            names.add(c.getString(c.getColumnIndex("name")));

        c.close();

        for (String name : names)
        {
            try
            {
                SQLiteStatement delete = database.compileStatement("delete from " + name + " where (_id != -1)");
                delete.execute();
            }
            catch (SQLException e)
            {
                LogManager.getInstance(context).logException(e);
            }
        }

        database.close();
    }

    static int getType(Cursor cursor, int i) throws Exception
    {
        SQLiteCursor sqLiteCursor = (SQLiteCursor) cursor;

        CursorWindow cursorWindow = sqLiteCursor.getWindow();

        int pos = cursor.getPosition();

        if (cursorWindow.isNull(pos, i))
            return ProbeValuesProvider.FIELD_TYPE_NULL;
        else if (cursorWindow.isLong(pos, i))
            return ProbeValuesProvider.FIELD_TYPE_INTEGER;
        else if (cursorWindow.isFloat(pos, i))
            return ProbeValuesProvider.FIELD_TYPE_FLOAT;
        else if (cursorWindow.isString(pos, i))
            return ProbeValuesProvider.FIELD_TYPE_STRING;
        else if (cursorWindow.isBlob(pos, i))
            return ProbeValuesProvider.FIELD_TYPE_BLOB;

        return ProbeValuesProvider.FIELD_TYPE_UNKNOWN;
    }

    public Cursor retrieveValues(Context context, String name, Map<String, String> schema)
    {
        MatrixCursor matrix = null;

        ProbeValuesSqlHelper helper = new ProbeValuesSqlHelper(context);

        synchronized (this)
        {
            SQLiteDatabase database = helper.getWritableDatabase();

            String localName = this.tableName(context, name, schema);

            if (this.tableExists(context, localName) == false)
                this.createTable(localName, schema);
            try
            {
                Cursor c = database.query(localName, null, null, null, null, null, ProbeValuesProvider.TIMESTAMP);

                matrix = new MatrixCursor(c.getColumnNames());

                while (c.moveToNext())
                {
                    Object[] values = new Object[c.getColumnCount()];

                    for (int i = 0; i < c.getColumnCount(); i++)
                    {
                        switch(ProbeValuesProvider.getType(c, i))
                        {
                            case ProbeValuesProvider.FIELD_TYPE_BLOB:
                                values[i] = c.getBlob(i);
                                break;
                            case ProbeValuesProvider.FIELD_TYPE_INTEGER:
                                values[i] = c.getInt(i);
                                break;
                            case ProbeValuesProvider.FIELD_TYPE_FLOAT:
                                values[i] = c.getFloat(i);
                                break;
                            case ProbeValuesProvider.FIELD_TYPE_STRING:
                                values[i] = c.getString(i);
                                break;
                            case ProbeValuesProvider.FIELD_TYPE_NULL:
                                values[i] = null;
                                break;
                        }
                    }

                    matrix.addRow(values);
                }

                c.close();
            }
            catch (Exception e)
            {
                LogManager.getInstance(context).logException(e);
            }

            database.close();
        }

        return matrix;
    }
}
