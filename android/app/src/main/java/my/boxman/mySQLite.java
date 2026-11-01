package my.boxman;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 负责 Android 端关卡库的定位与打开；读写逻辑在 LevelStore。 */
public class mySQLite extends LevelStore {

  public static mySQLite m_SQL;

  private static String DATABASE_PATH = myMaps.sRoot + myMaps.sPath + "DataBase/"; // 数据库在SD卡路径
  private static final String DB_NAME = "BoxMan.db";
  public SQLiteDatabase mSDB;

  public static mySQLite getInstance(Context context) {
    initDataBase(context);
    if (m_SQL == null) {
      m_SQL = new mySQLite();
    }
    return m_SQL;
  }

  private static void initDataBase(Context context) {
    if (!checkDataBase()) {
      copyDataBase(context);
    }
  }

  private static void copyDataBase(Context context) {
    File dir = new File(DATABASE_PATH);
    if (!dir.exists()) {
      dir.mkdirs();
    }
    try (FileOutputStream os = new FileOutputStream(DATABASE_PATH + DB_NAME);
        InputStream is = context.getResources().getAssets().open(DB_NAME)) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = is.read(buffer)) != -1) {
        os.write(buffer, 0, count);
        os.flush();
      }
    } catch (Exception e) {
      // 与原实现一致：复制失败时不中断启动
    }
  }

  private static boolean checkDataBase() {
    SQLiteDatabase checkDB = null;
    try {
      checkDB =
          SQLiteDatabase.openDatabase(DATABASE_PATH + DB_NAME, null, SQLiteDatabase.OPEN_READONLY);
    } catch (SQLiteException e) {
      // 数据库不存在或不可读
    }
    if (checkDB == null) {
      return false;
    }
    checkDB.close();
    return true;
  }

  public void closeDataBase() {
    if (mSDB != null) {
      mSDB.close();
      detachDatabase();
    }
  }

  public boolean openDataBase() {
    mSDB = SQLiteDatabase.openOrCreateDatabase(DATABASE_PATH + DB_NAME, null);
    if (mSDB == null) {
      return false;
    }
    attachDatabase(new AndroidDb(mSDB));
    return true;
  }
}
