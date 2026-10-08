package app.hibiki;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteProgram;
import android.database.sqlite.SQLiteStatement;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/**
 * One SQLite connection for drizzle's sqlite-proxy driver (src/platform/android/db.ts). Values are
 * bound with their real types - a number as INTEGER/REAL, a string as TEXT - because binding
 * everything as text (what rawQuery does) changes how SQLite compares and stores them. Statements
 * run one at a time on a single thread, in the order they were issued.
 */
@CapacitorPlugin(name = "HibikiDb")
public class HibikiDbPlugin extends Plugin {

    private final ExecutorService thread = Executors.newSingleThreadExecutor();
    private SQLiteDatabase db;

    private void onDbThread(PluginCall call, Runnable job) {
        thread.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void open(PluginCall call) {
        String path = call.getString("path");
        onDbThread(call, () -> {
            if (db == null || !db.isOpen()) {
                db = SQLiteDatabase.openOrCreateDatabase(path, null);
                db.enableWriteAheadLogging();
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void close(PluginCall call) {
        onDbThread(call, () -> {
            if (db != null) db.close();
            db = null;
            call.resolve();
        });
    }

    /** Runs a statement that returns nothing (DDL, a migration step, PRAGMA). */
    @PluginMethod
    public void exec(PluginCall call) {
        String sql = call.getString("sql");
        onDbThread(call, () -> {
            db.execSQL(sql);
            call.resolve();
        });
    }

    /** method: "run" (no rows), "all"/"values" (every row), "get" (first row or none). */
    @PluginMethod
    public void query(PluginCall call) {
        String sql = call.getString("sql");
        String method = call.getString("method", "all");
        JSONArray params = call.getArray("params", new JSArray());
        onDbThread(call, () -> {
            JSObject result = new JSObject();
            JSArray rows = new JSArray();
            if ("run".equals(method)) {
                try (SQLiteStatement statement = db.compileStatement(sql)) {
                    bind(statement, params);
                    statement.execute();
                }
            } else {
                try (Cursor cursor = db.rawQueryWithFactory((database, driver, table, query) -> {
                    bind(query, params);
                    return new SQLiteCursor(driver, table, query);
                }, sql, null, null)) {
                    int columns = cursor.getColumnCount();
                    while (cursor.moveToNext()) {
                        JSArray row = new JSArray();
                        for (int i = 0; i < columns; i++) row.put(value(cursor, i));
                        rows.put(row);
                        if ("get".equals(method)) break;
                    }
                }
            }
            result.put("rows", rows);
            call.resolve(result);
        });
    }

    private static void bind(SQLiteProgram program, JSONArray params) {
        try {
            for (int i = 0; i < params.length(); i++) {
                int index = i + 1;
                Object value = params.isNull(i) ? null : params.get(i);
                if (value == null) program.bindNull(index);
                else if (value instanceof Boolean) program.bindLong(index, ((Boolean) value) ? 1 : 0);
                else if (value instanceof Integer || value instanceof Long) program.bindLong(index, ((Number) value).longValue());
                else if (value instanceof Number) {
                    double d = ((Number) value).doubleValue();
                    if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 9.007199254740992E15) program.bindLong(index, (long) d);
                    else program.bindDouble(index, d);
                } else program.bindString(index, String.valueOf(value));
            }
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Object value(Cursor cursor, int i) {
        switch (cursor.getType(i)) {
            case Cursor.FIELD_TYPE_NULL:
                return org.json.JSONObject.NULL;
            case Cursor.FIELD_TYPE_INTEGER:
                return cursor.getLong(i);
            case Cursor.FIELD_TYPE_FLOAT:
                return cursor.getDouble(i);
            case Cursor.FIELD_TYPE_BLOB:
                return Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP);
            default:
                return cursor.getString(i);
        }
    }
}
