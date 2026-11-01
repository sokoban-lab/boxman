package my.boxman;

import android.database.Cursor;

/** android.database.Cursor 的 DbCursor 适配器。 */
final class AndroidCursor implements DbCursor {

  private final Cursor cursor;

  AndroidCursor(Cursor cursor) {
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
