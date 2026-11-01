package my.boxman;

import java.util.ArrayList;
import java.util.zip.CRC32;

/** 两端共享的关卡数据与系统设置。 */
public class LevelData {

  public static int m_nMaxSteps = 500000; // 保存答案到 DB 步数限制的
  public static int m_nMaxRow = 100; // 地图最大尺寸
  public static int m_nMaxCol = 100;

  public static String sRoot; // 路径根
  public static String sPath = "/"; // 根目录

  public static boolean m_StateIsRedy = false; // 状态是否准备就绪
  public static ans_Node m_State; // 读入的状态
  public static ArrayList<state_Node> mState1 = new ArrayList<state_Node>(); // 状态
  public static ArrayList<state_Node> mState2 = new ArrayList<state_Node>(); // 答案
  public static ArrayList<set_Node> mSets0 = new ArrayList<set_Node>(); // 关卡集列表 0 -- 入门
  public static ArrayList<set_Node> mSets1 = new ArrayList<set_Node>(); // 关卡集列表 1 -- 进阶
  public static ArrayList<set_Node> mSets2 = new ArrayList<set_Node>(); // 关卡集列表 2 -- 塑形
  public static ArrayList<set_Node> mSets3 = new ArrayList<set_Node>(); // 关卡集列表 3 -- 扩展
  public static ArrayList<mapNode> m_lstMaps = new ArrayList<mapNode>(); // 地图列表
  public static mapNode curMap, oldMap; // 当前关卡，查找相似关卡时的源
  public static int curMapNum; // 编辑关卡时，记录当前状态

  public static boolean isLurd = false; // 是否导入答案
  public static boolean isComment = false; // 是否导入答案的备注
  public static int[] m_Nums = {0, 0, 0, 0}; // 导入统计

  public static byte[][] Trun8 = { // 关卡8方位旋转之n转换算数组
    {0, 3, 2, 1, 4, 5, 6, 7},
    {1, 0, 3, 2, 7, 4, 5, 6},
    {2, 1, 0, 3, 6, 7, 4, 5},
    {3, 2, 1, 0, 5, 6, 7, 4},
    {4, 7, 6, 5, 0, 1, 2, 3},
    {5, 4, 7, 6, 3, 0, 1, 2},
    {6, 5, 4, 7, 2, 3, 0, 1},
    {7, 6, 5, 4, 1, 2, 3, 0}
  };

  public static char[][] DIR = { // 答案8方位旋转之n转换算数组
    {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'},
    {'u', 'r', 'd', 'l', 'U', 'R', 'D', 'L'},
    {'r', 'd', 'l', 'u', 'R', 'D', 'L', 'U'},
    {'d', 'l', 'u', 'r', 'D', 'L', 'U', 'R'},
    {'r', 'u', 'l', 'd', 'R', 'U', 'L', 'D'},
    {'d', 'r', 'u', 'l', 'D', 'R', 'U', 'L'},
    {'l', 'd', 'r', 'u', 'L', 'D', 'R', 'U'},
    {'u', 'l', 'd', 'r', 'U', 'L', 'D', 'R'}
  };

  public static String J_Title; // 关卡集"说明"
  public static String J_Author; // 关卡集"作者"
  public static String J_Comment; // 关卡集"注释"
  public static long m_Set_id; // 增补关卡之关卡集 id
  public static int[] m_Sets = { // 系统参数设置
    0, 0, 0, 0, 0, 2, 0, 0, 1, 0,
    3, 1, 1, 0, 2, 0, 0, 0, 0, 0,
    0, 1677721600, 1, 0, 0, 1, 0, 1, 0, 0,
    1, 1, 0, 0, 0, 0, 0, 1, 0, 0,
    5, 0
  };

  // 计算关卡或答案的CRC32
  static long getCRC32(String str) {
    if (str == null || str.length() == 0) return 0;
    try {
      CRC32 crc32 = new CRC32();
      crc32.update(str.getBytes("UTF-8"));
      return crc32.getValue();
    } catch (Exception e) {
      return 0;
    }
  }

  // 生成不重复的新关卡集名称
  static String getNewSetName() {
    String name = "新建关卡集_";
    int n = 1, k, len = mSets3.size();

    while (true) {
      // 检查新关卡集的名称有没有重复
      k = 0;
      for (; k < len; k++) {
        if (mSets3.get(k).title.equals(name + (n))) {
          break;
        }
      }
      // 直到新关卡集的名称有没有重复
      if (k == len) {
        break;
      }
      n++;
    }

    return name + n;
  }

  // 根据答案的旋转数计算出关卡某旋转的答案
  static String getANS(String str, int ans_num, int level_num) {
    if (ans_num == level_num) return str;

    StringBuilder ans = new StringBuilder();

    int len = str.length();
    for (int k = 0; k < len; k++) {
      switch (str.charAt(k)) {
        case 'l':
          ans.append(DIR[Trun8[ans_num][level_num]][0]);
          break;
        case 'u':
          ans.append(DIR[Trun8[ans_num][level_num]][1]);
          break;
        case 'r':
          ans.append(DIR[Trun8[ans_num][level_num]][2]);
          break;
        case 'd':
          ans.append(DIR[Trun8[ans_num][level_num]][3]);
          break;
        case 'L':
          ans.append(DIR[Trun8[ans_num][level_num]][4]);
          break;
        case 'U':
          ans.append(DIR[Trun8[ans_num][level_num]][5]);
          break;
        case 'R':
          ans.append(DIR[Trun8[ans_num][level_num]][6]);
          break;
        case 'D':
          ans.append(DIR[Trun8[ans_num][level_num]][7]);
      }
    }
    return ans.toString();
  }
}
