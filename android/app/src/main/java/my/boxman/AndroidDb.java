package my.boxman;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.Map;

/** Android SQLiteDatabase 的 Db 适配器。 */
final class AndroidDb implements Db {

  private final SQLiteDatabase database;

  AndroidDb(SQLiteDatabase database) {
    this.database = database;
  }

  @Override
  public DbCursor rawQuery(String sql, String[] selectionArgs) {
    return new AndroidCursor(database.rawQuery(sql, selectionArgs));
  }

  @Override
  public DbCursor query(
      String table,
      String[] columns,
      String selection,
      String[] selectionArgs,
      String groupBy,
      String having,
      String orderBy) {
    return new AndroidCursor(
        database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy));
  }

  @Override
  public void execSQL(String sql) {
    database.execSQL(sql);
  }

  @Override
  public long insert(String table, String nullColumnHack, DbValues values) {
    return database.insert(table, nullColumnHack, toContentValues(values));
  }

  @Override
  public int delete(String table, String whereClause, String[] whereArgs) {
    return database.delete(table, whereClause, whereArgs);
  }

  @Override
  public void close() {
    database.close();
  }

  private static ContentValues toContentValues(DbValues values) {
    ContentValues contentValues = new ContentValues();
    for (Map.Entry<String, Object> entry : values.asMap().entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof String) {
        contentValues.put(key, (String) value);
      } else if (value instanceof Integer) {
        contentValues.put(key, (Integer) value);
      } else if (value instanceof Long) {
        contentValues.put(key, (Long) value);
      } else if (value instanceof byte[]) {
        contentValues.put(key, (byte[]) value);
      } else {
        throw new IllegalArgumentException("不支持的列值类型：" + key);
      }
    }
    return contentValues;
  }
}
