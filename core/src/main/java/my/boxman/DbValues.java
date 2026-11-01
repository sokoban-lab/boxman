package my.boxman;

import java.util.LinkedHashMap;
import java.util.Map;

/** 插入语句使用的列值集合。 */
public final class DbValues {

  private final Map<String, Object> values = new LinkedHashMap<>();

  public void put(String key, String value) {
    values.put(key, value);
  }

  public void put(String key, int value) {
    values.put(key, value);
  }

  public void put(String key, long value) {
    values.put(key, value);
  }

  public void put(String key, byte[] value) {
    values.put(key, value);
  }

  public Map<String, Object> asMap() {
    return values;
  }
}
