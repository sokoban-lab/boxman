package my.boxman;

/** 关卡库所需的最小游标接口。 */
public interface DbCursor {

  int getCount();

  boolean moveToFirst();

  boolean moveToLast();

  boolean moveToNext();

  int getColumnIndex(String columnName);

  String getString(int columnIndex);

  int getInt(int columnIndex);

  long getLong(int columnIndex);

  void close();
}
