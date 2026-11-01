package my.boxman;

import java.util.Map;
import my.boxman.compat.sqlite.ContentValues;
import my.boxman.compat.sqlite.SQLiteDatabase;

/** 桌面 compat SQLiteDatabase 的 Db 适配器。 */
final class CompatDb implements Db {

  private final SQLiteDatabase database;

  CompatDb(SQLiteDatabase database) {
    this.database = database;
  }

  @Override
  public DbCursor rawQuery(String sql, String[] selectionArgs) {
    return new CompatCursor(database.rawQuery(sql, selectionArgs));
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
    return new CompatCursor(
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
