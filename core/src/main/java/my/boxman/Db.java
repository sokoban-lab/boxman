package my.boxman;

/** 关卡库所需的最小数据库接口。 */
public interface Db {

  DbCursor rawQuery(String sql, String[] selectionArgs);

  DbCursor query(
      String table,
      String[] columns,
      String selection,
      String[] selectionArgs,
      String groupBy,
      String having,
      String orderBy);

  void execSQL(String sql);

  long insert(String table, String nullColumnHack, DbValues values);

  int delete(String table, String whereClause, String[] whereArgs);

  void close();
}
