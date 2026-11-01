package my.boxman.jsoko;

/** 死锁检查所需的只读棋盘状态。 */
public interface LockBoard {

  char[][] cells();

  byte[][] freezeMarks();

  int playerRow();

  int playerCol();
}
