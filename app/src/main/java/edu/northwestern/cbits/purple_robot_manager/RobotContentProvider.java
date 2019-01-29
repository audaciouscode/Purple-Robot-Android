package edu.northwestern.cbits.purple_robot_manager;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;

import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;

public class RobotContentProvider extends ContentProvider
{
    public static final String AUTHORITY = "edu.northwestern.cbits.purple_robot_manager.content";

    private static final int RECENT_PROBE_VALUE_LIST = 1;
    private static final int RECENT_PROBE_VALUE = 2;

    private static final int SNAPSHOT_LIST = 3;
    private static final int SNAPSHOT = 4;

    private static final int DATABASE_VERSION = 3;
    private static final String DATABASE = "purple_robot.db";

    private static final String RECENT_PROBE_VALUES_TABLE = "recent_probe_values";
    private static final String SNAPSHOTS_TABLE = "snapshots";

    public final static Uri RECENT_PROBE_VALUES = Uri.parse("content://" + AUTHORITY + "/" + RECENT_PROBE_VALUES_TABLE);
    public final static Uri SNAPSHOTS = Uri.parse("content://" + AUTHORITY + "/" + SNAPSHOTS_TABLE);

    private UriMatcher _uriMatcher = null;
    private SQLiteOpenHelper _openHelper = null;
    private SQLiteDatabase _db = null;

    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs)
    {
        int result = 0;

        switch (this._uriMatcher.match(uri))
        {
            case RobotContentProvider.RECENT_PROBE_VALUE_LIST:
                result = this._db.delete(RobotContentProvider.RECENT_PROBE_VALUES_TABLE, selection, selectionArgs);
                break;
            case RobotContentProvider.SNAPSHOT_LIST:
                result = this._db.delete(RobotContentProvider.SNAPSHOTS_TABLE, selection, selectionArgs);
                break;
        }

        return result;
    }

    public Uri insert(@NonNull Uri uri, ContentValues values)
    {
        Uri newUri = uri;

        try
        {
            newUri = ContentUris.withAppendedId(uri, values.getAsLong("_id"));
        }
        catch (NullPointerException e)
        {
            LogManager.getInstance(this.getContext()).logException(e);
        }

        switch (this._uriMatcher.match(uri))
        {
        case RobotContentProvider.RECENT_PROBE_VALUE_LIST:
            if (this.update(newUri, values, null, null) == 1)
                return newUri;

            break;
        case RobotContentProvider.SNAPSHOT_LIST:
            long id = this._db.insert(RobotContentProvider.SNAPSHOTS_TABLE, null, values);

            return ContentUris.withAppendedId(uri, id);
        }

        return null;
    }

    public boolean onCreate()
    {
        this._uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        this._uriMatcher.addURI(RobotContentProvider.AUTHORITY, RobotContentProvider.RECENT_PROBE_VALUES_TABLE,
                RobotContentProvider.RECENT_PROBE_VALUE_LIST);
        this._uriMatcher.addURI(RobotContentProvider.AUTHORITY, RobotContentProvider.RECENT_PROBE_VALUES_TABLE + "/#",
                RobotContentProvider.RECENT_PROBE_VALUE);

        this._uriMatcher.addURI(RobotContentProvider.AUTHORITY, RobotContentProvider.SNAPSHOTS_TABLE,
                RobotContentProvider.SNAPSHOT_LIST);
        this._uriMatcher.addURI(RobotContentProvider.AUTHORITY, RobotContentProvider.SNAPSHOTS_TABLE + "/#",
                RobotContentProvider.SNAPSHOT);

        final RobotContentProvider me = this;

        this._openHelper = new SQLiteOpenHelper(this.getContext(), RobotContentProvider.DATABASE, null,
                RobotContentProvider.DATABASE_VERSION)
        {
            public void onCreate(SQLiteDatabase db)
            {
                this.onUpgrade(db, 0, RobotContentProvider.DATABASE_VERSION);
            }

            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
            {
                switch (oldVersion)
                {
                    case 0:
                        db.execSQL(me.getContext().getString(R.string.create_recent_probe_values_sql));
                    case 1:
                        db.execSQL(me.getContext().getString(R.string.create_snapshots_sql));
                    case 2:
                        db.execSQL(me.getContext().getString(R.string.db_update_snapshots_add_audio));
                    default:
                        break;
                }
            }
        };

        this._db = this._openHelper.getWritableDatabase();

        return true;
    }

    public int updateOrInsert(Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        String[] projection = { "_id" };

        int count = 0;
        Cursor c = null;

        switch (this._uriMatcher.match(uri))
        {
            case RobotContentProvider.RECENT_PROBE_VALUE:
                c = this.query(uri, projection, selection, selectionArgs, null);

                if (c.getCount() == 0)
                {
                    this._db.insert(RobotContentProvider.RECENT_PROBE_VALUES_TABLE, null, values);
                    count = 1;
                }
                else
                    count = this.update(RobotContentProvider.RECENT_PROBE_VALUES, values, selection, selectionArgs);

                break;
            case RobotContentProvider.SNAPSHOT:
                c = this.query(uri, projection, selection, selectionArgs, null);

                if (c.getCount() == 0)
                {
                    this._db.insert(RobotContentProvider.SNAPSHOTS_TABLE, null, values);
                    count = 1;
                }
                else
                    count = this.update(RobotContentProvider.SNAPSHOTS, values, selection, selectionArgs);

                break;
        }

        if (c != null && c.isClosed() == false)
            c.close();

        this.getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        int result = 0;

        try
        {
            switch (this._uriMatcher.match(uri))
            {
                case RobotContentProvider.RECENT_PROBE_VALUE_LIST:
                    result = this._db.update(RobotContentProvider.RECENT_PROBE_VALUES_TABLE, values, selection, selectionArgs);

                    if (result == 0)
                    {
                        this._db.insert(RobotContentProvider.RECENT_PROBE_VALUES_TABLE, null, values);

                        result = 1;
                    }

                    break;
                case RobotContentProvider.RECENT_PROBE_VALUE:
                    result = this.updateOrInsert(uri, values, this.buildSingleSelection(selection),
                            this.buildSingleSelectionArgs(uri, selectionArgs));
                    break;

                case RobotContentProvider.SNAPSHOT_LIST:
                    result = this._db.update(RobotContentProvider.SNAPSHOTS_TABLE, values, selection, selectionArgs);

                    if (result == 0)
                    {
                        this._db.insert(RobotContentProvider.SNAPSHOTS_TABLE, null, values);

                        result = 1;
                    }

                    break;
                case RobotContentProvider.SNAPSHOT:
                    result = this.updateOrInsert(uri, values, this.buildSingleSelection(selection), this.buildSingleSelectionArgs(uri, selectionArgs));
                    break;
            }
        }
        catch (SQLiteException e)
        {
            LogManager.getInstance(this.getContext()).logException(e);
        }

        return result;
    }

    public String getType(@NonNull Uri uri)
    {
        switch (this._uriMatcher.match(uri))
        {
            case RobotContentProvider.RECENT_PROBE_VALUE_LIST:
                return "vnd.android.cursor.dir/vnd.edu.northwestern.cbits.purple_robot_manager.content.recent_probe_value";
            case RobotContentProvider.RECENT_PROBE_VALUE:
                return "vnd.android.cursor.item/vnd.edu.northwestern.cbits.purple_robot_manager.content.recent_probe_value";
            case RobotContentProvider.SNAPSHOT_LIST:
                return "vnd.android.cursor.dir/vnd.edu.northwestern.cbits.purple_robot_manager.content.snapshot";
            case RobotContentProvider.SNAPSHOT:
                return "vnd.android.cursor.item/vnd.edu.northwestern.cbits.purple_robot_manager.content.snapshot";
        }

        return null;
    }

    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        Cursor c = null;
        switch (this._uriMatcher.match(uri))
        {
            case RobotContentProvider.RECENT_PROBE_VALUE_LIST:
                c = this._db.query(RobotContentProvider.RECENT_PROBE_VALUES_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case RobotContentProvider.RECENT_PROBE_VALUE:
                c = this._db.query(RobotContentProvider.RECENT_PROBE_VALUES_TABLE, projection, this.buildSingleSelection(selection), this.buildSingleSelectionArgs(uri, selectionArgs), null, null, sortOrder);
                break;
            case RobotContentProvider.SNAPSHOT_LIST:
                c = this._db.query(RobotContentProvider.SNAPSHOTS_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case RobotContentProvider.SNAPSHOT:
                c = this._db.query(RobotContentProvider.SNAPSHOTS_TABLE, projection, this.buildSingleSelection(selection), this.buildSingleSelectionArgs(uri, selectionArgs), null, null, sortOrder);
                break;
        }

        String[] colNames = { "_id" };

        if (c == null)
            c = new MatrixCursor(colNames);

        return c;
    }

    public String[] buildSingleSelectionArgs(Uri uri, String[] selectionArgs)
    {
        if (selectionArgs == null)
        {
            selectionArgs = new String[1];
            selectionArgs[0] = uri.getLastPathSegment();
        }
        else
        {
            String[] newSelectionArgs = new String[selectionArgs.length + 1];

            System.arraycopy(selectionArgs, 0, newSelectionArgs, 0, selectionArgs.length);

            newSelectionArgs[selectionArgs.length] = uri.getLastPathSegment();

            selectionArgs = newSelectionArgs;
        }

        return selectionArgs;
    }

    public String buildSingleSelection(String selection)
    {
        if (selection == null)
            selection = "_id = ?";
        else
            selection += " AND _id = ?";

        return selection;
    }
}
