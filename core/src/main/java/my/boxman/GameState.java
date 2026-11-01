package my.boxman;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import my.boxman.jsoko.DiagonalLock;
import my.boxman.jsoko.FreezeLock;
import my.boxman.jsoko.LockBoard;

/**
 * 一局游戏的完整状态与规则 —— 与界面无关的部分。
 *
 * <p>棋盘、仓管员位置、动作栈、正逆推计数和死锁判定都在这里；两个客户端只保留动画、对话框和绘制。
 */
public class GameState implements LockBoard {

  // 迷宫
  public char[][] m_cArray;
  public char[][] m_cArray0;
  public char[][] bk_cArray;
  public char[][] ls_bk_cArray;

  // 迷宫选择
  public byte[][] m_selArray;
  public byte[][] bk_selArray;

  // 迷宫箱子编号（人为 / 自动）
  public short[][] m_iBoxNum;
  public short[][] m_iBoxNum2;

  // 检查冻结时使用的临时数字
  public byte[][] m_Freeze;

  // 15、为逆推死点；16、为正推网锁点；7、为正推箱子初位；8、为正推初态地板；
  // 11、为哪个箱子能动或未动过；12、为正推未被使用过的地板；44、是否显示标尺
  public boolean[][] mark15;
  public boolean[][] mark16;
  public boolean[][] mark7;
  public boolean[][] mark8;
  public boolean[][] mark11;
  public boolean[][] mark12;
  public boolean[][] mark44;
  public short[][] mark14; // 逆推可推的点
  public byte[][] mark41; // 网锁标志

  public char[][] mArray9; // 检查死锁用的临时地图（空地图）
  public MyPathfinder mPF; // 检查死锁用的探路者
  public MyPathfinder mPathfinder; // 探路者

  public DiagonalLock closedDiagonalLock;
  public FreezeLock freezeDeadlock;

  // 正推：目标数、完成数
  public int m_nGoals;
  public int m_nGoals_OK;
  // 逆推：目标数、完成数
  public int m_nGoals_2;
  public int m_nGoals_OK_2;

  public int m_nRow, m_nCol; // 正推仓管员位置
  public int m_nRow2, m_nCol2; // 逆推仓管员位置
  public int m_nRow0, m_nCol0; // 逆推初始占位
  public int m_nRow3, m_nCol3; // 逆推过关时用，记录正推地图的初始占位
  public int m_nRow4, m_nCol4; // 导入用，记录逆推地图的初始占位
  public int m_iR9, m_iC9; // 正推箱子停止位置
  public int m_iR10, m_iC10; // 逆推箱子停止位置
  public int b_nRow = -1, b_nCol = -1; // 动画中的箱子
  public byte m_Dir; // 当前动作方向

  public int m_nStep; // 待执行的动作数
  public int m_nLastSteps = -1;
  public int m_Gif_Start = 0;
  public int[] m_iStep = new int[4]; // 推、移步数

  public LinkedList<Byte> m_lstMovedHistory = new LinkedList<Byte>();
  public LinkedList<Byte> m_lstMovUnDo;
  public LinkedList<Byte> m_lstMovReDo;
  public LinkedList<Byte> m_lstMovUnDo2;
  public LinkedList<Byte> m_lstMovReDo2;

  public boolean m_bPush;
  public boolean m_bMoved;
  public boolean m_bBusing;
  public boolean m_bYanshi;
  public boolean m_bYanshi2;
  public boolean m_bNetLock;
  public boolean m_bACT_ERROR;
  public boolean m_bACT_IgnoreCase;

  /** 最近一次正推所移动箱子的原位置（无则为 -1），供界面播放动画。 */
  public int lastBoxFromRow = -1;

  public int lastBoxFromCol = -1;

  private int i, j, i2, j2;
  private static final int[] DR_REDO1 = {0, 0, -1, 0, 1, 0, -2, 0, 2};
  private static final int[] DC_REDO1 = {0, -1, 0, 1, 0, -2, 0, 2, 0};
  private static final int[] DR_UNDO1 = {0, 0, 1, 0, -1, 0, -1, 0, 1};
  private static final int[] DC_UNDO1 = {0, 1, 0, -1, 0, -1, 0, 1, 0};
  private static final int[] DR_REDO2 = {0, 0, -1, 0, 1, 0, 1, 0, -1};
  private static final int[] DC_REDO2 = {0, -1, 0, 1, 0, 1, 0, -1, 0};
  private static final int[] DR_UNDO2 = {0, 0, 1, 0, -1, 0, 2, 0, -2};
  private static final int[] DC_UNDO2 = {0, 1, 0, -1, 0, 2, 0, -2, 0};
  private static final byte[] DR4 = {0, 0, -1, 1};
  private static final byte[] DC4 = {-1, 1, 0, 0};

  public GameState() {
    closedDiagonalLock = new DiagonalLock(this);
    freezeDeadlock = new FreezeLock(this);
  }

  @Override
  public char[][] cells() {
    return m_cArray;
  }

  @Override
  public byte[][] freezeMarks() {
    return m_Freeze;
  }

  @Override
  public int playerRow() {
    return m_nRow;
  }

  @Override
  public int playerCol() {
    return m_nCol;
  }

  /** 清空上一局的仓管员位置与动作栈。棋盘等尺寸相关的数组由 {@link #loadLevel} 建立。 */
  public void beginNewGame() {
    m_bBusing = true;
    m_nRow = -1;
    m_nCol = -1;
    m_nRow2 = -1;
    m_nCol2 = -1;
    m_nRow0 = -1;
    m_nCol0 = -1;
    m_nRow4 = -1;
    m_nCol4 = -1;

    if (m_lstMovUnDo != null) m_lstMovUnDo.clear();
    if (m_lstMovReDo != null) m_lstMovReDo.clear();
    if (m_lstMovUnDo2 != null) m_lstMovUnDo2.clear();
    if (m_lstMovReDo2 != null) m_lstMovReDo2.clear();
    m_lstMovUnDo = new LinkedList<Byte>();
    m_lstMovReDo = new LinkedList<Byte>();
    m_lstMovUnDo2 = new LinkedList<Byte>();
    m_lstMovReDo2 = new LinkedList<Byte>();
  }

  /** 按关卡数据建立正推/逆推棋盘，并计算目标数与箱子编号。 */
  public void loadLevel(String map, int rows, int cols) {
    m_iBoxNum = new short[rows][cols];
    m_iBoxNum2 = new short[rows][cols];
    m_Freeze = new byte[rows][cols];
    m_cArray = new char[rows][cols];
    m_cArray0 = new char[rows][cols];
    bk_cArray = new char[rows][cols];
    m_selArray = new byte[rows][cols];
    bk_selArray = new byte[rows][cols];
    ls_bk_cArray = bk_cArray;

    mark7 = new boolean[rows][cols];
    mark8 = new boolean[rows][cols];
    mark11 = new boolean[rows][cols];
    mark12 = new boolean[rows][cols];
    mark44 = new boolean[rows][cols];
    mark41 = new byte[rows][cols];

    String[] arr = map.split("\r\n|\n\r|\n|\r|\\|");
    char ch;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        m_iBoxNum[r][c] = -1;
        m_iBoxNum2[r][c] = -1;
        ch = arr[r].charAt(c);
        switch (ch) {
          case '#':
          case '_':
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = ch;
            break;
          case '-':
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = ch;
            mark8[r][c] = true;
            break;
          case '*':
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = ch;
            mark7[r][c] = true;
            break;
          case '+':
            m_nRow = r;
            m_nCol = c;
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = '$';
            break;
          case '.':
            m_nGoals++;
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = '$';
            mark8[r][c] = true;
            break;
          case '$':
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = '.';
            mark7[r][c] = true;
            break;
          case '@':
            m_nRow = r;
            m_nCol = c;
            m_cArray[r][c] = ch;
            m_cArray0[r][c] = ch;
            bk_cArray[r][c] = '-';
            break;
          default:
            break;
        }
      }
    }

    mark14 = null;
    mark15 = null;
    mark16 = null;
    mArray9 = null;

    countBoxes(m_cArray, m_nRow, m_nCol);
    mPathfinder = new MyPathfinder(rows, cols, () -> LevelData.m_Sets[17] == 1);

    m_iStep[0] = 0;
    m_iStep[1] = 0;
    m_Gif_Start = 0;
    m_iStep[2] = 0;
    m_iStep[3] = 0;
    m_nStep = 0;

    m_nRow3 = m_nRow;
    m_nCol3 = m_nCol;
  }

  /** 关卡成功载入后的会话复位。 */
  public void endNewGame() {
    m_bBusing = false;
    m_bMoved = false;
    m_bYanshi = false;
    m_bYanshi2 = false;
    m_nLastSteps = -1;
  }

  private void countBoxes(char[][] level, int mr, int mc) {
    int nRows = level.length;
    int nCols = level[0].length;

    boolean[][] mark = new boolean[nRows][nCols];
    int f, mr2, mc2;

    int[] dr = {-1, 1, 0, 0, -1, 1, -1, 1};
    int[] dc = {0, 0, 1, -1, -1, -1, 1, 1};

    Queue<Integer> p = new LinkedList<Integer>();
    p.offer(mr << 16 | mc);
    mark[mr][mc] = true;
    while (!p.isEmpty()) {
      f = p.poll();
      mr = f >>> 16;
      mc = f & 0x0000FFFF;
      for (int k = 0; k < 4; k++) {
        mr2 = mr + dr[k];
        mc2 = mc + dc[k];
        if (mr2 < 0
            || mr2 >= nRows
            || mc2 < 0
            || mc2 >= nCols
            || mark[mr2][mc2]
            || level[mr2][mc2] == '_'
            || level[mr2][mc2] == '#') {
          continue;
        }

        p.add(mr2 << 16 | mc2);
        mark[mr2][mc2] = true;
      }
    }

    short boxNum = 0;
    m_nGoals = 0;
    m_nGoals_OK = 0;
    m_nGoals_2 = 0;
    m_nGoals_OK_2 = 0;
    for (int r = 0; r < nRows; r++) {
      for (int c = 0; c < nCols; c++) {
        switch (level[r][c]) {
          case '$':
            if (mark[r][c]) {
              boxNum++;
              m_iBoxNum2[r][c] = boxNum;
            }
            m_nGoals_2++;
            break;
          case '*':
            if (mark[r][c]) {
              boxNum++;
              m_iBoxNum2[r][c] = boxNum;
            }
            m_nGoals_OK++;
            m_nGoals++;
            m_nGoals_OK_2++;
            m_nGoals_2++;
            break;
          case '.':
          case '+':
            m_nGoals++;
            break;
          default:
            break;
        }
      }
    }
  }

  /** 正推：执行“推”或“移”1 步。 */
  public void reDo1() {
    lastBoxFromRow = -1;
    lastBoxFromCol = -1;
    if (m_lstMovReDo.isEmpty()) {
      m_bBusing = false;
      m_nStep = 0;
      return;
    }
    m_bBusing = true;
    m_iR9 = -1;
    m_Dir = m_lstMovReDo.pollLast();

    if (m_Dir < 1 || m_Dir > 8) {
      m_bBusing = false;
      m_lstMovReDo.clear();
      m_nStep = 0;
      m_bACT_ERROR = true;
      return;
    }
    m_bPush = (m_Dir > 4);

    if (m_Dir < 5) {
      LevelData.m_Sets[14] = m_Dir;
      LevelData.m_Sets[5] = m_Dir;
      i = m_nRow + DR_REDO1[m_Dir];
      j = m_nCol + DC_REDO1[m_Dir];
      i2 = -1;
      j2 = -1;
    } else {
      LevelData.m_Sets[14] = m_Dir - 4;
      LevelData.m_Sets[5] = m_Dir - 4;
      i = m_nRow + DR_REDO1[m_Dir - 4];
      j = m_nCol + DC_REDO1[m_Dir - 4];
      i2 = m_nRow + DR_REDO1[m_Dir];
      j2 = m_nCol + DC_REDO1[m_Dir];
    }

    if (m_bACT_IgnoreCase) {
      if (m_Dir > 4) {
        if (i >= 0
            && j >= 0
            && i < m_cArray.length
            && j < m_cArray[0].length
            && (m_cArray[i][j] == '-' || m_cArray[i][j] == '.')) {
          m_Dir = (byte) (m_Dir - 4);
          i = m_nRow + DR_REDO1[m_Dir];
          j = m_nCol + DC_REDO1[m_Dir];
          i2 = -1;
          j2 = -1;
        }
      } else {
        if (i >= 0
            && j >= 0
            && i < m_cArray.length
            && j < m_cArray[0].length
            && (m_cArray[i][j] == '$' || m_cArray[i][j] == '*')) {
          m_Dir = (byte) (m_Dir + 4);
          i = m_nRow + DR_REDO1[m_Dir - 4];
          j = m_nCol + DC_REDO1[m_Dir - 4];
          i2 = m_nRow + DR_REDO1[m_Dir];
          j2 = m_nCol + DC_REDO1[m_Dir];
        }
      }
    }

    if (m_Dir > 4) {
      if (i < 0
          || j < 0
          || i2 < 0
          || j2 < 0
          || i >= m_cArray.length
          || j >= m_cArray[0].length
          || i2 >= m_cArray.length
          || j2 >= m_cArray[0].length
          || m_cArray[i2][j2] != '-' && m_cArray[i2][j2] != '.'
          || m_cArray[i][j] != '$' && m_cArray[i][j] != '*') {
        m_lstMovReDo.clear();
        m_bBusing = false;
        m_nStep = 0;
        m_bACT_ERROR = true;
        return;
      }
      lastBoxFromRow = i;
      lastBoxFromCol = j;
      if (m_cArray[i2][j2] == '-') m_cArray[i2][j2] = '$';
      else {
        m_cArray[i2][j2] = '*';
        m_nGoals_OK++;
      }
      m_iBoxNum[i2][j2] = m_iBoxNum[i][j];
      m_iBoxNum[i][j] = -1;
      m_iBoxNum2[i2][j2] = m_iBoxNum2[i][j];
      m_iBoxNum2[i][j] = -1;

      if (m_cArray[i][j] == '$') m_cArray[i][j] = '@';
      else {
        m_cArray[i][j] = '+';
        m_nGoals_OK--;
      }
      if (m_cArray[m_nRow][m_nCol] == '@') m_cArray[m_nRow][m_nCol] = '-';
      else m_cArray[m_nRow][m_nCol] = '.';

      m_nRow = i;
      m_nCol = j;
      m_iR9 = i2;
      m_iC9 = j2;
      b_nRow = i2;
      b_nCol = j2;

      m_lstMovUnDo.offer(m_Dir);
      m_iStep[0]++;
    } else {
      if (i < 0
          || j < 0
          || i >= m_cArray.length
          || j >= m_cArray[0].length
          || m_cArray[i][j] != '-' && m_cArray[i][j] != '.') {
        m_lstMovReDo.clear();
        m_bBusing = false;
        m_nStep = 0;
        m_bACT_ERROR = true;
        return;
      }
      if (m_cArray[i][j] == '-') m_cArray[i][j] = '@';
      else m_cArray[i][j] = '+';
      if (m_cArray[m_nRow][m_nCol] == '@') m_cArray[m_nRow][m_nCol] = '-';
      else m_cArray[m_nRow][m_nCol] = '.';

      m_nRow = i;
      m_nCol = j;

      m_lstMovUnDo.offer(m_Dir);
    }

    m_iStep[1]++;
    m_nStep--;
  }

  /** 正推：撤销 1 步。 */
  public void unDo1() {
    if (m_lstMovUnDo.isEmpty()) {
      m_bBusing = false;
      return;
    }
    m_bBusing = true;
    m_Dir = m_lstMovUnDo.pollLast();

    m_bPush = (m_Dir > 4);

    switch (m_Dir) {
      case 1:
        LevelData.m_Sets[14] = 3;
        LevelData.m_Sets[5] = 3;
        break;
      case 2:
        LevelData.m_Sets[14] = 4;
        LevelData.m_Sets[5] = 4;
        break;
      case 3:
        LevelData.m_Sets[14] = 1;
        LevelData.m_Sets[5] = 1;
        break;
      case 4:
        LevelData.m_Sets[14] = 2;
        LevelData.m_Sets[5] = 2;
        break;
      case 5:
        LevelData.m_Sets[14] = 3;
        LevelData.m_Sets[5] = 1;
        break;
      case 6:
        LevelData.m_Sets[14] = 4;
        LevelData.m_Sets[5] = 2;
        break;
      case 7:
        LevelData.m_Sets[14] = 1;
        LevelData.m_Sets[5] = 3;
        break;
      case 8:
        LevelData.m_Sets[14] = 2;
        LevelData.m_Sets[5] = 4;
        break;
      default:
        break;
    }
    if (m_Dir < 5) {
      i = m_nRow + DR_UNDO1[m_Dir];
      j = m_nCol + DC_UNDO1[m_Dir];
      i2 = -1;
      j2 = -1;
    } else {
      i = m_nRow + DR_UNDO1[m_Dir - 4];
      j = m_nCol + DC_UNDO1[m_Dir - 4];
      i2 = m_nRow + DR_UNDO1[m_Dir];
      j2 = m_nCol + DC_UNDO1[m_Dir];
    }

    if (m_Dir > 4) {
      if ((m_cArray[i][j] == '-' || m_cArray[i][j] == '.')
          && (m_cArray[i2][j2] == '$' || m_cArray[i2][j2] == '*')) {
        if (m_cArray[i][j] == '-') m_cArray[i][j] = '@';
        else m_cArray[i][j] = '+';
        if (m_cArray[m_nRow][m_nCol] == '@') m_cArray[m_nRow][m_nCol] = '$';
        else {
          m_cArray[m_nRow][m_nCol] = '*';
          m_nGoals_OK++;
        }
        if (m_cArray[i2][j2] == '$') m_cArray[i2][j2] = '-';
        else {
          m_cArray[i2][j2] = '.';
          m_nGoals_OK--;
        }
        m_iBoxNum[m_nRow][m_nCol] = m_iBoxNum[i2][j2];
        m_iBoxNum[i2][j2] = -1;
        m_iBoxNum2[m_nRow][m_nCol] = m_iBoxNum2[i2][j2];
        m_iBoxNum2[i2][j2] = -1;

        b_nRow = m_nRow;
        b_nCol = m_nCol;
        m_nRow = i;
        m_nCol = j;

        m_lstMovReDo.offer(m_Dir);
        m_iStep[0]--;
      }
    } else {
      if (m_cArray[i][j] == '-' || m_cArray[i][j] == '.') {
        if (m_cArray[i][j] == '-') m_cArray[i][j] = '@';
        else m_cArray[i][j] = '+';
        if (m_cArray[m_nRow][m_nCol] == '@') m_cArray[m_nRow][m_nCol] = '-';
        else m_cArray[m_nRow][m_nCol] = '.';

        m_nRow = i;
        m_nCol = j;

        m_lstMovReDo.offer(m_Dir);
      }
    }

    m_iStep[1]--;
    if (m_iStep[1] < m_Gif_Start) m_Gif_Start = 0;
    m_nStep--;
  }

  /** 逆推：执行 1 步。 */
  public void reDo2() {
    if (m_lstMovReDo2.isEmpty()) {
      m_bBusing = false;
      m_nStep = 0;
      return;
    }
    m_bBusing = true;
    m_iR10 = -1;
    m_Dir = m_lstMovReDo2.pollLast();

    if (m_Dir < 1 || m_Dir > 8) {
      m_bBusing = false;
      m_lstMovReDo2.clear();
      m_nStep = 0;
      m_bACT_ERROR = true;
      return;
    }
    m_bPush = (m_Dir > 4);

    switch (m_Dir) {
      case 1:
        LevelData.m_Sets[14] = 1;
        LevelData.m_Sets[5] = 1;
        break;
      case 2:
        LevelData.m_Sets[14] = 2;
        LevelData.m_Sets[5] = 2;
        break;
      case 3:
        LevelData.m_Sets[14] = 3;
        LevelData.m_Sets[5] = 3;
        break;
      case 4:
        LevelData.m_Sets[14] = 4;
        LevelData.m_Sets[5] = 4;
        break;
      case 5:
        LevelData.m_Sets[14] = 1;
        LevelData.m_Sets[5] = 3;
        break;
      case 6:
        LevelData.m_Sets[14] = 2;
        LevelData.m_Sets[5] = 4;
        break;
      case 7:
        LevelData.m_Sets[14] = 3;
        LevelData.m_Sets[5] = 1;
        break;
      case 8:
        LevelData.m_Sets[14] = 4;
        LevelData.m_Sets[5] = 2;
        break;
      default:
        break;
    }
    if (m_Dir < 5) {
      i = m_nRow2 + DR_REDO2[m_Dir];
      j = m_nCol2 + DC_REDO2[m_Dir];
      i2 = -1;
      j2 = -1;
    } else {
      i = m_nRow2 + DR_REDO2[m_Dir - 4];
      j = m_nCol2 + DC_REDO2[m_Dir - 4];
      i2 = m_nRow2 + DR_REDO2[m_Dir];
      j2 = m_nCol2 + DC_REDO2[m_Dir];
    }

    if (m_bACT_IgnoreCase) {
      if (m_Dir > 4) {
        if (i >= 0
            && j >= 0
            && i < bk_cArray.length
            && j < bk_cArray[0].length
            && (bk_cArray[i2][j2] == '-' || bk_cArray[i2][j2] == '.')) {
          m_Dir = (byte) (m_Dir - 4);
          i = m_nRow2 + DR_REDO2[m_Dir];
          j = m_nCol2 + DC_REDO2[m_Dir];
          i2 = -1;
          j2 = -1;
        }
      } else {
        if (i >= 0
            && j >= 0
            && i < bk_cArray.length
            && j < bk_cArray[0].length
            && (bk_cArray[m_nRow2 + DR_REDO2[m_Dir]][m_nCol2 + DC_REDO2[m_Dir]] == '$'
                || bk_cArray[m_nRow2 + DR_REDO2[m_Dir]][m_nCol2 + DC_REDO2[m_Dir]] == '*')) {
          m_Dir = (byte) (m_Dir + 4);
          i = m_nRow2 + DR_REDO2[m_Dir - 4];
          j = m_nCol2 + DC_REDO2[m_Dir - 4];
          i2 = m_nRow2 + DR_REDO2[m_Dir];
          j2 = m_nCol2 + DC_REDO2[m_Dir];
        }
      }
    }

    if (m_Dir > 4) {
      if (i < 0
          || j < 0
          || i2 < 0
          || j2 < 0
          || i >= bk_cArray.length
          || j >= bk_cArray[0].length
          || i2 >= bk_cArray.length
          || j2 >= bk_cArray[0].length
          || bk_cArray[i][j] != '-' && bk_cArray[i][j] != '.'
          || bk_cArray[i2][j2] != '$' && bk_cArray[i2][j2] != '*') {
        m_lstMovReDo2.clear();
        m_bBusing = false;
        m_nStep = 0;
        m_bACT_ERROR = true;
        return;
      }
      if (bk_cArray[i][j] == '-') bk_cArray[i][j] = '@';
      else bk_cArray[i][j] = '+';
      if (bk_cArray[m_nRow2][m_nCol2] == '@') bk_cArray[m_nRow2][m_nCol2] = '$';
      else {
        bk_cArray[m_nRow2][m_nCol2] = '*';
        m_nGoals_OK_2++;
      }
      if (bk_cArray[i2][j2] == '$') bk_cArray[i2][j2] = '-';
      else {
        bk_cArray[i2][j2] = '.';
        m_nGoals_OK_2--;
      }

      m_iR10 = m_nRow2;
      m_iC10 = m_nCol2;
      b_nRow = m_nRow2;
      b_nCol = m_nCol2;
      m_nRow2 = i;
      m_nCol2 = j;

      m_lstMovUnDo2.offer(m_Dir);
      m_iStep[2]++;
    } else {
      if (i < 0
          || j < 0
          || i >= bk_cArray.length
          || j >= bk_cArray[0].length
          || bk_cArray[i][j] != '-' && bk_cArray[i][j] != '.') {
        m_lstMovReDo2.clear();
        m_bBusing = false;
        m_nStep = 0;
        m_bACT_ERROR = true;
        return;
      }
      if (bk_cArray[i][j] == '-') bk_cArray[i][j] = '@';
      else bk_cArray[i][j] = '+';
      if (bk_cArray[m_nRow2][m_nCol2] == '@') bk_cArray[m_nRow2][m_nCol2] = '-';
      else bk_cArray[m_nRow2][m_nCol2] = '.';

      m_nRow2 = i;
      m_nCol2 = j;

      m_lstMovUnDo2.offer(m_Dir);
    }

    m_iStep[3]++;
    m_nStep--;
  }

  /** 逆推：撤销 1 步。 */
  public void unDo2() {
    if (m_lstMovUnDo2.isEmpty()) {
      m_bBusing = false;
      return;
    }
    m_bBusing = true;
    m_Dir = m_lstMovUnDo2.pollLast();

    m_bPush = (m_Dir > 4);

    switch (m_Dir) {
      case 1:
      case 5:
        LevelData.m_Sets[14] = 3;
        LevelData.m_Sets[5] = 3;
        break;
      case 2:
      case 6:
        LevelData.m_Sets[14] = 4;
        LevelData.m_Sets[5] = 4;
        break;
      case 3:
      case 7:
        LevelData.m_Sets[14] = 1;
        LevelData.m_Sets[5] = 1;
        break;
      case 4:
      case 8:
        LevelData.m_Sets[14] = 2;
        LevelData.m_Sets[5] = 2;
        break;
      default:
        break;
    }
    if (m_Dir < 5) {
      i = m_nRow2 + DR_UNDO2[m_Dir];
      j = m_nCol2 + DC_UNDO2[m_Dir];
      i2 = -1;
      j2 = -1;
    } else {
      i = m_nRow2 + DR_UNDO2[m_Dir - 4];
      j = m_nCol2 + DC_UNDO2[m_Dir - 4];
      i2 = m_nRow2 + DR_UNDO2[m_Dir];
      j2 = m_nCol2 + DC_UNDO2[m_Dir];
    }

    if (m_Dir > 4) {
      if ((bk_cArray[i2][j2] == '-' || bk_cArray[i2][j2] == '.')
          && (bk_cArray[i][j] == '$' || bk_cArray[i][j] == '*')) {
        if (bk_cArray[i2][j2] == '-') bk_cArray[i2][j2] = '$';
        else {
          bk_cArray[i2][j2] = '*';
          m_nGoals_OK_2++;
        }
        if (bk_cArray[i][j] == '$') bk_cArray[i][j] = '@';
        else {
          bk_cArray[i][j] = '+';
          m_nGoals_OK_2--;
        }
        if (bk_cArray[m_nRow2][m_nCol2] == '@') bk_cArray[m_nRow2][m_nCol2] = '-';
        else bk_cArray[m_nRow2][m_nCol2] = '.';

        m_nRow2 = i;
        m_nCol2 = j;
        b_nRow = i2;
        b_nCol = j2;

        m_lstMovReDo2.offer(m_Dir);
        m_iStep[2]--;
      }
    } else {
      if (bk_cArray[i][j] == '-' || bk_cArray[i][j] == '.') {
        if (bk_cArray[i][j] == '-') bk_cArray[i][j] = '@';
        else bk_cArray[i][j] = '+';
        if (bk_cArray[m_nRow2][m_nCol2] == '@') bk_cArray[m_nRow2][m_nCol2] = '-';
        else bk_cArray[m_nRow2][m_nCol2] = '.';

        m_nRow2 = i;
        m_nCol2 = j;

        m_lstMovReDo2.offer(m_Dir);
      }
    }

    m_iStep[3]--;
    m_nStep--;
  }

  /** 撤回一侧的全部动作。 */
  public void undoAll(boolean backward) {
    if (backward) {
      m_nStep = m_lstMovUnDo2.size();
      while (m_nStep > 0) unDo2();
    } else {
      m_nStep = m_lstMovUnDo.size();
      while (m_nStep > 0) unDo1();
    }
    m_nStep = 0;
  }

  /** 逆推仓管员回到初始位置。 */
  public void goHome() {
    if (m_nRow2 != m_nRow0 || m_nCol2 != m_nCol0) {
      if (m_nRow2 > -1) {
        if (bk_cArray[m_nRow2][m_nCol2] == '@') bk_cArray[m_nRow2][m_nCol2] = '-';
        else bk_cArray[m_nRow2][m_nCol2] = '.';
      }

      if (bk_cArray[m_nRow0][m_nCol0] == '-') bk_cArray[m_nRow0][m_nCol0] = '@';
      else bk_cArray[m_nRow0][m_nCol0] = '+';

      m_nRow2 = m_nRow0;
      m_nCol2 = m_nCol0;
    }
  }

  public boolean isSolved() {
    return (LevelData.m_Sets[13] == 0 || m_iStep[2] <= 0)
        && m_nGoals_OK == m_nGoals
        && m_iStep[0] > 0;
  }

  public boolean isSolvedBackward() {
    return (LevelData.m_Sets[13] == 0 || m_iStep[0] <= 0)
        && m_nGoals_OK_2 == m_nGoals_2
        && m_iStep[2] > 0
        && mPathfinder.manTo2(true, bk_cArray, -1, -1, m_nRow2, m_nCol2, m_nRow3, m_nCol3);
  }

  /** 正逆推是否相合；{@code backwardView} 表示当前正在显示逆推棋盘。 */
  public boolean meet(boolean backwardView) {
    if (m_iStep[2] < 1 || m_bYanshi || m_bYanshi2) return false;

    boolean ok = true;
    for (int r = 0; r < m_cArray.length; r++) {
      for (int c = 0; c < m_cArray[0].length; c++) {
        if ((m_cArray[r][c] == '$' || m_cArray[r][c] == '*')
            && bk_cArray[r][c] != '$'
            && bk_cArray[r][c] != '*') {
          return false;
        }
      }
    }
    if (m_nRow2 != m_nRow || m_nCol2 != m_nCol) {
      if (backwardView) {
        ok = mPathfinder.manTo2(true, bk_cArray, -1, -1, m_nRow2, m_nCol2, m_nRow, m_nCol);
      } else {
        ok = mPathfinder.manTo2(false, m_cArray, -1, -1, m_nRow, m_nCol, m_nRow2, m_nCol2);
      }
    }
    if (ok) {
      m_nStep = 0;
      LevelData.m_Sets[13] = 0;
    }
    return ok;
  }

  private boolean isVisited(byte[][] marks, int r, int c) {
    try {
      return marks[r][c] > 0;
    } catch (Throwable ex) {
      return true;
    }
  }

  private boolean isWallBox(char[][] arr, int r, int c) {
    try {
      if (arr[r][c] == '#' || arr[r][c] == '_' || arr[r][c] == '$' || arr[r][c] == '*') {
        return true;
      }
    } catch (Throwable ex) {
      return true;
    }
    return false;
  }

  private boolean isWall(char[][] arr, int r, int c) {
    try {
      if (arr[r][c] == '#' || arr[r][c] == '_') return true;
    } catch (Throwable ex) {
      return true;
    }
    return false;
  }

  private boolean isBox(char[][] arr, int r, int c) {
    try {
      if (arr[r][c] == '$') return true;
    } catch (Throwable ex) {
    }
    return false;
  }

  private boolean isBoxGoal(char[][] arr, int r, int c) {
    try {
      if (arr[r][c] == '*') return true;
    } catch (Throwable ex) {
    }
    return false;
  }

  private boolean isFloor2(char[][] arr, int r, int c) {
    try {
      if (arr[r][c] == '-' || arr[r][c] == '.' || arr[r][c] == '@' || arr[r][c] == '+') {
        return true;
      }
    } catch (Throwable ex) {
    }
    return false;
  }

  private boolean isFloor1(char[][] arr, int r, int c) {
    try {
      if (arr[r][c] == '-' || arr[r][c] == '@') return true;
    } catch (Throwable ex) {
    }
    return false;
  }

  public boolean isLockCount(char[][] arr, short[][] mark, int r, int c, int row, int col) {
    for (int x = 0; x < m_cArray.length; x++) {
      for (int y = 0; y < m_cArray[0].length; y++) {
        mPathfinder.mark1[x][y] = false;
      }
    }

    mArray9[r][c] = '$';
    mPF.boxReachable(true, r, c, row, col);
    mArray9[r][c] = '-';

    boolean allOk = true;
    int n = 0, n2 = 0;
    int r1, c1;
    int p = 0, tail = 0;
    mPathfinder.pt[0] = row << 16 | col;
    mPathfinder.mark1[mPathfinder.pt[0] >>> 16][mPathfinder.pt[0] & 0x0000ffff] = true;
    for (; p <= tail; ) {
      for (int k = 0; 4 > k; k++) {
        try {
          r1 = (mPathfinder.pt[p] >>> 16) + DR4[k];
          c1 = (mPathfinder.pt[p] & 0x0000ffff) + DC4[k];
          if (!mPF.mark4[r1][c1]) continue;
          if (mark[r][c] == mark[r1][c1] && !mPathfinder.mark1[r1][c1]) {
            tail++;
            mPathfinder.pt[tail] = r1 << 16 | c1;
            mPathfinder.mark1[r1][c1] = true;

            if (!mark15[r1][c1] && (arr[r1][c1] == '$' || arr[r1][c1] == '*')) n++;
            if (mark15[r1][c1] && arr[r1][c1] == '.') n--;
            if (allOk && arr[r1][c1] == '$') allOk = false;
          }
          if (mark15[r1][c1] && !mPathfinder.mark1[r1][c1] && arr[r1][c1] == '.') n2++;
        } catch (Throwable ex) {
        }
      }
      p++;
    }

    if (allOk) return false;
    return (n > mark[r][c] + n2);
  }

  public boolean isLockGoal(char[][] arr, int boxRow, int boxCol, int row, int col) {
    mArray9[boxRow][boxCol] = '$';
    mPF.boxReachable(false, boxRow, boxCol, row, col);

    int n1 = 0, n2 = 0;
    for (int r = 0; r < m_cArray.length; r++) {
      for (int c = 0; c < m_cArray[0].length; c++) {
        if (mPF.mark3[r][c]) {
          if (arr[r][c] == '$') n1++;
          if (arr[r][c] == '.' || arr[r][c] == '+') n2++;
        }
      }
    }
    mArray9[boxRow][boxCol] = '-';
    return n1 > n2;
  }

  public boolean isLockNet2(char[][] arr, int newBoxRow, int newBoxCol) {
    int rows = arr.length, cols = arr[0].length;

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        mark41[r][c] = 0;
      }
    }

    Queue<Integer> queue = new LinkedList<Integer>();
    int pos, r = newBoxRow, c = newBoxCol, r1, c1, r2, c2;

    queue.offer(r << 16 | c);
    mark41[r][c] = 1;
    boolean flg = false;

    while (!queue.isEmpty()) {
      pos = queue.poll();
      r = pos >>> 16;
      c = pos & 0x0000ffff;

      flg |= (!isBoxGoal(arr, r, c));

      for (int k = 0; k < 4; k++) {
        r1 = r + DR4[k];
        c1 = c + DC4[k];
        r2 = r + DR4[k] * 2;
        c2 = c + DC4[k] * 2;

        if (isVisited(mark41, r2, c2) || isWall(arr, r2, c2) || isWall(arr, r1, c1)) {
          continue;
        } else if (isFloor2(arr, r2, c2)) {
          return false;
        } else {
          queue.offer(r2 << 16 | c2);
          mark41[r2][c2] = 1;
        }
      }
    }
    return flg;
  }

  /** 计算网点、网口标志。 */
  public void netInfo(char[][] arr, int boxRow, int boxCol) {
    int rows = arr.length, cols = arr[0].length;
    int pos, r = boxRow, c = boxCol, r1, c1, r2, c2;

    for (int x = 0; x < rows; x++) {
      for (int y = 0; y < cols; y++) {
        mark41[x][y] = 0;
      }
    }

    Queue<Integer> queue = new LinkedList<Integer>();
    queue.offer(r << 16 | c);
    mark41[r][c] = 1;

    while (!queue.isEmpty()) {
      pos = queue.poll();
      r = pos >>> 16;
      c = pos & 0x0000ffff;

      for (int k = 0; k < 4; k++) {
        r1 = r + DR4[k];
        c1 = c + DC4[k];
        r2 = r + DR4[k] * 2;
        c2 = c + DC4[k] * 2;

        if (isVisited(mark41, r2, c2) || isWall(arr, r2, c2) || isWall(arr, r1, c1)) {
          continue;
        } else if (isFloor1(arr, r2, c2) || isBox(arr, r2, c2)) {
          mark41[r2][c2] = 2;
        } else {
          queue.offer(r2 << 16 | c2);
          mark41[r2][c2] = 1;
        }
      }
    }
  }

  public int getStep(LinkedList<Byte> moves) {
    if (m_nGoals == 1) return moves.size();
    int len = moves.size();
    int[] boxRc = {1000, 1000};
    int r = 0, c = 0;
    byte dir;
    int n = 0, k = 0;
    boolean flg = false;
    Iterator<Byte> descItr = moves.descendingIterator();
    while (descItr.hasNext()) {
      dir = descItr.next();
      k++;
      switch (dir) {
        case 1:
          c--;
          break;
        case 2:
          r--;
          break;
        case 3:
          c++;
          break;
        case 4:
          r++;
          break;
        case 5:
          c--;
          if (boxRc[0] != r || boxRc[1] != c) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r;
          boxRc[1] = c - 1;
          break;
        case 6:
          r--;
          if (boxRc[0] != r || boxRc[1] != c) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r - 1;
          boxRc[1] = c;
          break;
        case 7:
          c++;
          if (boxRc[0] != r || boxRc[1] != c) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r;
          boxRc[1] = c + 1;
          break;
        case 8:
          r++;
          if (boxRc[0] != r || boxRc[1] != c) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r + 1;
          boxRc[1] = c;
          break;
        default:
          break;
      }
    }
    if (flg) return n;
    return len;
  }

  public int getStep2(LinkedList<Byte> moves) {
    if (m_nGoals == 1) return moves.size();
    int len = moves.size();
    int[] boxRc = {1000, 1000};
    int r = 0, c = 0;
    byte dir;
    int n = 0, k = 0;
    boolean flg = false;
    Iterator<Byte> descItr = moves.descendingIterator();
    while (descItr.hasNext()) {
      dir = descItr.next();
      k++;
      switch (dir) {
        case 1:
          c--;
          break;
        case 2:
          r--;
          break;
        case 3:
          c++;
          break;
        case 4:
          r++;
          break;
        case 5:
          if (boxRc[0] != r || boxRc[1] != c + 1) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r;
          boxRc[1] = c;
          c--;
          break;
        case 6:
          if (boxRc[0] != r + 1 || boxRc[1] != c) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r;
          boxRc[1] = c;
          r--;
          break;
        case 7:
          if (boxRc[0] != r || boxRc[1] != c - 1) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r;
          boxRc[1] = c;
          c++;
          break;
        case 8:
          if (boxRc[0] != r - 1 || boxRc[1] != c) {
            if (flg) return n;
            flg = true;
          }
          n = k;
          boxRc[0] = r;
          boxRc[1] = c;
          r++;
          break;
        default:
          break;
      }
    }
    if (flg) return n;
    return len;
  }
}
