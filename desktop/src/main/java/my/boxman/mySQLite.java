package my.boxman;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import my.boxman.compat.sqlite.Cursor;
import my.boxman.compat.sqlite.SQLiteDatabase;
import my.boxman.compat.sqlite.SQLiteException;

/** 负责桌面端关卡库的定位与打开；读写逻辑在 LevelStore。 */
public class mySQLite extends LevelStore {

  public static mySQLite m_SQL;

  private static String DATABASE_PATH = resolveDataBasePath();
  private static final String DB_NAME = "BoxMan.db";
  public SQLiteDatabase mSDB;

  private static String resolveDataBasePath() {
    String root =
        myMaps.sRoot == null ? System.getProperty("user.home") + "/.boxman/" : myMaps.sRoot;
    String path = myMaps.sPath == null ? "/" : myMaps.sPath;
    return root + path + "DataBase/";
  }

  public static mySQLite getInstance(Object context) {
    return getInstance();
  }

  public static mySQLite getInstance() {
    initDataBase();
    if (m_SQL == null) {
      m_SQL = new mySQLite();
    }
    return m_SQL;
  }

  private static void initDataBase() {
    DATABASE_PATH = resolveDataBasePath();
    if (!checkDataBase()) {
      copyDataBase();
    }
  }

  private static void copyDataBase() {
    String databaseFilename = DATABASE_PATH + DB_NAME;
    File dir = new File(DATABASE_PATH);
    File target = new File(databaseFilename);
    File tmp = new File(databaseFilename + ".tmp");
    FileOutputStream os = null;
    InputStream is = null;

    if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
      throw new IllegalStateException("无法创建关卡库目录：" + DATABASE_PATH);
    }
    try {
      is = mySQLite.class.getResourceAsStream("/assets/" + DB_NAME);
      if (is == null) {
        is = mySQLite.class.getResourceAsStream("/assets/BoxMan.db");
      }
      // 资源取不到时不能静默返回：否则会留下 0 字节的库文件，之后永远被判定为“已存在”。
      if (is == null) {
        throw new IllegalStateException(
            "关卡库资源 /assets/"
                + DB_NAME
                + " 不在 classpath 中。运行时/测试时请把 src/main/resources（或 build/resources/main）加入"
                + " classpath。");
      }

      os = new FileOutputStream(tmp);
      byte[] buffer = new byte[8192];
      int count;
      long total = 0;
      while ((count = is.read(buffer)) != -1) {
        os.write(buffer, 0, count);
        total += count;
      }
      os.flush();
      os.close();
      os = null;

      if (total <= 0) {
        throw new IllegalStateException("关卡库资源为空：" + DB_NAME);
      }
      if (target.exists() && !target.delete()) {
        throw new IllegalStateException("无法覆盖已损坏的关卡库：" + databaseFilename);
      }
      if (!tmp.renameTo(target)) {
        throw new IllegalStateException("无法写入关卡库：" + databaseFilename);
      }
    } catch (IOException e) {
      throw new IllegalStateException("复制关卡库失败：" + databaseFilename, e);
    } finally {
      try {
        if (os != null) os.close();
      } catch (IOException ignored) {
        // 清理阶段忽略关闭异常
      }
      try {
        if (is != null) is.close();
      } catch (IOException ignored) {
        // 清理阶段忽略关闭异常
      }
      if (tmp.exists() && !tmp.delete()) {
        tmp.deleteOnExit();
      }
    }
  }

  private static boolean checkDataBase() {
    String databaseFilename = DATABASE_PATH + DB_NAME;
    File file = new File(databaseFilename);

    // 0 字节文件是上次复制中断留下的半成品，sqlite 能打开却没有任何表。
    if (!file.isFile() || file.length() == 0) {
      return false;
    }

    SQLiteDatabase checkDB = null;
    boolean ok = false;
    try {
      checkDB = SQLiteDatabase.openDatabase(databaseFilename, null, SQLiteDatabase.OPEN_READONLY);
      Cursor cursor =
          checkDB.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table'", null);
      ok = cursor != null && cursor.moveToFirst() && cursor.getInt(0) > 0;
      if (cursor != null) {
        cursor.close();
      }
    } catch (SQLiteException e) {
      ok = false;
    } catch (Exception e) {
      ok = false;
    } finally {
      if (checkDB != null) {
        checkDB.close();
      }
    }
    return ok;
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
    attachDatabase(new CompatDb(mSDB));
    return true;
  }
}
