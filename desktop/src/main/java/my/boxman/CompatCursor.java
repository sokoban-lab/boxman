package my.boxman;

import my.boxman.compat.sqlite.Cursor;

/** 桌面 compat Cursor 的 DbCursor 适配器。 */
final class CompatCursor implements DbCursor {

  private final Cursor cursor;

  CompatCursor(Cursor cursor) {
    this.cursor = cursor;
  }

  @Override
  public int getCount() {
    return cursor.getCount();
  }

  @Override
  public boolean moveToFirst() {
    return cursor.moveToFirst();
  }

  @Override
  public boolean moveToLast() {
    return cursor.moveToLast();
  }

  @Override
  public boolean moveToNext() {
    return cursor.moveToNext();
  }

  @Override
  public int getColumnIndex(String columnName) {
    return cursor.getColumnIndex(columnName);
  }

  @Override
  public String getString(int columnIndex) {
    return cursor.getString(columnIndex);
  }

  @Override
  public int getInt(int columnIndex) {
    return cursor.getInt(columnIndex);
  }

  @Override
  public long getLong(int columnIndex) {
    return cursor.getLong(columnIndex);
  }

  @Override
  public void close() {
    cursor.close();
  }
}
