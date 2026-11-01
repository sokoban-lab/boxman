package my.boxman;

import java.util.LinkedList;
import java.util.Queue;

// 关卡节点
class mapNode {
  int Num; // 关卡序号
  int Rows; // 地图尺寸
  int Cols;
  int Trun;
  long P_id; // 关卡所属的关卡集的 id
  long Level_id; // 关卡在数据库中的 id
  boolean Solved; // 是否已解
  boolean Lock; // 是否加锁图标
  boolean Select; // 是否被选中
  String fileName; // 编辑关卡时的关卡文档名
  String Author; // 作者
  String Title; // 标题
  String Comment; // 描述
  String Map; // 关卡
  String Map0; // 标准化 XSB
  int L_CRC_Num; // 计算关卡第几转（最小CRC在以本关卡CRC为开头的序列中的序号）
  long key; // 关卡 CRC

  // 从 DB 中加载时调用
  public mapNode(
      int n,
      long Pid,
      long id,
      int fs,
      String m,
      String t,
      String a,
      String c,
      long k,
      int ln,
      String thinXSB,
      int lock) {
    Num = n; // 关卡序号
    Trun = 0;
    Lock = (lock == 1);
    P_id = Pid;
    Level_id = id;
    fileName = null;
    Author = a;
    Title = t;
    Comment = c;
    Solved = (fs == 1);
    Select = false;
    Map = m;
    L_CRC_Num = ln;
    Map0 = thinXSB;
    String[] Arr;
    try {
      Arr = Map.split("\r\n|\n\r|\n|\r|\\|");
      Rows = Arr.length; // 地图高
      Cols = Arr[0].length(); // 地图宽
      key = k;
    } catch (Exception e) { // 无效关卡
      Map = "--";
      Rows = 1;
      Cols = 2;
      key = 0;
      Map0 = "";
      L_CRC_Num = -1;
    }
  }

  // 编辑的新建和导入调用
  public mapNode(int rs, int cs, String[] m, String t, String a, String c) {
    // 编辑关卡时，关卡序号、关卡id、关卡CRC 统一设置为 -1，关卡id小于0用于区别试推状态
    Num = -1; // 关卡序号
    Level_id = -1; // 关卡 id
    key = -1; // 关卡 CRC
    P_id = -1;
    Lock = false;
    fileName = null;
    Author = a;
    Title = t;
    Comment = c;
    Rows = rs;
    Cols = cs;
    if (m == null) Map = null;
    else {
      StringBuilder str = new StringBuilder();
      int n;
      char ch;
      for (int i = 0; i < Rows; i++) {
        n = m[i].length();
        for (int j = 0; j < Cols; j++) {
          ch = j < n ? m[i].charAt(j) : '-';
          switch (ch) {
            case '#':
            case '_':
            case '-':
            case '.':
            case '$':
            case '*':
            case '@':
            case '+':
              break;
            default:
              ch = '-';
          }
          str.append(ch);
        }
        if (i < Rows - 1) str.append('\n');
      }
      Map = str.toString();
    }
  }

  // 编辑的新建和导入调用
  public mapNode(int l, int r, int t, int b, char[][] m) {
    // 编辑关卡时，关卡序号、关卡id、关卡CRC 统一设置为 -1，关卡id小于0用于区别试推状态
    Num = -1; // 关卡序号
    Level_id = -1; // 关卡 id
    key = -1; // 关卡 CRC
    P_id = -1;
    Lock = false;
    fileName = null;
    Author = "";
    Title = "";
    Comment = "";
    Rows = b - t + 1;
    Cols = r - l + 1;

    StringBuilder str = new StringBuilder();
    for (int i = t; i <= b; i++) {
      for (int j = l; j <= r; j++) {
        str.append(m[i][j]);
      }
      if (i < b) str.append('\n');
    }
    Map = str.toString();
  }

  // 查找相似关卡时，记录源关卡
  public mapNode(
      long id, long pid, int rs, int cs, String m, String t, String a, String c, String m0) {
    // 编辑关卡时，关卡序号、关卡id、关卡CRC 统一设置为 -1，关卡id小于0用于区别试推状态
    Num = -1; // 关卡序号
    key = -1; // 关卡 CRC
    fileName = null;
    Lock = false;
    Level_id = id;
    P_id = pid;
    Author = a;
    Title = t;
    Comment = c;
    Rows = rs;
    Cols = cs;
    Map = m;
    Map0 = m0;
  }

  // 常规导入或编辑提交时调用
  public mapNode(String m, String t, String a, String c) {
    Num = -1; // 关卡序号
    Lock = false;
    P_id = -1;
    Level_id = 0;
    fileName = null;
    Author = a;
    Title = t;
    Comment = c;
    Solved = false;

    String[] arr = m.split("\r\n|\n\r|\n|\r|\\|");
    int rs = arr.length, cs = 0;
    for (int k = 0; k < rs; k++) {
      if (cs < arr[k].length()) {
        cs = arr[k].length();
      }
    }
    Rows = rs;
    Cols = cs;

    if (rs > LevelData.m_nMaxRow
        || cs > LevelData.m_nMaxCol
        || rs < 3
        || cs < 3
        || !mapNormalize(arr)) { // 无效关卡
      Map = "--";
      Map0 = "";
      Title = "无效关卡";
      Author = "";
      if (c.trim().isEmpty()) {
        Comment = m + "\nTitle: " + t + "\nAuthor: " + a; // 无效关卡的所有信息，记录在关卡的“说明”中
      } else {
        Comment =
            m
                + "\nTitle: "
                + t
                + "\nAuthor: "
                + a
                + "\nComment: \n"
                + c
                + "\nComment_End: "; // 无效关卡的所有信息，记录在关卡的“说明”中
      }
      if (rs < 3 || cs < 3) {
        Rows = 1;
        Cols = 2;
      }
      L_CRC_Num = -1;
      key = 0;
    }
  }

  int[] dr = {-1, 1, 0, 0, -1, 1, -1, 1}; // 前四个是可移动方向，后面是四个临角
  int[] dc = {0, 0, 1, -1, -1, -1, 1, 1};
  boolean[][] Mark;

  // 地图标准化，包括：简单标准化 -- 保留关卡的墙外造型；精准标准化 -- 不保留关卡的墙外造型，同时计算 CRC 等
  public boolean mapNormalize(String[] Arr) {
    char[][] sMapArray = new char[Rows][Cols];

    int mr = -1, mc = -1;
    int nLen, nRen = 0;
    char ch;
    for (int i = 0; i < Rows; i++) {
      nLen = Arr[i].length();
      for (int j = 0; j < Cols; j++) {
        if (j < nLen) ch = Arr[i].charAt(j);
        else ch = '-';

        switch (ch) {
          case '#':
          case '.':
          case '$':
          case '*':
            break;
          case '@':
            nRen++;
            mr = i;
            mc = j;
            break;
          case '+':
            nRen++;
            mr = i;
            mc = j;
            break;
          default:
            ch = '-';
        }
        sMapArray[i][j] = ch;
      }
    }

    if (nRen != 1) { // 对仓管员 <> 1
      return false;
    }

    Mark = new boolean[Rows][Cols]; // 标志数组，表示地图上某一位置Mark1[i][j]是否访问过。
    int F, mr2, mc2, left = mc, top = mr, right = mc, bottom = mr, nBox = 0, nDst = 0;

    Queue<Integer> P = new LinkedList<Integer>();
    P.offer(mr << 16 | mc);
    Mark[mr][mc] = true;
    while (!P.isEmpty()) { // 走完后，Mark[][]为 1 的，为墙内
      F = P.poll();
      mr = F >>> 16;
      mc = F & 0x0000FFFF;
      switch (sMapArray[mr][mc]) {
        case '$':
          nBox++;
          break;
        case '*':
          nBox++;
          nDst++;
          break;
        case '.':
        case '+':
          nDst++;
          break;
      }
      for (int k = 0; k < 4; k++) { // 仓管员向四个方向走
        mr2 = mr + dr[k];
        mc2 = mc + dc[k];
        if (mr2 < 0
            || mr2 >= Rows
            || mc2 < 0
            || mc2 >= Cols
            || // 出界
            Mark[mr2][mc2]
            || sMapArray[mr2][mc2] == '#') continue; // 已访问或遇到墙

        // 调整四至
        if (left > mc2) left = mc2;
        if (top > mr2) top = mr2;
        if (right < mc2) right = mc2;
        if (bottom < mr2) bottom = mr2;

        P.add(mr2 << 16 | mc2);
        Mark[mr2][mc2] = true; // 标记为已访问
      }
    }

    if (nBox != nDst || nBox < 1 || nDst < 1) { // 可达区域内的箱子与目标点数不正确
      return false;
    }

    // 标准化后的尺寸（八转）
    int nRows = bottom - top + 1 + 2;
    int nCols = right - left + 1 + 2;
    char[][] aMap0 = new char[nRows][nCols];
    char[][] aMap1 = new char[nCols][nRows];
    char[][] aMap2 = new char[nRows][nCols];
    char[][] aMap3 = new char[nCols][nRows];
    char[][] aMap4 = new char[nRows][nCols];
    char[][] aMap5 = new char[nCols][nRows];
    char[][] aMap6 = new char[nRows][nCols];
    char[][] aMap7 = new char[nCols][nRows];

    // 整理关卡元素
    for (int i = 0; i < Rows; i++) {
      for (int j = 0; j < Cols; j++) {
        ch = sMapArray[i][j];
        if (Mark[i][j]) { // 墙内
          if (!(ch == '-' || ch == '.' || ch == '$' || ch == '*' || ch == '@'
              || ch == '+')) { // 无效元素
            ch = '-';
            sMapArray[i][j] = ch;
          }
        } else { // 墙外造型
          if (ch == '*' || ch == '$') {
            ch = '#';
            sMapArray[i][j] = ch;
          } else if (!(ch == '#' || ch == '_')) { // 无效元素
            ch = '_';
            sMapArray[i][j] = ch;
          }
        }
        if (i >= top && i <= bottom && j >= left && j <= right) { // “四至”范围内
          if (Mark[i][j]) aMap0[i - top + 1][j - left + 1] = ch; // 标准化关卡的有效元素（暂时空出四周）
          else aMap0[i - top + 1][j - left + 1] = '_';
        }
      }
    }

    // 关卡最小化
    int mTop = 0, mLeft = 0, mBottom = Rows - 1, mRight = Cols - 1;
    for (int k = 0, t; k < Rows; k++) {
      t = 0;
      while (t < Cols && sMapArray[k][t] == '_') t++;
      if (t == Cols) mTop++;
      else break;
    }
    for (int k = Rows - 1, t; k > mTop; k--) {
      t = 0;
      while (t < Cols && sMapArray[k][t] == '_') t++;
      if (t == Cols) mBottom--;
      else break;
    }
    if (mBottom - mTop < 2) return false;

    for (int k = 0, t; k < Cols; k++) {
      t = mTop;
      while (t <= mBottom && sMapArray[t][k] == '_') t++;
      if (t > mBottom) mLeft++;
      else break;
    }
    for (int k = Cols - 1, t; k > mLeft; k--) {
      t = mTop;
      while (t <= mBottom && sMapArray[t][k] == '_') t++;
      if (t > mBottom) mRight--;
      else break;
    }
    if (mRight - mLeft < 2) return false;

    StringBuilder s1 = new StringBuilder(); // 保持关卡原貌，对墙外造型部分不再清理
    for (int i = mTop; i <= mBottom; i++) {
      for (int j = mLeft; j <= mRight; j++) {
        s1.append(sMapArray[i][j]);
      }
      if (i < Rows - 1) s1.append('\n');
    }
    Map = s1.toString(); // 关卡原貌，已做简单标准化（保留墙外造型）
    Rows = mBottom - mTop + 1;
    Cols = mRight - mLeft + 1;

    // 标准化关卡的四周填充 '_'
    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        if (i == 0 || j == 0 || i == nRows - 1 || j == nCols - 1) aMap0[i][j] = '_';
      }
    }

    // 标准化
    for (int i = 1; i < nRows - 1; i++) {
      for (int j = 1; j < nCols - 1; j++) {
        if (aMap0[i][j] != '_' && aMap0[i][j] != '#') { // 探查内部有效元素的八个方位，是否可以安排墙壁
          if (aMap0[i - 1][j] == '_') aMap0[i - 1][j] = '#';
          if (aMap0[i + 1][j] == '_') aMap0[i + 1][j] = '#';
          if (aMap0[i][j - 1] == '_') aMap0[i][j - 1] = '#';
          if (aMap0[i][j + 1] == '_') aMap0[i][j + 1] = '#';
          if (aMap0[i + 1][j - 1] == '_') aMap0[i + 1][j - 1] = '#';
          if (aMap0[i + 1][j + 1] == '_') aMap0[i + 1][j + 1] = '#';
          if (aMap0[i - 1][j - 1] == '_') aMap0[i - 1][j - 1] = '#';
          if (aMap0[i - 1][j + 1] == '_') aMap0[i - 1][j + 1] = '#';
        }
      }
    }

    // 标准化后的八转：关卡先顺时针旋转（得到：0转、1转、2转、3转），4转为0转的左右镜像，4转再顺时针旋转（得到：4转、5转、6转、7转）
    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        aMap1[j][nRows - 1 - i] = aMap0[i][j];
        aMap2[nRows - 1 - i][nCols - 1 - j] = aMap0[i][j];
        aMap3[nCols - 1 - j][i] = aMap0[i][j];
        aMap4[i][nCols - 1 - j] = aMap0[i][j];
        aMap5[nCols - 1 - j][nRows - 1 - i] = aMap0[i][j];
        aMap6[nRows - 1 - i][j] = aMap0[i][j];
        aMap7[j][i] = aMap0[i][j];
      }
    }

    StringBuilder s20 = new StringBuilder(); // 0 转
    StringBuilder s21 = new StringBuilder(); // 1 转
    StringBuilder s22 = new StringBuilder(); // 2 转
    StringBuilder s23 = new StringBuilder(); // 3 转
    StringBuilder s24 = new StringBuilder(); // 4 转
    StringBuilder s25 = new StringBuilder(); // 5 转
    StringBuilder s26 = new StringBuilder(); // 6 转
    StringBuilder s27 = new StringBuilder(); // 7 转

    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        s20.append(aMap0[i][j]);
        s22.append(aMap2[i][j]);
        s24.append(aMap4[i][j]);
        s26.append(aMap6[i][j]);
      }
      if (i < nRows - 1) {
        s20.append('\n');
        s22.append('\n');
        s24.append('\n');
        s26.append('\n');
      }
    }
    for (int i = 0; i < nCols; i++) {
      for (int j = 0; j < nRows; j++) {
        s21.append(aMap1[i][j]);
        s23.append(aMap3[i][j]);
        s25.append(aMap5[i][j]);
        s27.append(aMap7[i][j]);
      }
      if (i < nCols - 1) {
        s21.append('\n');
        s23.append('\n');
        s25.append('\n');
        s27.append('\n');
      }
    }

    // 计算八转的最小 CRC，及其处于八转的第几转
    long[] key8 = {0, 0, 0, 0, 0, 0, 0, 0};
    key8[1] = LevelData.getCRC32(s21.toString());
    key8[2] = LevelData.getCRC32(s22.toString());
    key8[3] = LevelData.getCRC32(s23.toString());
    key8[4] = LevelData.getCRC32(s24.toString());
    key8[5] = LevelData.getCRC32(s25.toString());
    key8[6] = LevelData.getCRC32(s26.toString());
    key8[7] = LevelData.getCRC32(s27.toString());
    Map0 = s20.toString(); // 精准标准化后的关卡
    key = LevelData.getCRC32(Map0);
    L_CRC_Num = 0;

    for (int i = 1; i < 8; i++) {
      if (key > key8[i]) {
        key = key8[i];
        L_CRC_Num = i;
      }
    }

    return true;
  }
} // end class
