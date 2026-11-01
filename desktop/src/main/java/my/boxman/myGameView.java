package my.boxman;

import my.boxman.compat.ResourceLoader;
import my.boxman.jsoko.DiagonalLock;
import my.boxman.jsoko.FreezeLock;
import my.boxman.jsoko.IntStack;
import my.boxman.jsoko.LockBoard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import my.boxman.compat.UiWindow;
import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.HoloPopupMenu;

public class myGameView extends JFrame {

    /**
     * 底栏按钮 = 原版 {@code res/values/style.xml} 的 {@code tab_style}。
     *
     * <p>{@code main_bottom} 与 {@code myEditView} 的 {@code edit_bottom} 用的是同一份样式，
     * 实现已提到 {@link my.boxman.compat.HoloTabBar.TabButton}；这里只留一个名字。
     */
    public static class GameButton extends my.boxman.compat.HoloTabBar.TabButton {
        public GameButton(String text) {
            super(text);
        }

        public GameButton(String text, Icon icon) {
            super(text, icon);
        }
    }

    // ⚠️ 这两个字段**跨线程**：写入在 EDT（onImport / done() / myStop()），
    // 读取在 SwingWorker 自己的线程（AsyncCountBoxsTask 的 isCancelled 判断、
    // 用例里等 done() 的轮询循环）。不加 volatile 就没有 happens-before ——
    // 轮询方可能一直读到旧值，也可能在 done() 之后仍然读到非 null，
    // 从而看到「任务已完成」但**看不到任务对模型的改动**（典型症状：
    // Phase19 的「导入动作任务应完成」/「应推动仓管员向右移动一格」间歇性失败）。
    volatile AsyncCountBoxsTask mTask;
    public volatile RunMicroTask mMicroTask;

    /** 测试缝：把「弹模态框」换成「只记不弹」（见 TEST_NOTES 的 dialogShower 一节） */
    java.util.function.Consumer<JDialog> dialogShower = dlg -> dlg.setVisible(true);

    /**
     * 「死锁移动」提示框 —— 原版 {@code myGameView.java:1315-1325} 的 {@code dlg4 / lockDlg}。
     *
     * <p>原版是「在挂 {@code bt_UnDo} 监听之前建好一个，之后只用 {@code setMessage()} +
     * {@code show()}」，而 {@code show()} 不阻塞。PC 端原先改成了阻塞式
     * {@code JOptionPane.showConfirmDialog}，见 {@link #showLockDlg()} 的注释。
     */
    public HoloAlertDialog lockDlg = null;

    /**
     * 「恭喜过关！」提示框 —— 原版 {@code myGameView.java:1243-1268} 的 {@code dlg / AotoNextDlg}。
     *
     * <p>⚠️ <b>它在 {@code UpData1()} 里被弹出来，而 {@code UpData1()} 是 1ms 动画定时器的回调</b>
     * （原版 {@code myGameView.java:345 / 385}）。PC 端原先写成阻塞式
     * {@code JOptionPane.showConfirmDialog} —— 那就是「点『否』直接卡死」的元凶：
     * 模态框在 EDT 上开嵌套事件循环，1ms 定时器在嵌套循环里照旧触发 → {@code UpData1()}
     * 又走早退分支 → 又 {@code showConfirmDialog} → 再嵌一层，栈无限加深。
     * 原版是「建好一个 + {@code show()} 不阻塞」，照搬。
     */
    public HoloAlertDialog AotoNextDlg = null;

    /** 「退出」提示框 —— 原版 {@code myGameView.java:1270-1279} 的 {@code dlg0 / exitDlg}。 */
    public HoloAlertDialog exitDlg = null;

    /** 「更换关卡（上一关）」提示框 —— 原版 {@code myGameView.java:1282-1296} 的 {@code dlg1 / exitDlg2}。 */
    public HoloAlertDialog exitDlg2 = null;

    /** 「更换关卡（下一关）」提示框 —— 原版 {@code myGameView.java:1299-1313} 的 {@code dlg2 / exitDlg3}。 */
    public HoloAlertDialog exitDlg3 = null;

    javax.swing.Timer mClockTimer;  // 背景时间定时器
    javax.swing.Timer myTimer1;
    javax.swing.Timer myTimer2;
    javax.swing.Timer myTimer3;
    javax.swing.Timer myTimer4;

    public myGameViewMap mMap;  // 地图

    // 底行按钮
    public GameButton bt_UnDo = null;
    public GameButton bt_ReDo = null;
    public GameButton bt_More = null;
    public GameButton bt_IM = null;
    public GameButton bt_Sel = null;
    public GameButton bt_TR = null;
    public GameButton bt_BK = null;

    JPanel main_bottom; // 底部栏面板

    /** 「更多」按钮弹出的选项菜单（原版 {@code res/menu/player.xml}），见 {@link #openOptionsMenu()}。 */
    private JPopupMenu optionsMenu;

    // 动画中的箱子与方向
    public int oldDir;
    public final int m_iSleep[] = {50, 17, 10, 5, 2}; // 移动速度
    public final String m_sSleep[] = {"最快", "较快", "中速", "较慢", "最慢"}; // 移动速度

    public final GameState state = new GameState();
    public String m_imPort_YASS;  // 是否做过动作“导入”或“YASS”过动作

    // 四邻：左、右、上、下
    final byte[] dr4 = {0, 0, -1, 1};
    final byte[] dc4 = {-1, 1, 0, 0};

    public int m_nItemSelect;  // 对话框中的出item选择前的记忆

    int MyMINUTE;   // 记忆时钟的分钟数，用于更新背景时间
    int m_nMacro_Row, m_nMacro_Col;  // “宏”功能中，用于记忆仓管员坐标

    public myGameView() {
        setTitle("推箱快手");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }

            /**
             * 原版 {@code myGameView.onStart()}：从「关卡状态与答案」窗口回来时，
             * 如果那边置了 {@code m_StateIsRedy}，就把选中的状态/答案载入进来。
             * PC 端用「窗口重新获得焦点」等价替代 Activity 的 onStart()。
             */
            @Override
            public void windowActivated(WindowEvent e) {
                if (myMaps.m_StateIsRedy) {
                    OpenState();  //处理打开的状态
                }
            }
        });

        initUI();

        // ⚠️ 必须在 UI 装好之后、initGame() 之前调：initGame() 里按地图控件的实际尺寸
        // 装载关卡，窗口没 pack 过的话地图尺寸为 0，舞台区就画不出来。
        UiWindow.applyPhoneSize(this);

        initTimers();
        initGame();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        mMap = new myGameViewMap();
        mMap.Init(this);
        add(mMap, BorderLayout.CENTER);

        main_bottom = createBottomBar();
        add(main_bottom, BorderLayout.SOUTH);

        // 原版 myGameView 是 FEATURE_NO_TITLE + FLAG_FULLSCREEN：既没有 ActionBar 也没有菜单栏，
        // 全部菜单项都在底栏「更多」按钮弹出的选项菜单里（对应 res/menu/player.xml，见 openOptionsMenu()）。
        //
        // ⚠️ 这里曾经还挂过一份 PC 自造的「地图右键菜单」（installMapPopupMenu，导航/操作/视图/工具/帮助
        // 五组）。原版 myGameView / myGameViewMap 里没有任何 registerForContextMenu /
        // onCreateContextMenu / setOnLongClickListener，地图上没有右键菜单 —— 已整体删除（阶段 G ④）。
        // 原先靠它兜底的入口现在都有原版路径了：
        //   关卡编辑器   → myGridView 上下文菜单「改编为新关卡 / 编辑」
        //   相似关卡对比 → myGridView 上下文菜单「查找相似关卡」→ FindDialog → myFindView
        //   图像识别     → BoxMan 菜单「图像识别」→ myPicListView → myRecogView
        //   动作管理     → player_IN「导入...」
        //   关卡状态     → player_load「打开状态...」
        //   背景颜色/标尺/导出/GIF/重新开始/帮助/关于 → 「设置...」/「开关选项...」/player.xml 各项
    }

    private Icon getScaledIcon(String resName) {
        return my.boxman.compat.HoloTabBar.icon(resName);
    }

    private JPanel createBottomBar() {
        JPanel bar = my.boxman.compat.HoloTabBar.bar();

        bt_UnDo = new GameButton("后退", getScaledIcon("undobtn"));
        bt_ReDo = new GameButton("前进", getScaledIcon("redobtn"));
        bt_IM   = new GameButton("瞬移", getScaledIcon("imbtn"));
        bt_BK   = new GameButton("逆推", getScaledIcon("bkbtn"));
        bt_Sel  = new GameButton("计数", getScaledIcon("selbtn"));
        bt_TR   = new GameButton("转置", getScaledIcon("trbtn"));
        bt_More = new GameButton("更多", getScaledIcon("chbtn"));

        setupButtonEvents();

        bar.add(bt_UnDo);
        bar.add(bt_ReDo);
        bar.add(bt_IM);
        bar.add(bt_BK);
        bar.add(bt_Sel);
        bar.add(bt_TR);
        bar.add(bt_More);

        return bar;
    }

    private void attachLongPress(GameButton btn, Runnable longPressAction) {
        btn.setOnLongClickListener(e -> longPressAction.run());

        final javax.swing.Timer timer = new javax.swing.Timer(500, null);
        timer.setRepeats(false);
        timer.addActionListener(ev -> {
            btn.getModel().setArmed(false);
            longPressAction.run();
        });

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    longPressAction.run();
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    timer.start();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (timer.isRunning()) {
                    timer.stop();
                }
            }
        });
    }

    private void setupButtonEvents() {
        // 原版 onCreate 里一次性建好 5 个提示框（myGameView.java:1243-1325），顺序是
        // dlg(AotoNextDlg) → dlg0(exitDlg) → dlg1(exitDlg2) → dlg2(exitDlg3) → dlg4(lockDlg)。
        // 全部 setCancelable(false)，之后只用 show() —— 而 show() 不阻塞。
        //
        // ⚠️ 这 4 个（尤其 AotoNextDlg）**不能**再用阻塞式 JOptionPane：
        //   · AotoNextDlg 在 UpData1() 里弹，而 UpData1() 是 myTimer1（1ms）的回调；
        //   · exitDlg / exitDlg2 / exitDlg3 由 DoEvent() 的键盘快捷键触发，
        //     也完全可能在动画循环还在跑的时候按下。
        // 模态框会在 EDT 上开嵌套事件循环，而那个 1ms 定时器在嵌套循环里照旧触发 →
        // 同一个提示框被一层层叠着弹出来，栈无限加深，表现就是「点了按钮直接卡死」。

        // 「恭喜过关！」——原版 dlg（myGameView.java:1243-1268）
        AotoNextDlg = HoloAlertDialog.createNonModal(this, "恭喜过关！");
        AotoNextDlg.setMessage("是否自动打开下一个未解关卡？");
        AotoNextDlg.addButton("否", null);
        JButton btNextLevel = AotoNextDlg.addButton("是", () -> {
            int k = myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1;
            int len = myMaps.m_lstMaps.size();
            while (k < len && myMaps.m_lstMaps.get(k).Solved) {
                k++;
            }
            if (k < len) {
                myMaps.curMap = myMaps.m_lstMaps.get(k);
                myMaps.m_nTrun = myMaps.curMap.Trun;
                bt_BK.setChecked(false);
                bt_TR.setChecked(false);
                bt_TR.setText(myMaps.m_nTrun + " 转");
                initMap();
                state.ls_bk_cArray = state.bk_cArray;
            } else {
                MyToast.showToast(this, "后面没有未解关卡！", MyToast.LENGTH_SHORT);
            }
        });
        AotoNextDlg.setDefaultButton(btNextLevel);

        // 「退出」——原版 dlg0（myGameView.java:1270-1279）
        exitDlg = HoloAlertDialog.createNonModal(this, "退出");
        exitDlg.setMessage("有状态未保存，坚持退出吗？");
        exitDlg.addButton("否", null);
        JButton btExitYes = exitDlg.addButton("是", () -> {
            myStop();
            dispose();
        });
        exitDlg.setDefaultButton(btExitYes);

        // 「更换关卡（上一关）」——原版 dlg1（myGameView.java:1282-1296）
        exitDlg2 = HoloAlertDialog.createNonModal(this, "更换关卡");
        exitDlg2.setMessage("有状态未保存，坚持更换吗？");
        exitDlg2.addButton("否", null);
        JButton btPreYes = exitDlg2.addButton("是", () -> {
            int n1 = myMaps.m_lstMaps.indexOf(myMaps.curMap);
            if (n1 > 0) {
                myPre(n1 - 1);
            } else {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
            }
        });
        exitDlg2.setDefaultButton(btPreYes);

        // 「更换关卡（下一关）」——原版 dlg2（myGameView.java:1299-1313）
        exitDlg3 = HoloAlertDialog.createNonModal(this, "更换关卡");
        exitDlg3.setMessage("有状态未保存，坚持更换吗？");
        exitDlg3.addButton("否", null);
        JButton btNextYes = exitDlg3.addButton("是", () -> {
            int n2 = myMaps.m_lstMaps.indexOf(myMaps.curMap);
            if (n2 >= 0 && n2 + 1 < myMaps.m_lstMaps.size()) {
                myNext(n2 + 1);
            } else {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
            }
        });
        exitDlg3.setDefaultButton(btNextYes);

        // 「死锁移动」提示框：原版在挂 bt_UnDo 监听之前就把它建好（myGameView.java:1315-1325）。
        // 原版：setTitle("死锁移动").setMessage(...).setCancelable(false)
        //       .setNegativeButton("继续", null).setPositiveButton("撤销移动", → bt_UnDo 取反)
        // ⚠️ 必须是**非模态**：它是在 1ms 动画定时器里弹的，模态框会在 EDT 上开嵌套事件循环，
        // 定时器照旧触发、动画继续推进、再次满足死锁条件、再弹一层，层层嵌套直到卡死。
        lockDlg = HoloAlertDialog.createNonModal(this, "死锁移动");
        lockDlg.addButton("继续", null);
        JButton btUndoMove = lockDlg.addButton("撤销移动", () -> {
            // 原版 AlertDialog 的按钮先 dismiss 再走监听器，这里保持同样顺序
            lockDlg.dispose();
            bt_UnDo.setChecked(!bt_UnDo.isChecked());
        });
        // 原版 AlertDialog 会把 positive 按钮（button1，最右）设为默认焦点按钮
        lockDlg.setDefaultButton(btUndoMove);

        // 后退
        bt_UnDo.addItemListener(e -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            state.m_bNetLock = false;
            state.m_nStep = 0;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            state.m_bACT_ERROR = false;
            mMap.invalidate();
            if (state.m_bBusing) return;

            if (bt_BK.isChecked()) {
                if (state.m_lstMovUnDo2.isEmpty()) {
                    MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                } else {
                    if (myMaps.m_Sets[23] == 1) state.m_nStep = 1;
                    else state.m_nStep = state.getStep(state.m_lstMovUnDo2);
                    UpData4(1);
                }
            } else {
                if (myMaps.isMacroDebug) {
                    levelReset(false);
                    if (myMaps.m_ActionIsPos) {
                        Iterator<Byte> myItr = state.m_lstMovedHistory.iterator();
                        state.m_lstMovReDo.clear();
                        state.m_lstMovUnDo.clear();
                        while (myItr.hasNext()) {
                            state.m_lstMovReDo.offer(myItr.next());
                            state.m_nStep = 1;
                            reDo1();
                        }
                        mMap.invalidate();
                    }
                    mMap.curMoves = 0;

                    m_nMacro_Row = state.m_nRow;
                    m_nMacro_Col = state.m_nCol;
                    int len = mMap.myMacro.size();
                    if (len > 1) {
                        mMap.myMacro.remove(len - 1);
                        for (int k = 0; k < len - 2; k++) {
                            myDo_Block(mMap.myMacro.get(k), mMap.myMacro.get(k), true, false);
                        }
                        state.m_lstMovReDo.clear();
                        mMap.invalidate();
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                } else {
                    if (state.m_lstMovUnDo.isEmpty()) {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    } else {
                        if (myMaps.m_Sets[23] == 1) state.m_nStep = 1;
                        else {
                            if (state.m_nLastSteps >= 0 && state.m_nLastSteps <= state.m_lstMovUnDo.size()) state.m_nStep = state.m_lstMovUnDo.size() - state.m_nLastSteps;
                            else state.m_nStep = state.getStep2(state.m_lstMovUnDo);
                        }
                        state.m_nLastSteps = -1;
                        UpData2(1);
                    }
                }
            }
            state.m_bBusing = false;
        });

        attachLongPress(bt_UnDo, () -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            state.m_bNetLock = false;
            state.m_nStep = 0;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            state.m_bACT_ERROR = false;
            mMap.d_Moves = mMap.m_PicWidth;
            mMap.invalidate();

            if (bt_BK.isChecked()) {
                MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
                levelReset(true);
            } else {
                MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
                levelReset(false);

                if (myMaps.m_ActionIsPos && myMaps.isMacroDebug) {
                    Iterator<Byte> myItr = state.m_lstMovedHistory.iterator();
                    state.m_lstMovReDo.clear();
                    state.m_lstMovUnDo.clear();
                    while (myItr.hasNext()) {
                        state.m_lstMovReDo.offer(myItr.next());
                        state.m_nStep = 1;
                        reDo1();
                    }
                }
                mMap.myMacro.clear();
                mMap.myMacro.add(0);
                mMap.myMacroInf = "";
            }
            mMap.curMoves = 0;
            state.m_bBusing = false;
            mMap.invalidate();
        });

        // 前进
        bt_ReDo.addItemListener(e -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            state.m_bNetLock = false;
            state.m_nStep = 0;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            state.m_bACT_ERROR = false;
            mMap.invalidate();
            if (state.m_bBusing) return;

            if (bt_BK.isChecked()) {
                if (state.m_lstMovReDo2.isEmpty()) {
                    MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                } else {
                    if (myMaps.m_Sets[23] == 1) state.m_nStep = 1;
                    else state.m_nStep = state.getStep2(state.m_lstMovReDo2);
                    UpData3(1);
                }
            } else {
                if (myMaps.isMacroDebug) {
                    if (mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                        myDo_Block(mMap.myMacro.get(mMap.myMacro.size() - 1), mMap.myMacro.get(mMap.myMacro.size() - 1), false, false);
                        state.m_lstMovReDo.clear();
                        mMap.invalidate();
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                } else {
                    if (state.m_lstMovReDo.isEmpty()) {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    } else {
                        if (myMaps.m_Sets[23] == 1) state.m_nStep = 1;
                        else state.m_nStep = state.getStep(state.m_lstMovReDo);
                        mMap.Box_Row0 = -1;
                        state.m_nLastSteps = state.m_lstMovUnDo.size();
                        UpData1(1);
                    }
                }
            }
            state.m_bBusing = false;
        });

        attachLongPress(bt_ReDo, () -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            state.m_bNetLock = false;
            state.m_nStep = 0;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            state.m_bACT_ERROR = false;
            mMap.invalidate();

            if (bt_BK.isChecked()) {
                if (state.m_lstMovReDo2.isEmpty()) {
                    MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                } else {
                    MyToast.showToast(this, "进至尾！", MyToast.LENGTH_SHORT);
                    if (state.m_lstMovUnDo2.isEmpty()) goHome();
                    state.m_nStep = state.m_lstMovReDo2.size();
                    state.m_bYanshi = false;
                    state.m_bYanshi2 = true;
                    mMap.curMoves = 0;
                    UpData3(1);
                }
            } else {
                if (myMaps.isMacroDebug) {
                    if (mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                        myDo_Block(mMap.myMacro.get(mMap.myMacro.size() - 1), myMaps.sAction.length - 1, false, false);
                        state.m_lstMovReDo.clear();
                        mMap.invalidate();
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                } else {
                    if (state.m_lstMovReDo.isEmpty()) {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    } else {
                        MyToast.showToast(this, "正向演示！", MyToast.LENGTH_SHORT);
                        state.m_nStep = state.m_lstMovReDo.size();
                        mMap.Box_Row0 = -1;
                        state.m_bYanshi = true;
                        state.m_bYanshi2 = false;
                        mMap.curMoves = 0;
                        state.m_nLastSteps = -1;
                        UpData1(1);
                    }
                }
            }
            state.m_bBusing = false;
        });

        // 瞬移
        // 原版 myGameView.java:1551-1554：先按上次的开关状态 setChecked，再显式刷一次底色
        //（setChecked 在挂监听器之前调，不会触发回调）。
        bt_IM.setChecked(myMaps.m_Sets[6] == 1);
        bt_IM.setCheckedBackground(myMaps.m_Sets[6] == 1);
        bt_IM.addItemListener(e -> {
            mMap.d_Moves = mMap.m_PicWidth;
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            if (bt_IM.isChecked()) {
                bt_IM.setCheckedBackground(true);    // 原版：setBackgroundColor(0xff445566)
                myMaps.m_Sets[6] = 1;
            } else {
                bt_IM.setCheckedBackground(false);   // 原版：setBackgroundColor(0xff778899)
                myMaps.m_Sets[6] = 0;
            }
        });

        // 逆推
        bt_BK.setChecked(false);
        bt_BK.addItemListener(e -> {
            StopMicro();
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            myMaps.isRecording = false;
            mMap.d_Moves = mMap.m_PicWidth;
            state.m_bBusing = false;
            state.m_nStep = 0;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            state.m_bACT_ERROR = false;
            if (bt_BK.isChecked()) {
                bt_BK.setCheckedBackground(true);    // 原版：setBackgroundColor(0xff445566)
                if (state.m_nRow2 < 0 || state.m_nCol2 < 0) {
                    mMap.m_iR = state.m_nRow2;
                    mMap.m_iC = state.m_nCol2;
                    MyToast.showToast(this, "需要给出仓管员的位置！", MyToast.LENGTH_SHORT);
                }
            } else {
                bt_BK.setCheckedBackground(false);   // 原版：setBackgroundColor(0xff778899)
                mMap.m_iR = state.m_nRow;
                mMap.m_iC = state.m_nCol;
            }
            state.m_bNetLock = false;
            mMap.invalidate();
        });

        // 计数
        bt_Sel.setChecked(false);
        bt_Sel.addItemListener(e -> {
            StopMicro();
            mMap.d_Moves = mMap.m_PicWidth;
            state.m_nStep = 0;
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            state.m_bBusing = false;
            mMap.m_lGoto = false;
            mMap.m_lGoto2 = false;
            mMap.m_lParityBrightnessShade = false;

            mMap.m_Count[0] = 0;
            mMap.m_Count[1] = 0;
            mMap.m_Count[2] = 0;
            mMap.m_Count[3] = 0;
            mMap.m_Count[4] = 0;
            mMap.m_Count[5] = 0;
            if (state.m_cArray != null) {
                for (int i = 0; i < state.m_cArray.length; i++) {
                    for (int j = 0; j < state.m_cArray[0].length; j++) {
                        state.m_selArray[i][j] = 0;
                        state.bk_selArray[i][j] = 0;
                    }
                }
            }

            if (bt_Sel.isChecked()) {
                bt_Sel.setCheckedBackground(true);   // 原版：setBackgroundColor(0xff445566)
                mMap.m_boxCanMove = false;
                mMap.m_boxNoMoved = false;
                mMap.m_boxCanMove2 = false;
                mMap.m_boxNoUsed = false;
            } else {
                bt_Sel.setCheckedBackground(false);  // 原版：setBackgroundColor(0xff778899)
            }
            state.m_bNetLock = false;
            mMap.m_lChangeBK = false;
            mMap.invalidate();
        });

        // 转置
        bt_TR.setChecked(false);
        bt_TR.setText(myMaps.m_nTrun + " 转");
        bt_TR.addItemListener(e -> {
            StopMicro();
            myMaps.m_nTrun = (myMaps.m_nTrun + 1) % 8;
            if (myMaps.curMap != null) myMaps.curMap.Trun = myMaps.m_nTrun;
            bt_TR.setText(myMaps.m_nTrun + " 转");
            mMap.initArena();
            mMap.invalidate();
            if (myMaps.m_nTrun == 0) {
                MyToast.showToast(this, "第 0 转", MyToast.LENGTH_SHORT);
            }
        });
        attachLongPress(bt_TR, () -> {
            StopMicro();
            myMaps.m_nTrun = 0;
            if (myMaps.curMap != null) myMaps.curMap.Trun = myMaps.m_nTrun;
            bt_TR.setText(myMaps.m_nTrun + " 转");
            mMap.initArena();
            mMap.invalidate();
            MyToast.showToast(this, "第 0 转", MyToast.LENGTH_SHORT);
        });

        // 更多
        bt_More.addActionListener(e -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            state.m_bNetLock = false;
            mMap.m_lChangeBK = false;
            state.m_bACT_ERROR = false;
            mMap.invalidate();
            mMap.d_Moves = mMap.m_PicWidth;
            state.m_nStep = 0;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            state.m_bBusing = false;

            openOptionsMenu();
        });
        attachLongPress(bt_More, () -> {
            MyToast.showToast(this, "可以离开了！", MyToast.LENGTH_SHORT);
            handleExit();
        });
    }

    private void handleExit() {
        StopMicro();
        bt_Sel.setChecked(false);
        if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
        state.m_bNetLock = false;
        mMap.m_lChangeBK = false;
        state.m_bACT_ERROR = false;
        mMap.d_Moves = mMap.m_PicWidth;
        state.m_nStep = 0;
        state.m_bYanshi = false;
        state.m_bYanshi2 = false;
        state.m_bBusing = false;
        mMap.invalidate();

        if (state.m_bMoved && ((state.m_lstMovUnDo != null && state.m_lstMovUnDo.size() > 0) || (state.m_lstMovUnDo2 != null && state.m_lstMovUnDo2.size() > 0))) {
            // 原版 exitDlg.show()（myGameView.java:1726 / 4176 / 5158）—— 非阻塞
            showDialog(exitDlg);
        } else {
            myStop();
            dispose();
        }
    }

    private void initTimers() {
        MyMINUTE = 0;
        mClockTimer = new javax.swing.Timer(3000, e -> {
            if (myMaps.m_Sets[25] == 1) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                if (MyMINUTE != cal.get(Calendar.MINUTE)) {
                    MyMINUTE = cal.get(Calendar.MINUTE);
                    if (mMap != null) mMap.repaint();
                }
            }
        });
        mClockTimer.start();

        myTimer1 = new javax.swing.Timer(1, e -> UpData1(1));
        myTimer1.setRepeats(false);

        myTimer2 = new javax.swing.Timer(1, e -> UpData2(1));
        myTimer2.setRepeats(false);

        myTimer3 = new javax.swing.Timer(1, e -> UpData3(1));
        myTimer3.setRepeats(false);

        myTimer4 = new javax.swing.Timer(1, e -> UpData4(1));
        myTimer4.setRepeats(false);

        // ⚠️ 这 4 个一次性定时器**必须**关掉 coalesce（合并投递），否则动画循环会静默停摆。
        //
        // 原版是 {@code RefreshHandler1..4.sleep(ms)}（myGameView.java:168-171 等）：
        //     removeMessages(0);  sendMessageDelayed(obtainMessage(1), m);
        // Looper 对每个 message 都会投递一次，**不存在合并**，所以每一拍都必然到达。
        //
        // javax.swing.Timer 默认 coalesce == true，Timer.post() 是这样写的：
        //     if (notify.compareAndSet(false, true) || !coalesce) invokeLater(doPostEvent);
        // 而 notify 只在 DoPostEvent.run() **回调返回之后**才被 cancelEvent() 清掉。
        // 于是「在定时器自己的回调里重新排下一拍」（正是 sleepTimer 的用法）会踩中这个竞态：
        //   ① 回调执行中 → notify == true；
        //   ② sleepTimer 里 isRunning() 为 false（TimerQueue 线程在 post() 之前就已把
        //      delayedTimer 置空），所以不会走 stop() → notify 仍是 true；
        //   ③ TimerQueue 线程 1ms 后 post() → compareAndSet(false,true) **失败**，
        //      且 coalesce 为 true → **不投递** → 这一拍被丢掉；
        //   ④ 回调返回 → cancelEvent() → notify = false。
        //   结果：定时器既不在队列里、notify 也是 false，**再也不会响**。
        //
        // 若第 ③ 步发生在第 ④ 步之前（即 EDT 在回调里停留超过 1ms），就必然丢拍。
        // 弹「死锁移动」提示框正好会这样：showLockDlg() 紧跟在本拍 sleepTimer() 之后，
        // setVisible() 建窗/定尺寸要几十 ms，EDT 一直停在回调里 → 丢拍 → 循环停摆 →
        // state.m_bBusing 永远停在 true → bt_UnDo 监听器第 396 行 `if (state.m_bBusing) return;` 早退，
        // 表现就是用户报的「点击撤销不会撤销，直接卡住」。
        //
        // 关掉 coalesce 后 post() 走 `|| !coalesce` 分支，每次都投递 —— 与 sendMessageDelayed 等价。
        myTimer1.setCoalesce(false);
        myTimer2.setCoalesce(false);
        myTimer3.setCoalesce(false);
        myTimer4.setCoalesce(false);
    }

    /**
     * 排下一拍动画 —— 等价于原版 {@code RefreshHandler1..4.sleep(ms)}
     * （{@code myGameView.java:168-171}）：
     * <pre>removeMessages(0); sendMessageDelayed(obtainMessage(1), m);</pre>
     *
     * <p>{@code stop()} 对应 {@code removeMessages(0)}（撤掉尚未投递的那一拍），
     * {@code start()} 对应 {@code sendMessageDelayed()}（排新的一拍）。
     *
     * <p>⚠️ 调用方就是定时器自己的回调，所以 {@code isRunning()} 此时为 false 而跳过
     * {@code stop()} 是正常的 —— 但这也意味着 {@code start()} 必须能独立保证投递，
     * 这依赖 {@link #initTimers()} 里的 {@code setCoalesce(false)}，详见那里的注释。
     */
    private void sleepTimer(javax.swing.Timer timer, int ms) {
        // ⚠️ timer 可能是 null：myStop() 会把 myTimer1..4 一起置空，但**已经排进 EDT 队列的
        // 那一拍**照样会执行，于是走到这里就是 null。不挡的话就是一条未捕获的 EDT 异常
        // （实测原文："Cannot invoke \"javax.swing.Timer.isRunning()\" because \"timer\" is null"），
        // 既刷屏，又会把本拍之后的收尾逻辑整段跳过。
        // 语义上这正是「停表」：myStop() 之后本就不该再排下一拍。
        if (timer == null) return;
        if (timer.isRunning()) timer.stop();
        timer.setInitialDelay(Math.max(1, ms));
        timer.start();
    }

    private void initGame() {
        skinList();
        bkPicList();

        if (myMaps.curMap == null) {
            if (myMaps.m_lstMaps != null && !myMaps.m_lstMaps.isEmpty()) {
                myMaps.curMap = myMaps.m_lstMaps.get(0);
            }
        }

        if (myMaps.curMap != null) {
            myMaps.m_nTrun = myMaps.curMap.Trun;
        }
        myMaps.isHengping = false;
        state.m_iR9 = -1;
        state.m_iC9 = -1;
        state.m_iR10 = -1;
        state.m_iC10 = -1;

        initMap();
        state.m_bNetLock = false;

    }

    // 旋转方向计算
    private int getRotate(int mDir1, int mDir2) {
        int mRT = 0;
        if (state.b_nRow < 0 || (!bt_BK.isChecked() && myMaps.m_Sets[18] > 0)) {
            switch (mDir1) {
                case 1:
                    switch (mDir2) {
                        case 2: mRT = -2080; break;
                        case 3: mRT = -10170; break;
                        case 4: mRT = -1080; break;
                    }
                    break;
                case 2:
                    switch (mDir2) {
                        case 1: mRT = -1080; break;
                        case 3: mRT = -2080; break;
                        case 4: mRT = -10170; break;
                    }
                    break;
                case 3:
                    switch (mDir2) {
                        case 1: mRT = -10170; break;
                        case 2: mRT = -1080; break;
                        case 4: mRT = -2080; break;
                    }
                    break;
                case 4:
                    switch (mDir2) {
                        case 1: mRT = -2080; break;
                        case 2: mRT = -10170; break;
                        case 3: mRT = -1080; break;
                    }
                    break;
            }
        } else {
            switch (mDir1) {
                case 1:
                    switch (mDir2) {
                        case 4: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                        case 1: if (!bt_BK.isChecked()) mRT = -10170; break;
                        case 2: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                    }
                    break;
                case 2:
                    switch (mDir2) {
                        case 3: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                        case 1: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                        case 2: if (!bt_BK.isChecked()) mRT = -10170; break;
                    }
                    break;
                case 3:
                    switch (mDir2) {
                        case 3: if (!bt_BK.isChecked()) mRT = -10170; break;
                        case 4: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                        case 2: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                    }
                    break;
                case 4:
                    switch (mDir2) {
                        case 3: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                        case 4: if (!bt_BK.isChecked()) mRT = -10170; break;
                        case 1: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                    }
                    break;
            }
        }
        return mRT;
    }

    int[] YanshiSpeed = {0, 100, 200, 500, 1000};

    // redo -- 正推
    public void UpData1(int i) {
        if ((i != 1 || state.m_nStep <= 0 || state.m_lstMovReDo.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance()) {
                if (state.m_bMoved) {
                    if (myMaps.curMap != null && myMaps.curMap.Level_id > 0) {
                        saveAns(1);
                        // 原版 AotoNextDlg.show()：非阻塞、已显示就早退（myGameView.java:345 / 385）
                        showDialog(AotoNextDlg);
                    } else {
                        MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
                        saveAns(0);
                    }
                } else MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                if (state.m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            state.m_bBusing = false;
            state.m_bYanshi = false;
            state.m_bYanshi2 = false;
            return;
        }

        if (bt_IM.isChecked()) {
            if (state.m_bYanshi) {
                do {
                    reDo1();
                } while (state.m_nStep > 0 && (state.m_bPush && state.m_lstMovReDo.getLast() == state.m_Dir || !state.m_bPush && state.m_lstMovReDo.getLast() < 5));
                mMap.invalidate();

                if (state.m_nStep > 0) {
                    sleepTimer(myTimer1, YanshiSpeed[myMaps.m_Sets[10]]);
                    state.m_bPush = false;
                }
            } else {
                while (state.m_nStep > 0) reDo1();
                mMap.invalidate();
            }
            if (myClearance()) {
                state.m_bYanshi = false;
                if (state.m_bMoved) {
                    if (myMaps.curMap != null && myMaps.curMap.Level_id > 0) {
                        saveAns(1);
                        // 原版 AotoNextDlg.show()：非阻塞、已显示就早退（myGameView.java:345 / 385）
                        showDialog(AotoNextDlg);
                    } else {
                        MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
                        saveAns(0);
                    }
                } else MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                state.m_bYanshi = false;
                if (state.m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            state.m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                state.b_nRow = -1;
                state.b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                if (state.m_bYanshi && myMaps.m_Sets[28] == 1) {
                    do {
                        reDo1();
                    } while (state.m_nStep > 0 && !state.m_bPush);
                    state.m_bPush = false;
                } else {
                    reDo1();
                }
                myMaps.m_Sets[18] = 1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3) {
                    mMap.d_Moves = 0;
                } else {
                    if (!state.m_bACT_ERROR) {
                        mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
                    }
                }
            }
            mMap.invalidate();
            sleepTimer(myTimer1, 1);
        }

        // 正推死锁判断
        if (state.m_nStep == 0 && myMaps.m_Sets[11] == 1 && mMap.d_Moves >= mMap.m_PicWidth && myLock(state.m_iR9, state.m_iC9)) {
            showLockDlg();
        }
    }

    /**
     * 弹出「死锁移动」提示框 —— 原版 {@code myGameView.java:435 / 561} 的 {@code lockDlg.show()}。
     *
     * <p>⚠️ <b>不要</b>改回 {@code JOptionPane.showConfirmDialog}。这个提示是在
     * {@code myTimer1 / myTimer3}（1ms 一次性定时器）驱动的动画循环里弹的：模态框会在 EDT 上
     * 开一个嵌套事件循环，而那个定时器在嵌套循环里照旧触发 —— 动画继续跑完一步、又满足死锁
     * 条件、于是在上一层模态框<b>内部</b>再弹一层，栈无限加深，用户看到的就是「点『否』直接卡死」。
     *
     * <p>原版不会这样，因为 Android 的 {@code AlertDialog.show()} 不阻塞，而且
     * {@code Dialog.show()} 在已显示时会 {@code if (mShowing) return;} 早退 —— 所以动画每走
     * 一步都调一次 {@code show()} 也只是把同一个框保持在屏幕上。这里两条都照搬。
     *
     * <p>包内可见（而非 {@code private}）：Phase31 用例要直接驱动它来验证「已显示就不重复弹」。
     */
    void showLockDlg() {
        if (isLockDlgVisible()) return;
        showDialog(lockDlg);
    }

    /**
     * 弹出一个已经建好的提示框 —— 等价于原版 {@code Dialog.show()}。
     *
     * <p>⚠️ Android 的 {@code Dialog.show()} 在已经显示时会
     * {@code if (mShowing) { …; return; }} <b>直接早退</b>，所以动画循环每走一步都调一次
     * {@code show()} 也只是把同一个框保持在屏幕上，不会叠出第二个。这里照搬这个语义。
     *
     * <p>更要紧的是它<b>不阻塞</b>：{@code show()} 立刻返回，事件循环照常跑。PC 端早期用
     * 阻塞式 {@code JOptionPane} 顶替它，在 1ms 定时器的回调里会开嵌套事件循环，
     * 定时器在嵌套循环里照旧触发、又弹一层，栈无限加深 —— 见 {@link #AotoNextDlg}。
     */
    void showDialog(HoloAlertDialog dlg) {
        if (dlg == null || dlg.isVisible()) return;
        dialogShower.accept(dlg);
    }

    /**
     * 原版 {@code DoEvent} case 10 的「关闭调试」框（{@code myGameView.java:2625}）：
     * 标题「关闭调试」+ 一个<b>默认勾选</b>的复选框「关卡回到该“宏”打开前的状态」+ 单个「确定」。
     *
     * <p>PC 端早先把它退化成了 {@code JOptionPane.showConfirmDialog} 的「是 / 否」框 —— 两处不符：
     * <ol>
     *   <li>原版是<b>复选框</b>：勾不勾都要关框，勾了才复位关卡；「是 / 否」则把「否」变成了
     *       「什么都不做、框也不关」，语义完全不同。</li>
     *   <li>原版 {@code .create().show()} <b>不阻塞</b>，PC 的 {@code JOptionPane} 是模态的。</li>
     * </ol>
     * 这里按 {@code setView(isBack)} 的等价物重建：{@code HoloAlertDialog} 外壳 +
     * 复选框内容 + 单个「确定」，走非模态。
     *
     * <p>包内可见（而非 {@code private}）：Phase42 用例要直接驱动它验证复选框语义。
     */
    void showCloseMacroDebugDialog() {
        final JCheckBox isBack = new JCheckBox("关卡回到该“宏”打开前的状态");
        isBack.setSelected(true);                        // 原版 isBack.setChecked(true)
        isBack.setOpaque(false);
        isBack.setForeground(HoloContent.TEXT);          // primary_text_holo_dark
        isBack.setFont(HoloContent.font(Font.PLAIN));
        isBack.setFocusPainted(false);

        // 与 HoloMessageDialog.messageBody 同口径：左右 16dp、上下 8dp（TextView#message 的 padding）
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(8, 16, 8, 16));
        body.add(isBack, BorderLayout.CENTER);

        HoloAlertDialog dlg = HoloAlertDialog.createNonModal(this, "关闭调试");
        dlg.setContentView(body);
        dlg.addButton("确定", () -> {
            dlg.dispose();
            if (isBack.isSelected()) levelReset(false);  //正推关卡复位
            myMaps.isMacroDebug = false;
            mMap.invalidate();
        });
        dlg.setVisible(true);
    }

    /** 「死锁移动」提示框是否已在显示（原版 {@code Dialog.mShowing}）。测试可覆写以模拟。 */
    protected boolean isLockDlgVisible() {
        return lockDlg != null && lockDlg.isVisible();
    }

    // undo -- 正推
    public void UpData2(int i) {
        if ((i != 1 || state.m_nStep <= 0 || state.m_lstMovUnDo.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance())
                MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            else if (myMeet())
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            state.m_bBusing = false;
            state.m_bYanshi2 = false;
            return;
        }

        if (bt_IM.isChecked()) {
            while (state.m_nStep > 0) unDo1();
            mMap.invalidate();
            if (myClearance()) {
                state.m_bYanshi2 = false;
                MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                state.m_bYanshi2 = false;
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            state.m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                state.b_nRow = -1;
                state.b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                unDo1();
                myMaps.m_Sets[18] = -1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3)
                    mMap.d_Moves = 0;
                else mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
            }
            mMap.invalidate();
            sleepTimer(myTimer2, 1);
        }
    }

    // redo -- 逆推
    public void UpData3(int i) {
        if ((i != 1 || state.m_nStep <= 0 || state.m_lstMovReDo2.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance2()) {
                if (state.m_bMoved) {
                    zhengniHE2();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                if (state.m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            state.m_bBusing = false;
            return;
        }

        if (bt_IM.isChecked()) {
            if (state.m_bYanshi2) {
                do {
                    reDo2();
                } while (state.m_nStep > 0 && (state.m_bPush && state.m_lstMovReDo2.getLast() == state.m_Dir || !state.m_bPush && state.m_lstMovReDo2.getLast() < 5));
                mMap.invalidate();
                if (state.m_nStep > 0) {
                    sleepTimer(myTimer3, YanshiSpeed[myMaps.m_Sets[10]]);
                    state.m_bPush = false;
                }
            } else {
                while (state.m_nStep > 0) reDo2();
                mMap.invalidate();
            }
            if (myClearance2()) {
                if (state.m_bMoved) {
                    zhengniHE2();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                if (state.m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            state.m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                state.b_nRow = -1;
                state.b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                reDo2();
                myMaps.m_Sets[18] = 1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3) {
                    mMap.d_Moves = 0;
                } else {
                    if (!state.m_bACT_ERROR) {
                        mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
                    }
                }
            }
            mMap.invalidate();
            sleepTimer(myTimer3, 1);
        }

        // 逆推死锁判断
        if (state.m_nStep == 0 && myMaps.m_Sets[11] == 1 && mMap.d_Moves >= mMap.m_PicWidth && myLock2(state.m_iR10, state.m_iC10)) {
            showLockDlg();
        }
    }

    // undo -- 逆推
    public void UpData4(int i) {
        if ((i != 1 || state.m_nStep <= 0 || state.m_lstMovUnDo2.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance2())
                MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            else if (myMeet())
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            state.m_bBusing = false;
            return;
        }

        if (bt_IM.isChecked()) {
            while (state.m_nStep > 0) unDo2();
            mMap.invalidate();
            if (myClearance2())
                MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            else if (myMeet())
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            state.m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                state.b_nRow = -1;
                state.b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                unDo2();
                myMaps.m_Sets[18] = -1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3)
                    mMap.d_Moves = 0;
                else mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
            }
            mMap.invalidate();
            sleepTimer(myTimer4, 1);
        }
    }


    public void reDo1() {
        if (mMap.m_lGoto) {
            int len = state.m_lstMovUnDo.size();
            int len2 = len + state.m_lstMovReDo.size();
            if (len2 > 0) mMap.curMoves = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            else mMap.curMoves = 0;
        }
        boolean hadMove = !state.m_lstMovReDo.isEmpty();
        state.reDo1();
        if (hadMove) resetForwardHints();
        if (mMap.Box_Row0 < 0 && state.lastBoxFromRow >= 0) {
            mMap.Box_Row0 = state.lastBoxFromRow;
            mMap.Box_Col0 = state.lastBoxFromCol;
        }
        mMap.m_iR = state.m_nRow;
        mMap.m_iC = state.m_nCol;
    }

    private void resetForwardHints() {
        if (mMap.m_bBoxTo) mMap.m_bBoxTo = false;
        if (mMap.m_bManTo) mMap.m_bManTo = false;
        if (mMap.m_boxCanMove) mMap.m_boxCanMove = false;
        if (mMap.m_boxNoMoved) mMap.m_boxNoMoved = false;
        if (mMap.m_boxNoUsed) mMap.m_boxNoUsed = false;
        if (mMap.m_boxCanCome) mMap.m_boxCanCome = false;
    }

    private void resetBackwardHints() {
        if (mMap.m_bBoxTo2) mMap.m_bBoxTo2 = false;
        if (mMap.m_bManTo2) mMap.m_bManTo2 = false;
        if (mMap.m_boxCanMove2) mMap.m_boxCanMove2 = false;
        if (mMap.m_boxCanCome2) mMap.m_boxCanCome2 = false;
    }



    public void unDo1() {
        if (mMap.m_lGoto) {
            int len = state.m_lstMovUnDo.size();
            int len2 = len + state.m_lstMovReDo.size();
            if (len2 > 0) mMap.curMoves = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            else mMap.curMoves = 0;
        }
        boolean hadMove = !state.m_lstMovUnDo.isEmpty();
        state.unDo1();
        if (hadMove) resetForwardHints();
        mMap.m_iR = state.m_nRow;
        mMap.m_iC = state.m_nCol;
    }



    public void reDo2() {
        if (mMap.m_lGoto2) {
            int len = state.m_lstMovUnDo2.size();
            int len2 = len + state.m_lstMovReDo2.size();
            if (len2 > 0) mMap.curMoves2 = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            else mMap.curMoves2 = 0;
        }
        boolean hadMove = !state.m_lstMovReDo2.isEmpty();
        state.reDo2();
        if (hadMove) resetBackwardHints();
        mMap.m_iR = state.m_nRow2;
        mMap.m_iC = state.m_nCol2;
    }



    public void unDo2() {
        if (mMap.m_lGoto2) {
            int len = state.m_lstMovUnDo2.size();
            int len2 = len + state.m_lstMovReDo2.size();
            if (len2 > 0) mMap.curMoves2 = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            else mMap.curMoves2 = 0;
        }
        boolean hadMove = !state.m_lstMovUnDo2.isEmpty();
        state.unDo2();
        if (hadMove) resetBackwardHints();
        mMap.m_iR = state.m_nRow2;
        mMap.m_iC = state.m_nCol2;
    }


    private void goHome() {
        boolean changed = state.m_nRow2 != state.m_nRow0 || state.m_nCol2 != state.m_nCol0;
        state.goHome();
        if (changed) mMap.invalidate();
    }


    private void myPre(int n) {
        bt_BK.setChecked(false);
        bt_Sel.setChecked(false);
        myMaps.curMap = myMaps.m_lstMaps.get(n);
        myMaps.m_nTrun = myMaps.curMap.Trun;
        bt_TR.setText(myMaps.m_nTrun + " 转");
        initMap();
        state.ls_bk_cArray = state.bk_cArray;
    }

    private void myNext(int n) {
        bt_BK.setChecked(false);
        bt_Sel.setChecked(false);
        myMaps.curMap = myMaps.m_lstMaps.get(n);
        myMaps.m_nTrun = myMaps.curMap.Trun;
        bt_TR.setText(myMaps.m_nTrun + " 转");
        initMap();
        state.ls_bk_cArray = state.bk_cArray;
    }

    private void levelReset(boolean flg) {
        state.undoAll(flg);
        if (flg) {
            resetBackwardHints();
            mMap.m_iR = state.m_nRow2;
            mMap.m_iC = state.m_nCol2;
        } else {
            resetForwardHints();
            mMap.m_iR = state.m_nRow;
            mMap.m_iC = state.m_nCol;
        }

        state.m_nStep = 0;
        state.m_bBusing = false;
        state.m_bMoved = false;
        myMaps.m_StateIsRedy = false;
        myMaps.m_Sets[14] = 2;
        myMaps.m_Sets[5] = 2;
    }



    private void newGame() {
        state.beginNewGame();
        try {
            if (myMaps.curMap == null) return;
            state.loadLevel(myMaps.curMap.Map, myMaps.curMap.Rows, myMaps.curMap.Cols);

            mMap.m_iR = state.m_nRow;
            mMap.m_iC = state.m_nCol;

            mMap.m_bBoxTo = false;
            mMap.m_bBoxTo2 = false;
            mMap.m_bManTo = false;
            mMap.m_bManTo2 = false;
            mMap.m_boxCanMove = false;
            mMap.m_boxCanMove2 = false;
            mMap.m_boxNoMoved = false;
            mMap.m_boxNoUsed = false;
            mMap.m_boxCanCome = false;
            mMap.m_boxCanCome2 = false;
            mMap.m_lGoto = false;
            mMap.m_lGoto2 = false;
            mMap.m_lParityBrightnessShade = false;
            myMaps.m_bBiaochi = false;
            myMaps.m_bBianhao = false;

            state.endNewGame();
            myMaps.m_StateIsRedy = false;
            m_imPort_YASS = "";
            myMaps.m_Sets[14] = 2;
            myMaps.m_Sets[5] = 2;
        } catch (Throwable ex) {
            myStop();
            if (myMaps.curMap != null) {
                myMaps.curMap.Title = "无效关卡";
                myMaps.curMap.Map = "--";
                myMaps.curMap.Rows = 1;
                myMaps.curMap.Cols = 2;
            }
        }
    }


    public void initMap() {
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
        StopMicro();
        newGame();
        if (myMaps.curMap == null) return;

        mMap.m_lChangeBK = false;
        myMaps.isRecording = false;
        mMap.d_Moves = mMap.m_PicWidth;
        myMaps.m_Sets[13] = 0;
        levelReset(false);

        mMap.initArena();

        if (mySQLite.m_SQL != null) {
            mySQLite.m_SQL.Set_L_DateTime(myMaps.curMap.Level_id);
            mySQLite.m_SQL.load_StateList(myMaps.curMap.Level_id, myMaps.curMap.key);
        }

        if (myMaps.mState2.size() > 0) {
            myMaps.m_State = mySQLite.m_SQL.load_State(myMaps.mState2.get(0).id);
            if (myMaps.m_State.ans.length() > 0) formatPath(myMaps.m_State.ans, false);
        } else if (myMaps.m_Sets[37] == 1 && myMaps.mState1.size() > 0) {
            Collections.sort(myMaps.mState1, new Comparator<state_Node>() {
                @Override
                public int compare(state_Node o1, state_Node o2) {
                    return o2.time.compareTo(o1.time);
                }
            });
            myMaps.m_State = mySQLite.m_SQL.load_State(myMaps.mState1.get(0).id);
            state.m_nLastSteps = -1;
            int len = myMaps.m_State.ans.length();
            if (len > 0) {
                formatPath(myMaps.m_State.ans, false);
                if (myMaps.m_State.time.toLowerCase().indexOf("yass") >= 0) {
                    m_imPort_YASS = "[YASS]";
                } else if (myMaps.m_State.time.toLowerCase().indexOf("导入") >= 0) {
                    m_imPort_YASS = "[导入]";
                } else {
                    m_imPort_YASS = "";
                }
                len = state.m_lstMovReDo.size();
                for (int k = 0; k < len; k++) reDo1();
                state.m_bBusing = false;
            }

            len = myMaps.m_State.bk_ans.length();
            if (len > 0) {
                try {
                    state.m_nRow0 = myMaps.m_State.r;
                    state.m_nCol0 = myMaps.m_State.c;
                    state.m_nRow2 = state.m_nRow0;
                    state.m_nCol2 = state.m_nCol0;
                    state.bk_cArray[state.m_nRow2][state.m_nCol2] = (state.bk_cArray[state.m_nRow2][state.m_nCol2] == '-' ? '@' : '+');
                    formatPath(myMaps.m_State.bk_ans, true);
                    len = state.m_lstMovReDo2.size();
                    for (int k = 0; k < len; k++) reDo2();
                    state.m_bBusing = false;
                } catch (ArrayIndexOutOfBoundsException ex) {
                    state.m_nRow0 = -1;
                    state.m_nCol0 = -1;
                    state.m_nRow2 = state.m_nRow0;
                    state.m_nCol2 = state.m_nCol0;
                    state.m_lstMovReDo2.clear();
                }
            }
        }

        mTask = new AsyncCountBoxsTask(this);
        mTask.execute();

        myMaps.m_MapChange = true;
        state.m_bACT_ERROR = false;
        myMaps.isMacroDebug = false;
        mMap.m_lShowAnsInf = false;
    }

    /**
     * 处理「打开状态」——原版 {@code myGameView.OpenState()}（由 {@code onStart()} 里
     * {@code if (myMaps.m_StateIsRedy) OpenState();} 触发）。
     *
     * <p>入口在 {@code myStateBrow} 的上下文菜单「打开」：它把选中的状态读进
     * {@code myMaps.m_State} 并置 {@code m_StateIsRedy = true}，然后关闭自己，
     * 回到已经开着的游戏窗口。原版靠 Activity 的 {@code onStart()} 感知「回来了」；
     * PC 端没有 Activity 生命周期，改用窗口重新获得焦点（{@code windowActivated}）等价触发，
     * 见构造器里安装的 {@link WindowAdapter}。
     *
     * <p>注意 {@code solution == 0} 表示「答案」（停在开始位置），
     * 否则是「状态」（停在结束位置）——两者处理不同，不要合并。
     */
    private void OpenState() {
        state.m_nLastSteps = -1;
        myMaps.m_StateIsRedy = false;
        try {
            levelReset(false);  //正推复位
            myMaps.m_Sets[13] = 0;  //求解后，关闭“互动双推”模式

            int len = myMaps.m_State.ans.length();
            if (len > 0) {
                formatPath(myMaps.m_State.ans, false);
                if (myMaps.m_State.time.toLowerCase().indexOf("yass") >= 0) {
                    m_imPort_YASS = "[YASS]";
                } else if (myMaps.m_State.time.toLowerCase().indexOf("导入") >= 0) {
                    m_imPort_YASS = "[导入]";
                } else {
                    m_imPort_YASS = "";
                }
                if (myMaps.m_State.solution == 0) {  //答案，停在开始位置；状态，停在结束位置
                    len = state.m_lstMovReDo.size();
                    for (int k = 0; k < len; k++) reDo1();
                    state.m_bBusing = false;
                } else
                    MyToast.showToast(this, "答案加载成功！", MyToast.LENGTH_SHORT);
            }

            len = myMaps.m_State.bk_ans.length();
            if (len > 0) {
                try {
                    levelReset(true);  //逆推复位
                    try {
                        if (state.bk_cArray[state.m_nRow2][state.m_nCol2] == '@') state.bk_cArray[state.m_nRow2][state.m_nCol2] = '-';
                        else if (state.bk_cArray[state.m_nRow2][state.m_nCol2] == '+') state.bk_cArray[state.m_nRow2][state.m_nCol2] = '.';
                    } catch (ArrayIndexOutOfBoundsException ex) { }
                    state.m_nRow0 = myMaps.m_State.r;
                    state.m_nCol0 = myMaps.m_State.c;
                    state.m_nRow2 = state.m_nRow0;
                    state.m_nCol2 = state.m_nCol0;
                    state.bk_cArray[state.m_nRow2][state.m_nCol2] = (state.bk_cArray[state.m_nRow2][state.m_nCol2] == '-' ? '@' : '+');
                    formatPath(myMaps.m_State.bk_ans, true);
                    len = state.m_lstMovReDo2.size();
                    for (int k = 0; k < len; k++) reDo2();
                    state.m_bBusing = false;
                } catch (ArrayIndexOutOfBoundsException ex) {
                    state.m_nRow0 = -1;
                    state.m_nCol0 = -1;
                    state.m_nRow2 = state.m_nRow0;
                    state.m_nCol2 = state.m_nCol0;
                    state.m_lstMovReDo2.clear();
                }
            }
            bt_BK.setChecked(false);  //强制回到正推界面
            mMap.repaint();           //原版 mMap.invalidate()
        } catch (Throwable ex) {
        }
    }












    private boolean myLock(int bRow, int bCol) {
        if (state.m_iR9 > -1) {
            if (state.mArray9 != null && state.isLockGoal(state.m_cArray, bRow, bCol, state.m_nRow, state.m_nCol)) {
                lockDlg.setMessage("这一步造成关卡死锁，继续吗？\n（点位不足）");
                return true;
            } else if (state.freezeDeadlock != null && state.freezeDeadlock.isDeadlock(bRow, bCol)) {
                lockDlg.setMessage("这一步造成关卡死锁，继续吗？\n（僵位冻结）");
                return true;
            } else if (state.closedDiagonalLock != null && state.closedDiagonalLock.isDeadlock(bRow * state.m_cArray[0].length + bCol)) {
                lockDlg.setMessage("这一步造成关卡死锁，继续吗？\n（闭锁对角）");
                return true;
            }
        }
        return false;
    }

    private boolean myLock2(int bRow, int bCol) {
        if (state.m_iR10 > -1 && state.mark14 != null) {
            if (state.mArray9 != null && state.isLockCount(state.bk_cArray, state.mark14, bRow, bCol, state.m_nRow2, state.m_nCol2)) {
                lockDlg.setMessage("这一步造成关卡死锁，继续吗？\n（点位不足）");
                return true;
            } else if (state.isLockNet2(state.bk_cArray, bRow, bCol)) {  //「网」型死锁
                lockDlg.setMessage("这一步造成关卡死锁，继续吗？\n（网位互锁）");
                return true;
            }
        }
        return false;
    }

    private void mySetProgressBar() {
        state.m_nLastSteps = -1;
        if (bt_BK.isChecked()) {
            int len = state.m_lstMovUnDo2.size();
            int len2 = state.m_lstMovUnDo2.size() + state.m_lstMovReDo2.size();
            if (len2 > 0) {
                mMap.curMoves2 = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
                mMap.m_lGoto2 = true;
            } else {
                MyToast.showToast(this, "尚无动作可用！", MyToast.LENGTH_SHORT);
            }
        } else {
            int len = state.m_lstMovUnDo.size();
            int len2 = state.m_lstMovUnDo.size() + state.m_lstMovReDo.size();
            if (len2 > 0) {
                mMap.curMoves = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
                mMap.m_lGoto = true;
            } else {
                MyToast.showToast(this, "尚无动作可用！", MyToast.LENGTH_SHORT);
            }
        }
        mMap.invalidate();
    }

    private void skinList() {
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "皮肤/");
        myMaps.mFile_List1.clear();
        myMaps.mFile_List1.add("默认皮肤");
        if (!targetDir.exists()) targetDir.mkdirs();
        else {
            String[] filelist = targetDir.list();
            if (filelist != null) {
                Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
                for (String s : filelist) {
                    int dot = s.lastIndexOf('.');
                    if (dot > -1 && dot < s.length()) {
                        String prefix = s.substring(dot + 1);
                        if (prefix.equalsIgnoreCase("png"))
                            myMaps.mFile_List1.add(s);
                    }
                }
            }
        }
    }

    private void bkPicList() {
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "背景/");
        myMaps.mFile_List2.clear();
        myMaps.mFile_List2.add("使用背景色");
        if (!targetDir.exists()) targetDir.mkdirs();
        else {
            String[] filelist = targetDir.list();
            if (filelist != null) {
                Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
                for (String s : filelist) {
                    int dot = s.lastIndexOf('.');
                    if (dot > -1 && dot < s.length()) {
                        String prefix = s.substring(dot + 1);
                        if (prefix.equalsIgnoreCase("jpg") || prefix.equalsIgnoreCase("bmp") || prefix.equalsIgnoreCase("png"))
                            myMaps.mFile_List2.add(s);
                    }
                }
            }
        }
    }

    public boolean myClearance() {
        return state.isSolved();
    }


    public boolean myClearance2() {
        return state.isSolvedBackward();
    }


    public boolean myMeet() {
        boolean backward = bt_BK.isChecked();
        boolean flg = state.meet(backward);
        if (flg) {
            mMap.m_iR = backward ? state.m_nRow2 : state.m_nRow;
            mMap.m_iC = backward ? state.m_nCol2 : state.m_nCol;
        }
        return flg;
    }


    public void DoEvent(int act) {
        mMap.m_bBoxTo = false;
        mMap.m_bBoxTo2 = false;
        mMap.m_bManTo = false;
        mMap.m_bManTo2 = false;
        mMap.m_boxCanMove = false;
        mMap.m_boxCanMove2 = false;
        state.m_bNetLock = false;
        mMap.invalidate();
        switch (act) {
            case 0:
                if (myMaps.m_Sets[13] == 0) {
                    myMaps.m_Sets[13] = 1;
                } else {
                    myMaps.m_Sets[13] = 0;
                }
                mMap.invalidate();
                break;
            case 1:
                mMap.Box_Row0 = -1;
                state.m_nLastSteps = state.m_lstMovUnDo.size();
                UpData1(1);
                break;
            case 2:
                UpData3(1);
                break;
            case 3:
                bt_UnDo.onKeyLongPress(0, null);
                break;
            case 4:
                bt_ReDo.onKeyLongPress(0, null);
                break;
            case 5:
                bt_UnDo.setChecked(!bt_UnDo.isChecked());
                break;
            case 6:
                bt_ReDo.setChecked(!bt_ReDo.isChecked());
                break;
            case 7:
                StopMicro();
                if (myMaps.m_lstMaps != null) {
                    int n1 = myMaps.m_lstMaps.indexOf(myMaps.curMap);
                    if (n1 > 0) {
                        if (state.m_bMoved) {
                            // 原版 exitDlg2.show()（myGameView.java:2581 / 5190）—— 非阻塞
                            showDialog(exitDlg2);
                        } else myPre(n1 - 1);
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                }
                break;
            case 8:
                StopMicro();
                if (myMaps.m_lstMaps != null) {
                    int n2 = myMaps.m_lstMaps.indexOf(myMaps.curMap);
                    if (n2 >= 0 && n2 + 1 < myMaps.m_lstMaps.size()) {
                        if (state.m_bMoved) {
                            // 原版 exitDlg3.show()（myGameView.java:2592 / 5200）—— 非阻塞
                            showDialog(exitDlg3);
                        } else myNext(n2 + 1);
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                }
                break;
            case 9:
                setACT(true);
                myMaps.m_ActionIsRedy = false;
                if (bt_BK.isChecked())
                    myMaps.m_nRecording_Bggin2 = state.m_lstMovUnDo2.size();
                else
                    myMaps.m_nRecording_Bggin = state.m_lstMovUnDo.size();
                MyToast.showToast(this, "已送入导入缓存", MyToast.LENGTH_SHORT);
                break;
            case 10:
                if (myMaps.isMacroDebug) {
                    // 原版 myGameView.java:2625 的「关闭调试」框（见 showCloseMacroDebugDialog）
                    showCloseMacroDebugDialog();
                } else {
                    StopMicro();
                    mLoad_Do_Macro();
                }
                break;
            case 11:
                if (myMaps.isMacroDebug) {
                    MyToast.showToast(this, "“宏”调试时，不支持此功能！", MyToast.LENGTH_SHORT);
                } else {
                    if (bt_BK.isChecked()) {
                        if (mMap.m_lGoto2) mMap.m_lGoto2 = false;
                        else mySetProgressBar();
                    } else {
                        if (mMap.m_lGoto) mMap.m_lGoto = false;
                        else mySetProgressBar();
                    }
                }
                break;
            case 12:
                if (myMaps.m_Sets[38] == 1) {
                    myMaps.m_Sets[38] = 0;
                    MyToast.showToast(this, "奇偶格模式 - 关", MyToast.LENGTH_SHORT);
                } else {
                    myMaps.m_Sets[38] = 1;
                    MyToast.showToast(this, "奇偶格模式 - 开", MyToast.LENGTH_SHORT);
                }
                break;
        }
    }

    private void setACT(boolean flg) {
        StringBuilder s1 = new StringBuilder();
        StringBuilder s2 = new StringBuilder();
        try {
            char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
            Byte t;
            Iterator<Byte> myItr;
            if (bt_BK.isChecked()) {
                if (!state.m_lstMovUnDo2.isEmpty()) {
                    myItr = state.m_lstMovUnDo2.iterator();
                    while (myItr.hasNext()) {
                        t = myItr.next();
                        s1.append(Move[t - 1]);
                    }
                }
                if (myMaps.isRecording && flg) {
                    if (!state.m_lstMovUnDo2.isEmpty() && myMaps.m_nRecording_Bggin2 < state.m_lstMovUnDo2.size()) {
                        myItr = state.m_lstMovUnDo2.iterator();
                        for (int k = 0; k < myMaps.m_nRecording_Bggin2; k++) {
                            if (myItr.hasNext()) myItr.next();
                            else break;
                        }
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                } else {
                    if (!state.m_lstMovReDo2.isEmpty()) {
                        myItr = state.m_lstMovReDo2.descendingIterator();
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                }
            } else {
                if (!state.m_lstMovUnDo.isEmpty()) {
                    myItr = state.m_lstMovUnDo.iterator();
                    while (myItr.hasNext()) {
                        t = myItr.next();
                        s1.append(Move[t - 1]);
                    }
                }
                if (myMaps.isRecording && flg) {
                    if (!state.m_lstMovUnDo.isEmpty() && myMaps.m_nRecording_Bggin < state.m_lstMovUnDo.size()) {
                        myItr = state.m_lstMovUnDo.iterator();
                        for (int k = 0; k < myMaps.m_nRecording_Bggin; k++) {
                            if (myItr.hasNext()) myItr.next();
                            else break;
                        }
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                } else {
                    if (!state.m_lstMovReDo.isEmpty()) {
                        myItr = state.m_lstMovReDo.descendingIterator();
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                }
            }
            saveAct("act1", s1.toString());
            saveAct("act2", s2.toString());
        } catch (Throwable ex) { }
    }



    public void zhengniHE2() {
        if (state.m_nRow2 != state.m_nRow3 || state.m_nCol2 != state.m_nCol3)
            FindPath(state.m_nRow3, state.m_nCol3, true);

        int len = state.m_lstMovReDo2.size();
        for (int k = 0; k < len; k++) reDo2();
        state.m_bBusing = false;

        byte t;
        int s = 0;
        len = state.m_lstMovUnDo2.size();
        for (int k = 0; k < len; k++) {
            t = state.m_lstMovUnDo2.get(k);
            if (t < 5) s++;
            else break;
        }

        len = state.m_lstMovUnDo.size();
        for (int k = 0; k < len; k++) unDo1();
        state.m_lstMovReDo.clear();
        state.m_lstMovUnDo.clear();
        state.m_iStep[0] = 0;
        state.m_iStep[1] = 0;

        Byte mDir;
        Iterator<Byte> myItr = state.m_lstMovUnDo2.iterator();
        for (int k = 0; k < s; k++) {
            if (myItr.hasNext()) myItr.next();
            else break;
        }
        while (myItr.hasNext()) {
            mDir = myItr.next();
            switch (mDir) {
                case -1:
                case 1: state.m_lstMovReDo.offer((byte) 3); break;
                case -2:
                case 2: state.m_lstMovReDo.offer((byte) 4); break;
                case -3:
                case 3: state.m_lstMovReDo.offer((byte) 1); break;
                case -4:
                case 4: state.m_lstMovReDo.offer((byte) 2); break;
                case -5:
                case 5: state.m_lstMovReDo.offer((byte) 7); break;
                case -6:
                case 6: state.m_lstMovReDo.offer((byte) 8); break;
                case -7:
                case 7: state.m_lstMovReDo.offer((byte) 5); break;
                case -8:
                case 8: state.m_lstMovReDo.offer((byte) 6); break;
            }
        }
        mMap.invalidate();
    }

    public void zhengniHE() {
        if (state.m_nRow2 != state.m_nRow || state.m_nCol2 != state.m_nCol) {
            FindPath(state.m_nRow2, state.m_nCol2, false);
        } else {
            state.m_lstMovReDo.clear();
        }

        byte t;
        int s = 0;
        int len = state.m_lstMovUnDo2.size();
        for (int k = 0; k < len; k++) {
            t = state.m_lstMovUnDo2.get(k);
            if (t < 5) {
                s++;
            } else break;
        }

        Byte mDir;
        len = state.m_lstMovUnDo2.size();
        Iterator<Byte> myItr = state.m_lstMovUnDo2.descendingIterator();
        while (myItr.hasNext()) {
            mDir = myItr.next();
            if (--len < s) break;
            switch (mDir) {
                case -1:
                case 1: state.m_lstMovReDo.offerFirst((byte) 3); break;
                case -2:
                case 2: state.m_lstMovReDo.offerFirst((byte) 4); break;
                case -3:
                case 3: state.m_lstMovReDo.offerFirst((byte) 1); break;
                case -4:
                case 4: state.m_lstMovReDo.offerFirst((byte) 2); break;
                case -5:
                case 5: state.m_lstMovReDo.offerFirst((byte) 7); break;
                case -6:
                case 6: state.m_lstMovReDo.offerFirst((byte) 8); break;
                case -7:
                case 7: state.m_lstMovReDo.offerFirst((byte) 5); break;
                case -8:
                case 8: state.m_lstMovReDo.offerFirst((byte) 6); break;
            }
        }
        mMap.invalidate();
    }

    private void saveToFile(final String str, String fn) {
        try {
            FileOutputStream fout = new FileOutputStream(myMaps.sRoot + myMaps.sPath + fn);
            fout.write(str.getBytes());
            fout.flush();
            fout.close();
            MyToast.showToast(this, "保存成功！", MyToast.LENGTH_SHORT);
        } catch (Exception e) {
            MyToast.showToast(this, "出错了，保存失败！", MyToast.LENGTH_SHORT);
        }
    }

    public void saveAns(int m_Solution) {
        StringBuilder s1 = new StringBuilder();
        StringBuilder s2 = new StringBuilder();
        byte t;
        Iterator<Byte> myItr;

        char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
        if (!state.m_lstMovUnDo.isEmpty()) {
            myItr = state.m_lstMovUnDo.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s1.append(Move[t - 1]);
            }
        }

        if (m_Solution == 0 && !state.m_lstMovUnDo2.isEmpty()) {
            myItr = state.m_lstMovUnDo2.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s2.append(Move[t - 1]);
            }
        }

        if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("yass") >= 0) {
            m_imPort_YASS = "[YASS]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("导入") >= 0) {
            m_imPort_YASS = "[导入]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else {
            m_imPort_YASS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }

        if (m_Solution == 0 && (state.m_lstMovUnDo.size() > myMaps.m_nMaxSteps || state.m_lstMovUnDo2.size() > myMaps.m_nMaxSteps) || m_Solution == 1 && state.m_lstMovUnDo.size() > myMaps.m_nMaxSteps) {
            final StringBuilder str = new StringBuilder();
            str.append(myMaps.curMap.Map).append("\nTitle: ").append(myMaps.curMap.Title).append("\nAuthor: ").append(myMaps.curMap.Author);
            str.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:\n");

            if (m_Solution == 0) {
                str.append(s1).append("\n[").append(state.m_nRow0).append(", ").append(state.m_nCol0).append("]").append(s2);
            } else {
                str.append("Solution (moves ").append(state.m_iStep[1]).append(", pushes ").append(state.m_iStep[0]).append(": ");
                str.append(m_imPort_YASS);
                str.append(s1);
            }

            final String my_Name = "导入/" + myMaps.sFile + "_" + (myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1);
            File file;
            int n = 1;
            file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            while (file.exists()) {
                n++;
                file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            }
            final int finalN = n;
            // 原版 myGameView.java:3125 —— AlertDialog(THEME_HOLO_DARK) 标题「注意」+ 取消/确定，
            // setCancelable(false)。⚠️ 本方法由 UpData1()（动画回调）调用，所以必须用非模态：
            // 模态框会在 EDT 上开嵌套事件循环，定时器重入 → 层层嵌套 → 卡死。
            HoloConfirmDialog.showNonModal(this, "注意",
                    "答案或状态太长，保存到文档！\n" + my_Name + "(" + n + ").txt",
                    "取消", "确定",
                    () -> saveToFile(str.toString(), my_Name + "(" + finalN + ").txt"));
        } else {
            long hh = mySQLite.m_SQL.add_S(myMaps.curMap.Level_id,
                    m_Solution,
                    state.m_iStep[1],
                    state.m_iStep[0],
                    m_Solution == 0 ? state.m_iStep[3] : 0,
                    m_Solution == 0 ? state.m_iStep[2] : 0,
                    m_Solution == 0 ? state.m_nRow0 : -1,
                    m_Solution == 0 ? state.m_nCol0 : -1,
                    s1.toString(),
                    s2.toString(),
                    myMaps.curMap.key,
                    m_Solution == 0 ? -1 : myMaps.curMap.L_CRC_Num,
                    m_Solution == 0 ? "" : myMaps.curMap.Map0,
                    m_imPort_YASS);

            state.m_bMoved = false;
            if (hh > 0) {
                if (m_Solution == 1) {
                    myMaps.curMap.Solved |= (m_Solution == 1);
                    MyToast.showToast(this, "答案已保存！", MyToast.LENGTH_LONG);
                    state_Node ans = new state_Node();
                    ans.id = hh;
                    ans.pid = myMaps.curMap.Level_id;
                    ans.pkey = myMaps.curMap.key;
                    ans.moves = state.m_iStep[1];
                    ans.pushs = state.m_iStep[0];
                    ans.inf = "移动: " + state.m_iStep[1] + ", 推动: " + state.m_iStep[0];
                    ans.time = m_imPort_YASS;
                    myMaps.mState2.add(ans);
                    mMap.invalidate();
                } else {
                    MyToast.showToast(this, "状态已保存！", MyToast.LENGTH_LONG);
                }
            } else if (hh == 0) {
                if (m_Solution == 1) {
                    myMaps.curMap.Solved |= (m_Solution == 1);
                    MyToast.showToast(this, "答案有重复，未再保存！\n移动：" + (state.m_iStep[1] + state.m_lstMovReDo.size()) + "，推动：" + (state.m_iStep[0] + state.m_iStep[2]), MyToast.LENGTH_LONG);
                } else {
                    MyToast.showToast(this, "有重复，已重排！", MyToast.LENGTH_LONG);
                }
            } else {
                myMaps.curMap.Solved |= (m_Solution == 1);
                MyToast.showToast(this, "出错了，保存失败！", MyToast.LENGTH_LONG);
            }
        }
    }

    public void saveAns2() {
        StringBuilder s1 = new StringBuilder();
        byte t;
        Iterator<Byte> myItr;

        char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
        if (!state.m_lstMovUnDo.isEmpty()) {
            myItr = state.m_lstMovUnDo.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s1.append(Move[t - 1]);
            }
        }
        if (!state.m_lstMovReDo.isEmpty()) {
            myItr = state.m_lstMovReDo.descendingIterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s1.append(Move[t - 1]);
            }
        }

        if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("yass") >= 0) {
            m_imPort_YASS = "[YASS]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("导入") >= 0) {
            m_imPort_YASS = "[导入]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else {
            m_imPort_YASS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }

        if (state.m_lstMovUnDo.size() + state.m_lstMovReDo.size() > myMaps.m_nMaxSteps) {
            final StringBuilder str = new StringBuilder();
            str.append(myMaps.curMap.Map).append("\nTitle: ").append(myMaps.curMap.Title).append("\nAuthor: ").append(myMaps.curMap.Author);
            str.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:\n");
            str.append("Solution (moves ").append(state.m_iStep[1] + state.m_lstMovReDo.size()).append(", pushes ").append(state.m_iStep[0] + state.m_iStep[2]).append(": ");
            str.append(m_imPort_YASS);
            str.append(s1);

            File targetDir = new File(myMaps.sRoot + myMaps.sPath + "超长答案/");
            if (!targetDir.exists()) targetDir.mkdirs();

            final String my_Name = "超长答案/" + myMaps.sFile + "_" + (myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1);
            File file;
            int n = 1;
            file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            while (file.exists()) {
                n++;
                file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            }
            final int finalN = n;
            // 原版 myGameView.java:3236 —— 同 saveAns()，标题「注意」+ 取消/确定。
            // ⚠️ 同样在 UpData1() / UpData3() 的动画回调里，必须非模态。
            HoloConfirmDialog.showNonModal(this, "注意",
                    "答案太长，保存到文档！\n" + my_Name + "(" + n + ").txt",
                    "取消", "确定",
                    () -> saveToFile(str.toString(), my_Name + "(" + finalN + ").txt"));
        } else {
            long hh = mySQLite.m_SQL.add_S(myMaps.curMap.Level_id,
                    myMaps.curMap.Level_id > 0 ? 1 : 0,
                    state.m_iStep[1] + state.m_lstMovReDo.size(),
                    state.m_iStep[0] + state.m_iStep[2],
                    0,
                    0,
                    -1,
                    -1,
                    s1.toString(),
                    "",
                    myMaps.curMap.key,
                    myMaps.curMap.L_CRC_Num,
                    myMaps.curMap.Map0,
                    m_imPort_YASS);

            myMaps.curMap.Solved = true;
            state.m_bMoved = false;
            if (myMaps.curMap.Level_id > 0) {
                if (hh > 0) {
                    state_Node ans = new state_Node();
                    ans.id = hh;
                    ans.pid = myMaps.curMap.Level_id;
                    ans.pkey = myMaps.curMap.key;
                    ans.inf = "移动: " + state.m_iStep[1] + ", 推动: " + state.m_iStep[0];
                    ans.time = m_imPort_YASS;
                    myMaps.mState2.add(ans);
                    // 原版 myGameView.java:3272 —— AlertDialog(THEME_HOLO_DARK) 标题「正逆相合或通关！」
                    // + 单个「确定」，setCancelable(false)。三条都在动画回调里，必须非模态。
                    HoloMessageDialog.showNonModal(this, "正逆相合或通关！",
                            "答案成功保存！\n(可用进退键观看通关演示)", "确定");
                } else if (hh == 0) {
                    HoloMessageDialog.showNonModal(this, "正逆相合或通关！",
                            "答案有重复！\n移动：" + (state.m_iStep[1] + state.m_lstMovReDo.size())
                                    + "，推动：" + (state.m_iStep[0] + state.m_iStep[2])
                                    + "，\n本次未做保存！\n(可用进退键观看通关演示)", "确定");
                } else {
                    HoloMessageDialog.showNonModal(this, "正逆相合或通关！",
                            "DB写错误，答案未能保存，请利用剪切板手动保存到其它地方！\n(可用进退键观看通关演示)",
                            "确定");
                }
            }
        }
    }

    /**
     * 原版 {@code myGameView} 的选项菜单 —— {@code res/menu/player.xml} 的 13 项，
     * 由底栏「更多」按钮 {@code openOptionsMenu()} 弹出。
     *
     * <p>原版 {@code myGameView} 是 {@code FEATURE_NO_TITLE} + {@code FLAG_FULLSCREEN}，
     * **没有 ActionBar**，所以这里不用 {@code myActionBar}；菜单外壳与 ActionBar 溢出菜单
     * 共用同一套 {@code popup_menu_holo_dark} 样式，因此走 {@link HoloPopupMenu}。
     *
     * <p>条目顺序严格按 {@code player.xml}：设置… / 开关选项… / 重新开始 / 退至首 / 进至尾 /
     * 导出… / 导入… / YASS求解 / 打开状态… / 保存状态 / 关于 / 操作说明 / 退出。
     * （{@code player.xml} 里「Solver求解」整段被 {@code <!-- -->} 注释掉，原版不存在。）
     * {@code YASS求解} 已接（阶段 F）：原版那一步是跨应用 Intent，PC 无等价机制，
     * 等价于真机未装求解器 —— 见 {@link #onYassSolver()}。
     */
    public void openOptionsMenu() {
        JPopupMenu menu = HoloPopupMenu.create();

        HoloPopupMenu.addItem(menu, "设置...", this::showSetup1Dialog);
        HoloPopupMenu.addItem(menu, "开关选项...", this::showSetup2Dialog);
        HoloPopupMenu.addItem(menu, "重新开始", this::onReStart);
        HoloPopupMenu.addItem(menu, "退至首", this::onHome);
        HoloPopupMenu.addItem(menu, "进至尾", this::onEnd);
        HoloPopupMenu.addItem(menu, "导出...", this::onExport);
        HoloPopupMenu.addItem(menu, "导入...", this::onImport);
        HoloPopupMenu.addItem(menu, "YASS求解", this::onYassSolver);
        HoloPopupMenu.addItem(menu, "打开状态...", this::onOpenState);
        HoloPopupMenu.addItem(menu, "保存状态", this::onSaveState);
        // 原版 player_about → myAbout2（关卡描述），不是 myAbout。
        HoloPopupMenu.addItem(menu, "关于", () -> new myAbout2(this, myMaps.curMap).setVisible(true));
        // 原版 player_help：Bundle 传 m_Num = 1。
        HoloPopupMenu.addItem(menu, "操作说明", () -> new Help(1).setVisible(true));
        HoloPopupMenu.addItem(menu, "退出", this::handleExit);

        optionsMenu = menu;
        if (bt_More.isShowing()) menu.show(bt_More, 0, -menu.getPreferredSize().height);
    }

    /** 原版 {@code R.id.player_ReStart}「重新开始」。 */
    void onReStart() {
        // 原版 myGameView.java:3462 —— 无标题、取消/确定，复位逻辑整个写在 OnClickListener 里。
        // （原版那个 Builder 漏写了 THEME_HOLO_DARK，但应用主题本身就是深色，配色按 Holo 走。）
        HoloConfirmDialog dlg = new HoloConfirmDialog(this, "", "重新开始，确定吗？", "取消", "确定", () -> {
            mMap.d_Moves = mMap.m_PicWidth;
            if (bt_BK.isChecked()) {
                MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
                levelReset(true);
            } else {
                MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
                levelReset(false);
                if (myMaps.isMacroDebug) {
                    mMap.myMacro.clear();
                    mMap.myMacro.add(0);
                }
            }
            mMap.curMoves = 0;
            mMap.invalidate();
        });
        dlg.setVisible(true);
    }

    /** 原版 {@code R.id.player_Home}「退至首」。 */
    void onHome() {
        state.m_bYanshi = false;
        state.m_bYanshi2 = false;
        if (bt_BK.isChecked()) {
            if (state.m_lstMovUnDo2.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                state.m_bBusing = false;
            } else {
                state.m_nStep = state.m_lstMovUnDo2.size();
                UpData4(1);
            }
        } else {
            if (state.m_lstMovUnDo.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                state.m_bBusing = false;
            } else {
                state.m_nStep = state.m_lstMovUnDo.size();
                UpData2(1);
            }
        }
    }

    /** 原版 {@code R.id.player_End}「进至尾」。 */
    void onEnd() {
        state.m_bYanshi = false;
        state.m_bYanshi2 = false;
        if (bt_BK.isChecked()) {
            if (state.m_lstMovReDo2.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                state.m_bBusing = false;
            } else {
                if (state.m_lstMovUnDo2.isEmpty()) goHome();
                state.m_nStep = state.m_lstMovReDo2.size();
                UpData3(1);
            }
        } else {
            if (state.m_lstMovReDo.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                state.m_bBusing = false;
            } else {
                state.m_nStep = state.m_lstMovReDo.size();
                mMap.Box_Row0 = -1;
                state.m_nLastSteps = -1;
                UpData1(1);
            }
        }
    }

    /** 原版 {@code R.id.player_save}「保存状态」。 */
    void onSaveState() {
        if (state.m_lstMovUnDo.size() > 0 || state.m_lstMovUnDo2.size() > 0) {
            if ((myMaps.m_Sets[13] == 0 || state.m_iStep[2] <= 0) && state.m_nGoals_OK == state.m_nGoals
                    && state.m_iStep[0] > 0 && myMaps.curMap.Level_id > 0)
                saveAns(1);
            else
                saveAns(0);
        } else {
            MyToast.showToast(this, "没什么可保存的！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code R.id.player_Yass_Solver}「YASS求解」。
     * 逆推时没有此功能（原版 {@code mySolution()} 只接正推现场）。
     */
    void onYassSolver() {
        if (!bt_BK.isChecked()) {
            mySolution(0);   // YASS求解
        } else {
            MyToast.showToast(this, "逆推时，无此功能！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code myGameView.mySolution(int XYZ)}：先把当前状态自动存一次（自动查重），
     * 再 {@code startActivityForResult()} 把 {@code LEVEL} 交给第三方「YASS」求解器。
     *
     * <p><b>PC 上的落地方式</b>：原版这最后一步是 Android 的<b>跨应用 Intent</b> ——
     * {@code ComponentName("net.sourceforge.sokobanyasc.joriswit.yass", "yass.YASSActivity")}、
     * {@code action = "nl.joriswit.sokosolver.SOLVE"}、{@code extra = "LEVEL"}。
     * PC 没有等价的跨应用机制，等价于「设备上没装求解器」，所以这里直接抛出、
     * 落到与真机相同的 catch 分支：Toast「没有找到求解器！」。
     * <b>前面那段「自动保存状态」是真逻辑，照原版完整保留。</b>
     *
     * <p>求解成功后答案回流的路径见 {@link #onSolverResult(String, boolean)}。
     *
     * @param XYZ 0 = YASS 求解；1 = Festival 求解（原版该分支已被注释掉，PC 不接）
     */
    void mySolution(int XYZ) {
        try {
            // 拼接正逆推动作，进行查重和保存
            char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
            StringBuilder s1 = new StringBuilder();
            StringBuilder s2 = new StringBuilder();
            if (!state.m_lstMovUnDo.isEmpty()) {
                for (Byte t : state.m_lstMovUnDo) {
                    s1.append(Move[t - 1]);
                }
            }
            if (!state.m_lstMovUnDo2.isEmpty()) {
                s2.append("[").append(state.m_nRow0).append(", ").append(state.m_nCol0).append("]");
                for (Byte t : state.m_lstMovUnDo2) {
                    s2.append(Move[t - 1]);
                }
            }

            // 自动保存一下当前状态（自动查重），避免 yass 闪退造成丢失
            if ((state.m_iStep[1] > 0 || state.m_iStep[3] > 0)
                    && mySQLite.m_SQL.count_S(myMaps.curMap.Level_id, state.m_iStep[1], state.m_iStep[0],
                    state.m_iStep[3], state.m_iStep[2], state.m_nRow0, state.m_nCol0,
                    myMaps.getCRC32(s1.toString() + s2.toString())) <= 0) {
                if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("yass") >= 0) {
                    m_imPort_YASS = "[YASS]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                } else if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("导入") >= 0) {
                    m_imPort_YASS = "[导入]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                } else {
                    m_imPort_YASS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                }

                long hh = mySQLite.m_SQL.add_S(myMaps.curMap.Level_id,
                        3,
                        state.m_iStep[1],
                        state.m_iStep[0],
                        state.m_iStep[3],
                        state.m_iStep[2],
                        state.m_nRow0,
                        state.m_nCol0,
                        s1.toString(),
                        s2.toString(),
                        myMaps.curMap.key,
                        -1,
                        "",
                        m_imPort_YASS);

                state.m_bMoved = false;
                if (hh > 0) {
                    MyToast.showToast(this, "状态已保存！", MyToast.LENGTH_SHORT);
                }
            }

            // 原版：new Intent(ACTION_MAIN) + addCategory(CATEGORY_LAUNCHER)
            //       + setComponent(YASS 的 Activity) + setAction("nl.joriswit.sokosolver.SOLVE")
            //       + putExtra("LEVEL", myMaps.getLocale(state.m_cArray))
            //       + startActivityForResult(intent3, 1)
            // PC 无跨应用 Intent → 恒抛，等价于真机未安装 YASS。
            throw new UnsupportedOperationException(
                    "PC 上没有 YASS 求解器（原版是跨应用 Intent，非外部进程）");
        } catch (Exception e) {
            MyToast.showToast(this, "没有找到求解器！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code myGameView.onActivityResult(requestCode == 1, ...)} 的等价物：
     * 求解器把答案放在 {@code extra "SOLUTION"} 里回传。
     *
     * <p>PC 上目前没有求解器会回调到这里，但这条回路按原版语义保留：
     * 成功 → {@code formatPath(solution, false)} + Toast「答案已经载！」+ {@code m_imPort_YASS = "[YASS]"}；
     * 失败 → Toast「未能完成求解！」。
     *
     * @param solution 求解器回传的动作串（{@code RESULT_OK} 时才有意义）
     * @param ok       {@code resultCode == RESULT_OK}
     */
    void onSolverResult(String solution, boolean ok) {
        if (ok) {
            formatPath(solution, false);
            MyToast.showToast(this, "答案已经载！", MyToast.LENGTH_SHORT);
            m_imPort_YASS = "[YASS]";
        } else {
            MyToast.showToast(this, "未能完成求解！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code R.id.player_IN}「导入」：进入 {@link myActGMView}（动作管理）录入动作。
     * 原版还把 {@code LOCAL}（{@code myMaps.getLocale(state.m_cArray)}）塞进 Bundle，
     * 但 {@code myActGMView} 里接收那行是**注释掉的** —— 所以不用传。
     */
    void onImport() {
        prepareImport();
        new myActGMView(this, bt_BK.isChecked()).setVisible(true);
        processImportedAction();
    }

    /** 「导入」在开窗之前的簿记（原版 {@code player_IN} 分支的前半段）。 */
    void prepareImport() {
        setACT(false);                                  // 非录制模式
        myMaps.m_ActionIsRedy = false;
        if (bt_BK.isChecked())
            myMaps.m_nRecording_Bggin2 = state.m_lstMovUnDo2.size();  // 逆推录制起始点
        else
            myMaps.m_nRecording_Bggin = state.m_lstMovUnDo.size();    // 正推录制起始点
    }

    /**
     * 原版 {@code myGameView.onStart()} 中 {@code m_ActionIsRedy} 分支。
     *
     * <p>PC 的动作管理窗口是模态对话框，所以在它关闭后由 {@link #onImport()} 消费待执行动作；
     * 仅在 {@code myActGMView} 中设置全局标记不会自动运行动作。
     */
    void processImportedAction() {
        if (!myMaps.m_ActionIsRedy) return;
        if (myMaps.curMap == null || mMap == null) {
            myMaps.m_ActionIsRedy = false;
            return;
        }

        mMap.m_lGoto = false;
        mMap.m_lGoto2 = false;
        mMap.m_lParityBrightnessShade = false;

        try {
            if (bt_BK.isChecked()) {
                executeImportedBackwardAction();
            } else {
                executeImportedForwardAction();
            }
        } finally {
            myMaps.m_ActionIsRedy = false;
            mMap.invalidate();
            state.m_bBusing = false;
            if (!m_imPort_YASS.toLowerCase().contains("yass")
                    && !m_imPort_YASS.contains("导入")) {
                m_imPort_YASS = "[导入]";
            }
        }
    }

    /** 原版逆推导入：按可选坐标定位仓管员，再执行首行 LURD。 */
    private void executeImportedBackwardAction() {
        String firstLine = myMaps.sAction.length > 0 ? myMaps.sAction[0] : "";
        int row = -1;
        int col = -1;
        int open = firstLine.indexOf('[');
        int close = firstLine.indexOf(']');
        if (open >= 0 && close >= 0) {
            try {
                String[] xy = firstLine.substring(open + 1, close).split(",");
                row = Integer.parseInt(xy[1].trim()) - 1;   // 允许 "[19, 18]" 的逗号后空格
                col = Integer.parseInt(xy[0].trim()) - 1;
                if (row < 0 || col < 0 || row >= myMaps.curMap.Rows || col >= myMaps.curMap.Cols) {
                    row = -1;
                    col = -1;
                }
            } catch (RuntimeException ignored) {
                row = -1;
                col = -1;
            }
        }

        boolean hasPlayer = false;
        if (myMaps.m_ActionIsPos) {
            if (!isBackwardPlayerPositionValid()) {
                hasPlayer = placeBackwardPlayer(row, col);
            } else {
                hasPlayer = true;
            }
        } else if (row >= 0 && col >= 0) {
            if (isBackwardPlayerPositionValid()) {
                levelReset(true);
                removeBackwardPlayer();
            }
            state.m_nRow2 = row;
            state.m_nCol2 = col;
            state.m_nRow0 = row;
            state.m_nCol0 = col;
            hasPlayer = placeBackwardPlayer(row, col);
        } else if (isBackwardPlayerPositionValid()) {
            char player = state.bk_cArray[state.m_nRow2][state.m_nCol2];
            hasPlayer = player == '+' || player == '@';
        }

        if (hasPlayer) {
            doACT(firstLine);
        } else {
            MyToast.showToast(this, "没有仓管员或其位置无效！", MyToast.LENGTH_SHORT);
        }
    }

    private boolean isBackwardPlayerPositionValid() {
        return state.m_nRow2 >= 0 && state.m_nCol2 >= 0
                && state.m_nRow2 < myMaps.curMap.Rows && state.m_nCol2 < myMaps.curMap.Cols;
    }

    private boolean placeBackwardPlayer(int row, int col) {
        if (row < 0 || col < 0 || row >= myMaps.curMap.Rows || col >= myMaps.curMap.Cols) return false;
        state.m_nRow2 = row;
        state.m_nCol2 = col;
        state.m_nRow0 = row;
        state.m_nCol0 = col;
        if (state.bk_cArray[row][col] == '.') {
            state.bk_cArray[row][col] = '+';
            return true;
        }
        if (state.bk_cArray[row][col] == '-') {
            state.bk_cArray[row][col] = '@';
            return true;
        }
        return false;
    }

    private void removeBackwardPlayer() {
        if (state.bk_cArray[state.m_nRow2][state.m_nCol2] == '+') {
            state.bk_cArray[state.m_nRow2][state.m_nCol2] = '.';
        } else if (state.bk_cArray[state.m_nRow2][state.m_nCol2] == '@') {
            state.bk_cArray[state.m_nRow2][state.m_nCol2] = '-';
        }
    }

    /** 原版正推导入：保留当前点/关卡初态语义，并交给既有宏任务执行器。 */
    private void executeImportedForwardAction() {
        if (myMaps.isMacro(myMaps.sAction)) {
            if (myMaps.sAction.length > 0 && !myMaps.sAction[0].isEmpty()
                    && myMaps.sAction[0].charAt(0) == '=') {
                myMaps.m_ActionIsPos = false;
            } else {
                myMaps.m_ActionIsPos = true;
            }
            if (myMaps.m_ActionIsPos) {
                state.m_lstMovedHistory.clear();
                Iterator<Byte> it = state.m_lstMovUnDo.descendingIterator();
                while (it.hasNext()) {
                    state.m_lstMovedHistory.offer(it.next());
                }
            }
        }

        if (!myMaps.m_ActionIsPos) levelReset(false);

        m_nMacro_Row = state.m_nRow;
        m_nMacro_Col = state.m_nCol;
        mMap.myMacro.clear();
        mMap.myMacro.add(0);
        mMap.myMacroInf = "";
        myMaps.isMacroDebug = false;
        StopMicro();
        mMicroTask = new RunMicroTask(this);
        mMicroTask.execute(0, myMaps.sAction.length - 1);
        if (!state.m_lstMovReDo.isEmpty()) state.m_lstMovReDo.clear();
    }

    /**
     * 原版 {@code R.id.player_load}「打开状态」。
     *
     * <p>「宏」调试中时，先弹一个「关闭调试」对话框问是否把关卡退回宏打开前的状态，
     * 确认后才继续 {@link #onOpenState2()}（原版 {@code setCancelable(false)}，取消不清调试态）。
     */
    void onOpenState() {
        if (!myMaps.isMacroDebug) {
            onOpenState2();
            return;
        }
        final JCheckBox isBack = HoloContent.check("关卡回到该“宏”打开前的状态", true);
        HoloAlertDialog dlg = HoloAlertDialog.create(this, "关闭调试");
        dlg.setContentView(isBack);
        dlg.addButton("取消", null);
        dlg.addButton("确定", () -> {
            if (isBack.isSelected()) levelReset(false);   // 回到宏打开前的关卡状态（正推复位）
            myMaps.isMacroDebug = false;
            mMap.invalidate();
            onOpenState2();
        });
        dlg.setVisible(true);
    }

    /** 原版 {@code myGameView.myOpenState()}：载入状态列表 → 修正解关标记 → 打开 {@link myStateBrow}。 */
    void onOpenState2() {
        loadStateList();
        myMaps.m_StateIsRedy = false;
        myStateBrow.my_Sort = 0;   // 每次，默认移动优先排序答案
        new myStateBrow().setVisible(true);
    }

    /**
     * 状态列表排序：按保存时间**倒序**（最后保存的在最前面）。
     * 原版 {@code myGameView.myOpenState()} 里的匿名 {@code Comparator}。
     */
    static final Comparator<state_Node> STATE_TIME_DESC = new Comparator<state_Node>() {
        public int compare(state_Node o1, state_Node o2) {
            return o2.time.compareTo(o1.time);
        }
    };

    /** {@link #onOpenState2()} 里「读数据」的部分（与开窗分开，便于测试）。 */
    void loadStateList() {
        mySQLite.m_SQL.load_StateList(myMaps.curMap.Level_id, myMaps.curMap.key);
        // 仅修正本关卡是否解关（按关卡 id）；试推时（Num <= 0）不做修正
        if (myMaps.curMap.Num > 0) {
            myMaps.curMap.Solved = (myMaps.mState2.size() > 0);   // 修正关卡预览图之是否有答案
            mySQLite.m_SQL.Set_L_Solved(myMaps.curMap.Level_id, myMaps.curMap.Solved ? 1 : 0, true);
        }
        // 状态按保存时间排序，最后保存的在最前面
        Collections.sort(myMaps.mState1, STATE_TIME_DESC);
    }

    /** 原版 {@code player_EX} 打包给 {@link myExport} 的数据（与开窗分开，便于测试）。 */
    static class ExportData {
        String xsb;        // 关卡初态
        String lurd;       // Lurd 动作
        String local;      // 关卡正推现场
        String local8;     // 关卡正推现场 -- 旋转
        boolean isAns;     // 是否答案（正推已达答案）
        int gifStart;
        boolean[] rule;    // 需要显示标尺的格子
        short[] boxNum;    // 迷宫箱子编号（人为）
        String importYass;
    }

    /**
     * 原版 {@code R.id.player_EX}「导出」：把当前关卡的
     * 初态（XSB）/ 正推现场 / 正推现场-旋转 / Lurd 动作，连同标尺与箱子编号打包给 {@link myExport}。
     */
    void onExport() {
        ExportData d = buildExportData();
        new myExport(d.xsb, d.lurd, d.local, d.local8, d.isAns, d.gifStart, d.rule, d.boxNum, d.importYass)
                .setVisible(true);
    }

    /** 构造 {@link #onExport()} 要传给 {@link myExport} 的全部数据。 */
    ExportData buildExportData() {
        ExportData d = new ExportData();
        StringBuilder s_XSB = new StringBuilder();   // 关卡初态
        StringBuilder s_XSB1 = new StringBuilder();  // 关卡正推现场
        StringBuilder s_XSB8 = new StringBuilder();  // 关卡正推现场 -- 旋转
        StringBuilder s_Lurd = new StringBuilder();  // Lurd
        boolean isANS = (state.m_nGoals_OK == state.m_nGoals);

        // 关卡初态
        s_XSB.append(myMaps.curMap.Map).append("\nTitle: ").append(myMaps.curMap.Title)
                .append("\nAuthor: ").append(myMaps.curMap.Author);
        if (!myMaps.curMap.Comment.trim().isEmpty()) {
            s_XSB.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:");
        }

        // 关卡正推现场
        char ch;
        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                ch = state.m_cArray[i][j];  // 正推迷宫
                if (myMaps.m_Sets[13] == 1) {  // “互动双推”模式
                    if (state.bk_cArray[i][j] == '$' || state.bk_cArray[i][j] == '*') {  // 只有逆推地图中的箱子将成为正推的目标点位
                        switch (ch) {
                            case '-': ch = '.'; break;
                            case '$': ch = '*'; break;
                            case '@': ch = '+';
                        }
                    } else {
                        switch (ch) {
                            case '.': ch = '-'; break;
                            case '*': ch = '$'; break;
                            case '+': ch = '@';
                        }
                    }
                }
                s_XSB1.append(ch);
            }
            if (i < myMaps.curMap.Rows - 1) s_XSB1.append('\n');
        }

        // 关卡正推现场 -- 旋转（m_nTrun 为偶数时行优先，奇数时列优先）
        char ch2;
        if (myMaps.m_nTrun % 2 == 0) {
            for (int i = 0; i < myMaps.curMap.Rows; i++) {
                for (int j = 0; j < myMaps.curMap.Cols; j++) {
                    switch (myMaps.m_nTrun) {
                        case 0: ch = state.m_cArray[i][j];
                                ch2 = state.bk_cArray[i][j]; break;
                        case 2: ch = state.m_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j];
                                ch2 = state.bk_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j]; break;
                        case 4: ch = state.m_cArray[i][myMaps.curMap.Cols - 1 - j];
                                ch2 = state.bk_cArray[i][myMaps.curMap.Cols - 1 - j]; break;
                        case 6: ch = state.m_cArray[myMaps.curMap.Rows - 1 - i][j];
                                ch2 = state.bk_cArray[myMaps.curMap.Rows - 1 - i][j]; break;
                        default: ch = '_'; ch2 = '_'; break;
                    }
                    s_XSB8.append(dualPush(ch, ch2));
                }
                if (i < myMaps.curMap.Rows - 1) s_XSB8.append('\n');
            }
        } else {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                for (int i = 0; i < myMaps.curMap.Rows; i++) {
                    switch (myMaps.m_nTrun) {
                        case 1: ch = state.m_cArray[myMaps.curMap.Rows - 1 - i][j];
                                ch2 = state.bk_cArray[myMaps.curMap.Rows - 1 - i][j]; break;
                        case 3: ch = state.m_cArray[i][myMaps.curMap.Cols - 1 - j];
                                ch2 = state.bk_cArray[i][myMaps.curMap.Cols - 1 - j]; break;
                        case 5: ch = state.m_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j];
                                ch2 = state.bk_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j]; break;
                        case 7: ch = state.m_cArray[i][j];
                                ch2 = state.bk_cArray[i][j]; break;
                        default: ch = '_'; ch2 = '_'; break;
                    }
                    s_XSB8.append(dualPush(ch, ch2));
                }
                if (j < myMaps.curMap.Cols - 1) s_XSB8.append('\n');
            }
        }

        char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
        byte t;
        Iterator<Byte> myItr;

        // 解关答案
        if (!state.m_lstMovUnDo.isEmpty()) {
            myItr = state.m_lstMovUnDo.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s_Lurd.append(Move[t - 1]);
            }
        }
        if (!isANS) {  // 若正推已经是答案，则不再导出逆推动作
            if (!state.m_lstMovUnDo2.isEmpty()) {
                if (s_Lurd.length() > 0) s_Lurd.append('\n');
                s_Lurd.append('[').append(state.m_nCol0 + 1).append(',').append(state.m_nRow0 + 1).append(']');  // （x, y）-- 先列后行
                myItr = state.m_lstMovUnDo2.iterator();
                while (myItr.hasNext()) {
                    t = myItr.next();
                    s_Lurd.append(Move[t - 1]);
                }
            }
        }

        boolean[] my_Rule = new boolean[myMaps.curMap.Cols * myMaps.curMap.Rows];
        short[] my_BoxNum = new short[state.m_nGoals];  // 按箱子数定义，记录“自动箱子编号”，以方便转换“人工箱子编号”
        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                my_Rule[myMaps.curMap.Cols * i + j] = state.mark44[i][j];
                if (state.m_iBoxNum2[i][j] > 0 && (state.m_cArray[i][j] == '$' || state.m_cArray[i][j] == '*')) {
                    // 自动箱子编号与人工箱子编号建立关联
                    my_BoxNum[state.m_iBoxNum2[i][j] - 1] = myMaps.m_bBianhao ? state.m_iBoxNum2[i][j] : state.m_iBoxNum[i][j];
                }
            }
        }

        d.xsb = s_XSB.toString();
        d.lurd = s_Lurd.toString();
        d.local = s_XSB1.toString();
        d.local8 = s_XSB8.toString();
        d.isAns = isANS;
        d.gifStart = state.m_Gif_Start;
        d.rule = my_Rule;
        d.boxNum = my_BoxNum;
        d.importYass = m_imPort_YASS;
        return d;
    }

    /** 「互动双推」（{@code myMaps.m_Sets[13] == 1}）时把正推格按逆推格是否为箱子做点/箱/人互换。 */
    private static char dualPush(char ch, char ch2) {
        if (myMaps.m_Sets[13] != 1) return ch;
        if (ch2 == '$' || ch2 == '*') {
            switch (ch) {
                case '-': return '.';
                case '$': return '*';
                case '@': return '+';
            }
        } else {
            switch (ch) {
                case '.': return '-';
                case '*': return '$';
                case '+': return '@';
            }
        }
        return ch;
    }

    // ================================================================ 测试钩子

    /** 供测试读取「更多」选项菜单（调用 {@link #openOptionsMenu()} 之后才有值）。 */
    JPopupMenu optionsMenuForTest() {
        return optionsMenu;
    }

    /** 供测试读取选项菜单的条目文字（不含分隔线）。 */
    java.util.List<String> optionsMenuTitlesForTest() {
        java.util.List<String> titles = new java.util.ArrayList<String>();
        if (optionsMenu == null) return titles;
        for (Component c : optionsMenu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) titles.add(((HoloPopupMenu.Row) c).getText());
        }
        return titles;
    }

    /**
     * 原版 {@code R.id.player_Setup1}「场景设置」（{@code myGameView.java:3554}）。
     *
     * <p>原版是 {@code setSingleChoiceItems(m_menu2, -1, …)} 的<b>菜单式</b>单选框：
     * 单击某一项立刻分派到子对话框，而框本身<b>不关</b>；按钮栏只有一个
     * {@code setPositiveButton("返回", …)}，点了才 {@code BoxMan.saveSets()} 并关闭。
     * 只有「奇偶格位明暗度」在分支里自己 {@code dialog.dismiss()}。
     *
     * <p>PC 端原先用 {@code JOptionPane.showInputDialog} 的下拉框顶替 —— 既是浅色 PC 外观，
     * 也丢掉了「子对话框压在父框上、父框保持不动」的层次。
     *
     * <p>三个子框的回滚口径照原版：
     * <ul>
     *   <li><b>移动速度</b>：{@code onClick} 里就写 {@code m_Sets[10]}，没有「取消」按钮
     *       （只有「确定」→ {@code saveSets()}），所以<b>不回滚</b>。PC 上靠 ESC 退出。</li>
     *   <li><b>皮肤 / 背景</b>：{@code onClick} 里立刻应用（即时预览），
     *       「取消」回滚到打开时的那一项，且背景的「取消」分支<b>不</b>调 {@code setColorBK()}。</li>
     * </ul>
     */
    private void showSetup1Dialog() {
        final String[] menu2 = {"速度设置", "更换皮肤", "更换背景", "奇偶格位明暗度"};
        final HoloChoiceDialog dlg = new HoloChoiceDialog(this, "设置", null, menu2);
        dlg.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int which = dlg.list.getSelectedIndex();
            if (which < 0) return;
            switch (which) {
                case 0:   //速度设置
                    showSpeedDialog();
                    break;
                case 1:   //更换皮肤
                    showSkinDialog();
                    break;
                case 2:   //更换背景
                    showBkPicDialog();
                    break;
                case 3:   //奇偶格位明暗度条
                    dlg.dispose();          // 原版 dialog.dismiss()
                    mMap.m_lParityBrightnessShade = true;
                    mMap.invalidate();
                    break;
                default:
                    break;
            }
        });
        // 原版按钮栏只有一个「返回」：点了才保存设置
        dlg.addButton("返回", () -> {
            dlg.dispose();
            saveSets();
        });
        dlg.setVisible(true);
    }

    /** 「设置 → 速度设置」：原版 {@code myGameView.java:3567}。只有「确定」，选中项即写回。 */
    private void showSpeedDialog() {
        final HoloChoiceDialog speed = new HoloChoiceDialog(this, "移动速度", null, m_sSleep);
        if (myMaps.m_Sets[10] >= 0 && myMaps.m_Sets[10] < m_sSleep.length) {
            speed.list.setSelectedIndex(myMaps.m_Sets[10]);   // 原版初始下标就是 m_Sets[10]
        }
        speed.addButton("确定", () -> {
            int i = speed.list.getSelectedIndex();
            speed.dispose();
            if (i >= 0) myMaps.m_Sets[10] = i;
            saveSets();
        });
        speed.setVisible(true);
    }

    /** 「设置 → 更换皮肤」：原版 {@code myGameView.java:3591}。选中即预览，「取消」回滚。 */
    private void showSkinDialog() {
        skinList();
        if (myMaps.mFile_List1.isEmpty()) {
            MyToast.showToast(this, "没找到皮肤图片文档！", MyToast.LENGTH_SHORT);
            return;
        }
        // 原版 m_nItemSelect：找到当前皮肤的下标，找不到就用 0
        final int init = Math.max(0, myMaps.mFile_List1.indexOf(myMaps.skin_File));
        final HoloChoiceDialog skin = new HoloChoiceDialog(this, "皮肤", null,
                myMaps.mFile_List1.toArray(new String[0]));
        skin.list.setSelectedIndex(init);                     // 必须在挂监听器之前
        skin.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = skin.list.getSelectedIndex();
            if (i < 0) return;
            myMaps.skin_File = myMaps.mFile_List1.get(i);
            myMaps.loadSkins();
            mMap.invalidate();
        });
        skin.addButton("取消", () -> {
            skin.dispose();
            myMaps.iskinChange = false;
            myMaps.skin_File = myMaps.mFile_List1.get(init);  //选择的文档
            myMaps.loadSkins();
            mMap.invalidate();
        });
        skin.addButton("确定", () -> {
            skin.dispose();
            myMaps.iskinChange = true;
        });
        skin.setVisible(true);
    }

    /** 「设置 → 更换背景」：原版 {@code myGameView.java:3629}。选中即预览，「取消」回滚。 */
    private void showBkPicDialog() {
        bkPicList();
        if (myMaps.mFile_List2.isEmpty()) {
            MyToast.showToast(this, "没找到图片文档", MyToast.LENGTH_SHORT);
            return;
        }
        final int init = Math.max(0, myMaps.mFile_List2.indexOf(myMaps.bk_Pic));
        final HoloChoiceDialog bk = new HoloChoiceDialog(this, "背景图片", null,
                myMaps.mFile_List2.toArray(new String[0]));
        bk.list.setSelectedIndex(init);                       // 必须在挂监听器之前
        bk.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = bk.list.getSelectedIndex();
            if (i < 0) return;
            myMaps.bk_Pic = myMaps.mFile_List2.get(i);        //选择的文档
            myMaps.loadBKPic();
            if (i == 0) {                                     // 使用背景色（bkPicList 把它放在第 0 项）
                setColorBK();
            } else if (myMaps.bkPict != null) {               // 使用背景图片
                mMap.w_bkPic = myMaps.bkPict.getWidth();
                mMap.h_bkPic = myMaps.bkPict.getHeight();
                mMap.w_bkNum = myMaps.m_nWinWidth / mMap.w_bkPic + 1;
                mMap.h_bkNum = myMaps.m_nWinHeight / mMap.h_bkPic + 1;
            }
            mMap.invalidate();
        });
        bk.addButton("取消", () -> {
            bk.dispose();
            myMaps.bk_Pic = myMaps.mFile_List2.get(init);     //选择的文档
            myMaps.loadBKPic();
            // ⚠️ 原版「取消」分支只重算背景图尺寸，**不**调 setColorBK()（原版的怪癖，照搬）
            if (init > 0 && myMaps.bkPict != null) {
                mMap.w_bkPic = myMaps.bkPict.getWidth();
                mMap.h_bkPic = myMaps.bkPict.getHeight();
                mMap.w_bkNum = myMaps.m_nWinWidth / mMap.w_bkPic + 1;
                mMap.h_bkNum = myMaps.m_nWinHeight / mMap.h_bkPic + 1;
            }
            mMap.invalidate();
        });
        bk.addButton("确定", bk::dispose);
        bk.setVisible(true);
    }

    private void showSetup2Dialog() {
        JDialog dlg = new JDialog(this, "开关选项", true);
        dlg.setLayout(new BorderLayout());

        // 原版这里是 AlertDialog(THEME_HOLO_DARK) + setMultiChoiceItems()：
        // 面板是 dialog_full_holo_dark 的 #282828，正文 primary_text_holo_dark（浅色）。
        // 用户要求**保留现有的两列网格 + 单个「确定」布局**，只把配色对齐 Holo，
        // 免得这个框在一片深色界面里突兀地发白。
        dlg.getContentPane().setBackground(HoloAlertDialog.PANEL_BG);
        if (dlg.getContentPane() instanceof JComponent) {
            ((JComponent) dlg.getContentPane()).setOpaque(true);
        }

        JPanel pnl = new JPanel(new GridLayout(0, 2, 5, 5));
        pnl.setBackground(HoloAlertDialog.PANEL_BG);
        pnl.setOpaque(true);
        pnl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JCheckBox chk0 = new JCheckBox("双击箱子编号", myMaps.m_Sets[41] == 1);
        JCheckBox chk1 = new JCheckBox("自动箱子编号", myMaps.m_bBianhao);
        JCheckBox chk2 = new JCheckBox("标尺", myMaps.m_bBiaochi);
        JCheckBox chk3 = new JCheckBox("标尺不随关卡旋转", myMaps.m_Sets[9] == 1);
        JCheckBox chk4 = new JCheckBox("区分奇偶地板格", myMaps.m_Sets[38] == 1);
        JCheckBox chk5 = new JCheckBox("死锁嗅探", myMaps.m_Sets[11] == 1);
        JCheckBox chk6 = new JCheckBox("可达提示", myMaps.m_Sets[8] == 1);
        JCheckBox chk7 = new JCheckBox("仓管员转向动画", myMaps.m_Sets[27] == 1);
        JCheckBox chk8 = new JCheckBox("长按点位提示关联网", myMaps.m_Sets[3] == 1);
        JCheckBox chk9 = new JCheckBox("自动加载最新状态", myMaps.m_Sets[37] == 1);
        // 原版菜单项文字是「定位双推」；「禁用逆推目标点」只是原版代码里的行内注释
        // 与 INI 键名（见 BoxMan.java 的 file.get/set("操作", "禁用逆推目标点", ...)），不是 UI 文案。
        JCheckBox chk10 = new JCheckBox("定位双推", myMaps.m_Sets[32] == 1);
        JCheckBox chk11 = new JCheckBox("允许穿越", myMaps.m_Sets[17] == 1);
        JCheckBox chk12 = new JCheckBox("单步进退", myMaps.m_Sets[23] == 1);
        JCheckBox chk13 = new JCheckBox("进度条", (!bt_BK.isChecked() && mMap.m_lGoto) || (bt_BK.isChecked() && mMap.m_lGoto2));
        JCheckBox chk14 = new JCheckBox("自动爬阶梯", myMaps.m_Sets[29] == 1);
        JCheckBox chk15 = new JCheckBox("演示时仅推动", myMaps.m_Sets[28] == 1);
        JCheckBox chk16 = new JCheckBox("显示当前时间提示", myMaps.m_Sets[25] == 1);

        pnl.add(chk0); pnl.add(chk1);
        pnl.add(chk2); pnl.add(chk3);
        pnl.add(chk4); pnl.add(chk5);
        pnl.add(chk6); pnl.add(chk7);
        pnl.add(chk8); pnl.add(chk9);
        pnl.add(chk10); pnl.add(chk11);
        pnl.add(chk12); pnl.add(chk13);
        pnl.add(chk14); pnl.add(chk15);
        pnl.add(chk16);

        // 深色配色：浅色文字 + 透明底（FlatLaf 的复选框图标按前景色绘制，浅色前景才看得见）
        for (Component c : pnl.getComponents()) {
            if (c instanceof JCheckBox) {
                JCheckBox cb = (JCheckBox) c;
                cb.setOpaque(false);
                cb.setForeground(HoloContent.TEXT);
                cb.setFocusPainted(false);
            }
        }

        JPanel btnPnl = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPnl.setBackground(HoloAlertDialog.PANEL_BG);
        btnPnl.setOpaque(true);
        JButton btnOk = new HoloButton("确定");
        btnOk.addActionListener(e -> {
            myMaps.m_Sets[41] = chk0.isSelected() ? 1 : 0;
            myMaps.m_bBianhao = chk1.isSelected();
            myMaps.m_bBiaochi = chk2.isSelected();
            myMaps.m_Sets[9] = chk3.isSelected() ? 1 : 0;
            myMaps.m_Sets[38] = chk4.isSelected() ? 1 : 0;
            myMaps.m_Sets[11] = chk5.isSelected() ? 1 : 0;
            myMaps.m_Sets[8] = chk6.isSelected() ? 1 : 0;
            myMaps.m_Sets[27] = chk7.isSelected() ? 1 : 0;
            myMaps.m_Sets[3] = chk8.isSelected() ? 1 : 0;
            myMaps.m_Sets[37] = chk9.isSelected() ? 1 : 0;
            myMaps.m_Sets[32] = chk10.isSelected() ? 1 : 0;
            myMaps.m_Sets[17] = chk11.isSelected() ? 1 : 0;
            myMaps.m_Sets[23] = chk12.isSelected() ? 1 : 0;
            if (chk13.isSelected()) {
                if (bt_BK.isChecked()) mMap.m_lGoto2 = true;
                else mMap.m_lGoto = true;
            } else {
                if (bt_BK.isChecked()) mMap.m_lGoto2 = false;
                else mMap.m_lGoto = false;
            }
            myMaps.m_Sets[29] = chk14.isSelected() ? 1 : 0;
            myMaps.m_Sets[28] = chk15.isSelected() ? 1 : 0;
            myMaps.m_Sets[25] = chk16.isSelected() ? 1 : 0;

            saveSets();
            mMap.invalidate();
            dlg.dispose();
        });
        btnPnl.add(btnOk);

        dlg.add(pnl, BorderLayout.CENTER);
        dlg.add(btnPnl, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    public void setColorBK() {
        Color c = JColorChooser.showDialog(this, "选择背景色", new Color(myMaps.m_Sets[4], true));
        if (c != null) {
            myMaps.m_Sets[4] = c.getRGB();
            myMaps.bk_Pic = "使用背景色";
            mMap.invalidate();
            saveSets();
        }
    }

    public void setMenuVisible() {
        if (main_bottom.isVisible()) {
            main_bottom.setVisible(false);
            mMap.m_nArenaTop = 0;
        } else {
            main_bottom.setVisible(true);
            mMap.m_nArenaTop = getHeight() * 27 / 640;
        }
        revalidate();
        mMap.invalidate();
    }

    public void boxNoMoved() {
        if (bt_BK.isChecked()) return;

        int row = state.m_nRow3;
        int col = state.m_nCol3;
        mMap.m_boxNoMoved = true;
        mMap.m_boxNoUsed = true;
        int dir = 0;

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                if (state.mark7[i][j]) state.mark11[i][j] = false;
                else state.mark11[i][j] = true;
                if (state.mark8[i][j]) state.mark12[i][j] = false;
                else state.mark12[i][j] = true;
            }
        }

        byte mDir;
        int t = -1;
        Iterator<Byte> myItr = state.m_lstMovUnDo.iterator();
        while (myItr.hasNext()) {
            mDir = myItr.next();
            switch (mDir) {
                case 1:
                case 5:
                    col--;
                    t = 0;
                    break;
                case 2:
                case 6:
                    row--;
                    t = 2;
                    break;
                case 3:
                case 7:
                    col++;
                    t = 1;
                    break;
                case 4:
                case 8:
                    row++;
                    t = 3;
                    break;
            }

            if (mDir > 4 && t >= 0 && !state.mark11[row - dr4[t] * dir][col - dc4[t] * dir]) {
                state.mark11[row - dr4[t] * dir][col - dc4[t] * dir] = true;
                state.mark12[row - dr4[t] * dir][col - dc4[t] * dir] = true;
            }
            state.mark12[row][col] = true;
        }
    }

    public void boxCanCome(int row, int col) {
        boolean[][] mark, mark9;
        char[][] level;
        int nRow, nCol;

        if (bt_BK.isChecked()) {
            level = state.bk_cArray;
            mark = state.mPathfinder.mark4;
            mark9 = state.mark12;
            mMap.m_boxCanCome2 = true;
            nRow = state.m_nRow2;
            nCol = state.m_nCol2;
        } else {
            level = state.m_cArray;
            mark = state.mPathfinder.mark3;
            mark9 = state.mark11;
            mMap.m_boxCanCome = true;
            nRow = state.m_nRow;
            nCol = state.m_nCol;
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                mark9[i][j] = false;
            }
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                if (level[i][j] == '$' || level[i][j] == '*') {
                    state.mPathfinder.FindBlock(bt_BK.isChecked(), level, i, j);
                    state.mPathfinder.boxReachable(bt_BK.isChecked(), i, j, nRow, nCol);
                    mark9[i][j] = mark[row][col];
                }
            }
        }
        mark9[row][col] = true;
    }

    public void boxCanMove() {
        char[][] level;
        boolean[][] mark, mark9;
        boolean flg = bt_BK.isChecked();

        if (flg) {
            level = state.bk_cArray;
            mark = state.mPathfinder.mark2;
            mark9 = state.mark12;
            mMap.m_boxCanMove2 = true;
        } else {
            level = state.m_cArray;
            mark = state.mPathfinder.mark1;
            mark9 = state.mark11;
            mMap.m_boxCanMove = true;
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                mark9[i][j] = false;
            }
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                if (level[i][j] == '$' || level[i][j] == '*') {
                    for (int k = 0; 4 > k; k++) {
                        try {
                            if (flg) {
                                if (mark[i + dr4[k]][j + dc4[k]] && (level[i + dr4[k] * 2][j + dc4[k] * 2] == '-' || level[i + dr4[k] * 2][j + dc4[k] * 2] == '.' || level[i + dr4[k] * 2][j + dc4[k] * 2] == '@' || level[i + dr4[k] * 2][j + dc4[k] * 2] == '+')) {
                                    mark9[i][j] = true;
                                    break;
                                }
                            } else {
                                if (mark[i + dr4[k]][j + dc4[k]] && (level[i - dr4[k]][j - dc4[k]] == '-' || level[i - dr4[k]][j - dc4[k]] == '.' || level[i - dr4[k]][j - dc4[k]] == '@' || level[i - dr4[k]][j - dc4[k]] == '+')) {
                                    mark9[i][j] = true;
                                    break;
                                }
                            }
                        } catch (Throwable ex) { }
                    }
                }
            }
        }
    }

    public boolean FindPath(int k, int l, boolean flg) {
        LinkedList<Byte> path;
        LinkedList<Byte> pathByteList;

        if (flg) {
            path = state.mPathfinder.manTo(true, state.bk_cArray, state.m_nRow2, state.m_nCol2, k, l);
            pathByteList = state.m_lstMovReDo2;
        } else {
            path = state.mPathfinder.manTo(false, state.m_cArray, state.m_nRow, state.m_nCol, k, l);
            pathByteList = state.m_lstMovReDo;
        }

        if (!path.isEmpty()) {
            pathByteList.clear();
            while (!path.isEmpty()) {
                pathByteList.offer(path.removeFirst());
            }
            return true;
        } else return false;
    }

    public void formatPath(String strPath, boolean flg) {
        int Len = strPath.length();
        if (Len > 0) {
            LinkedList<Byte> pathByteList = flg ? state.m_lstMovReDo2 : state.m_lstMovReDo;
            pathByteList.clear();
            for (int t = 0; t < Len; t++) {
                switch (strPath.charAt(t)) {
                    case 'l': pathByteList.offerFirst((byte)1); break;
                    case 'u': pathByteList.offerFirst((byte)2); break;
                    case 'r': pathByteList.offerFirst((byte)3); break;
                    case 'd': pathByteList.offerFirst((byte)4); break;
                    case 'L': pathByteList.offerFirst((byte)5); break;
                    case 'U': pathByteList.offerFirst((byte)6); break;
                    case 'R': pathByteList.offerFirst((byte)7); break;
                    case 'D': pathByteList.offerFirst((byte)8); break;
                }
            }
        }
    }

    public void StopMicro() {
        if (mMicroTask != null) {
            mMicroTask.cancel(true);
            mMicroTask = null;
        }
    }

    public void myStop() {
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
        StopMicro();
        if (mClockTimer != null) {
            mClockTimer.stop();
            mClockTimer = null;
        }
        if (myTimer1 != null) { myTimer1.stop(); myTimer1 = null; }
        if (myTimer2 != null) { myTimer2.stop(); myTimer2 = null; }
        if (myTimer3 != null) { myTimer3.stop(); myTimer3 = null; }
        if (myTimer4 != null) { myTimer4.stop(); myTimer4 = null; }
        if (mMap != null) mMap.d_Moves = mMap.m_PicWidth;
    }

    public void saveSets() {
        IniFile file = new IniFile();
        file.set("常规", "当前关卡集组别", myMaps.m_Sets[0]);
        file.set("常规", "当前关卡集", myMaps.m_Sets[1]);
        file.set("常规", "预览时是否显示关卡标题", myMaps.m_Sets[2]);
        file.set("常规", "是否标识出重复关卡", myMaps.m_Sets[12]);
        file.set("常规", "浏览时每行的图标数", myMaps.m_Sets[33]);
        file.set("常规", "浏览时每行的图标默认数", myMaps.m_Sets[34]);

        file.set("界面", "背景色", myMaps.m_Sets[4]);
        file.set("界面", "皮肤", myMaps.skin_File);
        file.set("界面", "背景图片", myMaps.bk_Pic);
        file.set("界面", "背景时间", myMaps.m_Sets[25]);

        file.set("速度", "瞬移状态", myMaps.m_Sets[6]);
        file.set("速度", "移动速度", myMaps.m_Sets[10]);

        file.set("操作", "是否提示死锁", myMaps.m_Sets[11]);
        file.set("操作", "长按点位提示关联网", myMaps.m_Sets[3]);
        file.set("操作", "显示可达提示", myMaps.m_Sets[8]);
        file.set("操作", "仓管员转向动画", myMaps.m_Sets[27]);
        file.set("操作", "禁用全屏", myMaps.m_Sets[20]);
        file.set("操作", "演示时仅推动", myMaps.m_Sets[28]);
        file.set("操作", "标尺是否同步旋转", myMaps.m_Sets[9]);
        file.set("操作", "是否允许音量键选择关卡", myMaps.m_Sets[15]);
        file.set("操作", "显示系统虚拟按键", myMaps.m_Sets[16]);
        file.set("操作", "是否允许穿越", myMaps.m_Sets[17]);
        file.set("操作", "自动爬阶梯", myMaps.m_Sets[29]);
        file.set("操作", "导出答案的注释信息", myMaps.m_Sets[30]);
        file.set("操作", "自动打开导入关卡", myMaps.m_Sets[31]);
        file.set("操作", "禁用逆推目标点", myMaps.m_Sets[32]);
        file.set("操作", "区分奇偶地板格", myMaps.m_Sets[38]);
        file.set("操作", "偶格位明暗度", myMaps.m_Sets[39]);
        file.set("操作", "奇格位明暗度", myMaps.m_Sets[40]);
        file.set("操作", "双击箱子编号", myMaps.m_Sets[41]);

        file.set("编辑", "关卡编辑中，图中标尺的字体颜色", myMaps.m_Sets[21]);
        file.set("编辑", "关卡编辑中，携带标尺的元素", myMaps.m_Sets[22]);
        file.set("编辑", "是否采用YASC绘制习惯", myMaps.m_Sets[19]);
        file.set("识别", "图像识别", myMaps.m_Sets[36]);

        file.save(new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini"));
    }

    private void saveAct(String name, String value) {
        try {
            File f = new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini");
            IniFile ini = new IniFile(f);
            ini.set("Action", name, value);
            ini.save(f);
        } catch (Throwable ex) { }
    }

    private String loadAct(String name) {
        try {
            File f = new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini");
            if (!f.exists()) return "";
            IniFile ini = new IniFile(f);
            Object val = ini.get("Action", name, "");
            return val != null ? val.toString() : "";
        } catch (Throwable ex) {
            return "";
        }
    }

    private void mLoad_Do_Macro() {
        if (bt_BK.isChecked()) {
            MyToast.showToast(this, "逆推不支持宏功能！", MyToast.LENGTH_SHORT);
        } else {
            m_nItemSelect = -1;
            mMap.m_lGoto = false;
            mMap.m_lParityBrightnessShade = false;
            myMaps.mMacroList();
            if (myMaps.mFile_List.size() > 0) {
                String[] list = myMaps.mFile_List.toArray(new String[0]);
                // 原版 myGameView.java:4575 —— AlertDialog(THEME_HOLO_DARK) 标题「选择：宏」+
                // setSingleChoiceItems(list, -1, …) + 取消/确定，setCancelable(false)。
                // 未选中就点「确定」什么都不做（原版判 m_nItemSelect > -1）—— 正是 selectThenOk 的语义。
                HoloChoiceDialog.selectThenOk(this, "选择：宏", null, list, -1, idx -> {
                    m_nItemSelect = idx;
                    if (m_nItemSelect > -1) {
                        myMaps.isMacroDebug = false;
                        myMaps.sAction = myMaps.readMacroFile(myMaps.mFile_List.get(m_nItemSelect)).split("\n|\r|\n\r|\r\n|\\|");

                        int len = myMaps.sAction.length;
                        int w, w1, w2;
                        String str;
                        for (int k = 0; k < len; k++) {
                            if (myMaps.sAction[k].trim().isEmpty()) {
                                myMaps.sAction[k] = "";
                                continue;
                            } else {
                                str = myMaps.qj2bj(myMaps.sAction[k]).trim();
                            }
                            w = str.indexOf('<');
                            if (w >= 0) {
                                w1 = str.indexOf(';');
                                if (w1 >= 0 && w1 < w) w = w1;
                                else {
                                    w2 = str.indexOf('>');
                                    w = str.indexOf(';', w2);
                                }
                            } else {
                                w = str.indexOf(';');
                            }
                            if (w > 0) {
                                myMaps.sAction[k] = str.substring(0, w).replaceAll("[\t]", " ").trim();
                            } else if (w == 0) {
                                myMaps.sAction[k] = "";
                            } else {
                                myMaps.sAction[k] = str;
                            }
                        }

                        if (!myMaps.sAction[0].isEmpty() && myMaps.sAction[0].charAt(0) == '=') {
                            myMaps.m_ActionIsPos = false;
                        } else {
                            myMaps.m_ActionIsPos = true;
                        }
                        if (myMaps.m_ActionIsPos) {
                            state.m_lstMovedHistory.clear();
                            Iterator<Byte> myItr = state.m_lstMovUnDo.descendingIterator();
                            while (myItr.hasNext()) {
                                state.m_lstMovedHistory.offer(myItr.next());
                            }
                        }
                        if (!myMaps.m_ActionIsPos) {
                            levelReset(false);
                        }

                        m_nMacro_Row = state.m_nRow;
                        m_nMacro_Col = state.m_nCol;
                        mMap.myMacro.clear();
                        mMap.myMacro.add(0);
                        myMaps.isMacroDebug = false;
                        StopMicro();
                        mMicroTask = new RunMicroTask(this);
                        mMicroTask.execute(0, myMaps.sAction.length - 1);
                        state.m_bBusing = false;
                        mMap.invalidate();
                        if (!state.m_lstMovReDo.isEmpty()) {
                            state.m_lstMovReDo.clear();
                        }
                    }
                }).setVisible(true);
            } else {
                MyToast.showToast(this, "没有可读取的“宏”文档！", MyToast.LENGTH_SHORT);
            }
        }
    }

    String[][] DIR = {
            {"l", "u", "r", "d", "L", "U", "R", "D"},
            {"d", "l", "u", "r", "D", "L", "U", "R"},
            {"r", "d", "l", "u", "R", "D", "L", "U"},
            {"u", "r", "d", "l", "U", "R", "D", "L"},
            {"r", "u", "l", "d", "R", "U", "L", "D"},
            {"d", "r", "u", "l", "D", "R", "U", "L"},
            {"l", "d", "r", "u", "L", "D", "R", "U"},
            {"u", "l", "d", "r", "U", "L", "D", "R"}};

    private void doACT(String myACT) {
        if (myMaps.m_ActionIsTrun) {
            Pattern p = Pattern.compile("l|L|r|R|u|U|d|D");
            Matcher m = p.matcher(myACT);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                if (m.group().equals("l")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][0]);
                else if (m.group().equals("u")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][1]);
                else if (m.group().equals("r")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][2]);
                else if (m.group().equals("d")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][3]);
                else if (m.group().equals("L")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][4]);
                else if (m.group().equals("U")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][5]);
                else if (m.group().equals("R")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][6]);
                else if (m.group().equals("D")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][7]);
            }
            m.appendTail(sb);
            formatPath(sb.toString(), bt_BK.isChecked());
        } else {
            formatPath(myACT, bt_BK.isChecked());
        }
        int len;
        state.m_bACT_ERROR = false;
        if (bt_BK.isChecked()) {
            len = state.m_lstMovReDo2.size();
            if (len > 0) {
                for (int k = 0; k < len; k++) reDo2();
            }
        } else {
            len = state.m_lstMovReDo.size();
            if (len > 0) {
                for (int k = 0; k < len; k++) reDo1();
            }
        }
    }

    private char my_Get_Position(String str, int cs) {
        int m = 0, n = 0;
        char chr = '!';
        boolean inLine = cs < 0;

        if (inLine) {
            if (str.charAt(0) == '@') cs = 1;
            else if (str.charAt(0) == '+') cs = 2;
            else cs = 0;
        }

        boolean bMoves = str.indexOf('~') < 0;

        if (str.indexOf(',') < 0) {
            if (cs > 0) {
                chr = ' ';
            } else {
                try {
                    m = Integer.valueOf(str.replaceAll("[^0-9]", " ").trim()) - 1;
                    str = str.replaceAll("[^a-zA-Z]", " ").toLowerCase(Locale.getDefault()).trim();
                    if (str.isEmpty() || str.length() > 2) {
                        chr = ' ';
                    } else {
                        if (str.length() == 1) {
                            n = (int) str.charAt(0) - 'a';
                        } else {
                            int i = (int) str.charAt(0) - 'a';
                            int j = (int) str.charAt(1) - 'a';
                            n = (i + 1) * 26 + j;
                        }
                    }
                } catch (Throwable ex) {
                    chr = ' ';
                }
            }
        } else {
            try {
                String[] arr = str.replaceAll("[@~\\+\\[\\]]", " ").split(",");
                m = Integer.valueOf(arr[1].trim());
                n = Integer.valueOf(arr[0].trim());
            } catch (Throwable ex) {
                chr = ' ';
            }
        }

        if (chr != ' ') {
            int r2, c2;
            switch (cs) {
                case 1:
                    r2 = state.m_nRow + m;
                    c2 = state.m_nCol + n;
                    break;
                case 2:
                    r2 = m_nMacro_Row + m;
                    c2 = m_nMacro_Col + n;
                    break;
                default:
                    r2 = m;
                    c2 = n;
            }
            if (r2 < 0 || c2 < 0 || r2 >= myMaps.curMap.Rows || c2 >= myMaps.curMap.Cols) {
                chr = ' ';
            } else {
                if (!inLine) {
                    m_nMacro_Row = r2;
                    m_nMacro_Col = c2;
                    mMap.myMacroInf = "  >>>  [ " + mMap.mGetCur(r2, c2) + " ]";
                    if (bMoves) {
                        if (FindPath(m_nMacro_Row, m_nMacro_Col, false)) {
                            int len = state.m_lstMovReDo.size();
                            for (int k = 0; k < len; k++) {
                                reDo1();
                            }
                        }
                    }
                } else {
                    chr = state.m_cArray[r2][c2];
                    mMap.myMacroInf = "  >>>  [ " + mMap.mGetCur(r2, c2) + " ] = " + chr;
                }
            }
        }
        return chr;
    }

    private int my_Get_Num(String str, int def) {
        try {
            return Integer.valueOf(str.trim());
        } catch (Throwable ex) {
            return def;
        }
    }

    boolean isLooping = false;
    int loopBegin = -1, loopEnd = -1;

    private int myDo_Loop(int nLine) {
        int n = my_Get_Num(myMaps.sAction[nLine].replaceAll("[\\*]", " "), -1);
        if (n < 0) return nLine + 1;

        int k = nLine + 1;
        while (k < myMaps.sAction.length) {
            if (myMaps.sAction[k].isEmpty()) {
                k++;
                continue;
            }
            if (myMaps.sAction[k].charAt(0) == '*') {
                int n2 = my_Get_Num(myMaps.sAction[k].replaceAll("[\\*]", " "), -1);
                if (n2 < 0) {
                    k++;
                }
                break;
            }
            k++;
        }
        isLooping = true;
        loopBegin = nLine + 1;
        loopEnd = k - 1;
        for (int s = 0; s < n; s++) {
            myDo_Block(loopBegin, loopEnd, false, true);
            if (!isLooping) break;
        }
        if (isLooping) isLooping = false;
        loopBegin = -1;
        loopEnd = -1;
        return k;
    }

    private int myGet_GoTo(String mStr) {
        int k, n;
        char ch;
        String str = mStr.replaceAll("%", " ").trim();

        if (loopBegin >= 0 && str.equals("*")) {
            isLooping = false;
            return -2;
        }

        n = my_Get_Num(str, -1);
        if (n < 0) return -1;

        int len;
        if (loopBegin >= 0) {
            k = loopBegin;
            len = loopEnd;
        } else {
            k = 0;
            len = myMaps.sAction.length;
        }
        while (k < len) {
            if (myMaps.sAction[k].isEmpty()) {
                k++;
                continue;
            }
            ch = myMaps.sAction[k].charAt(0);
            if (ch == ':') {
                if (n == my_Get_Num(myMaps.sAction[k].replaceAll(":", " "), -1)) {
                    return k + 1;
                }
            }
            k++;
        }
        return -1;
    }

    private void myDo_Block(int mBegin, int mEnd, boolean isUnDo, boolean isLoop) {
        int n;
        char ch;

        int step = mBegin;
        while (step <= mEnd && step < myMaps.sAction.length) {
            mMap.myMacroInf = "";
            if (myMaps.sAction[step].isEmpty()) {
                step++;
                if (!isUnDo && !isLoop && mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                    mMap.myMacro.add(step);
                }
                continue;
            }

            ch = myMaps.sAction[step].charAt(0);
            state.m_bBusing = false;
            if (ch == '*') {
                step = myDo_Loop(step);
            } else {
                n = myDo_Line(myMaps.sAction[step], isUnDo, isLoop);
                if (n == -1) {
                    step++;
                } else if (n < -1) {
                    return;
                } else {
                    step = n;
                }
            }

            if (!bt_BK.isChecked()) {
                if (!isUnDo && !isLoop && mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                    mMap.myMacro.add(step);
                }
                if (!isUnDo && !isLoop && myMaps.isMacroDebug) break;
            }
        }
    }

    private int myDo_Line(String myACT, boolean isUnDo, boolean isLoop) {
        if (myACT.isEmpty()) return -1;

        String str;
        String[] myArr;
        int n, m;
        char ch;

        ch = myACT.charAt(0);
        state.m_bACT_IgnoreCase = false;
        state.m_bACT_ERROR = false;
        if (ch == '{') {
            try {
                if (myACT.indexOf('=') >= 0) {
                    myArr = myACT.replaceAll("[\\{\\}]", " ").split("=");
                    if (myArr.length > 1) {
                        str = myArr[0].trim();
                        n = my_Get_Num(str, 0);
                        str = myArr[1].trim();
                        if (n > 9 && n < 100 && myMaps.isLURD(str)) {
                            saveAct("reg" + n, str);
                        }
                    }
                } else {
                    state.m_bACT_IgnoreCase = (myACT.indexOf('~') >= 0);
                    myArr = myACT.replaceAll("~", " ").split("\\{|\\}");
                    if (myArr.length > 1) {
                        str = myArr[1].trim();
                        n = my_Get_Num(str, 0);
                        if (n > 0 && n < 100) {
                            str = loadAct("reg" + n);
                        }
                    } else {
                        str = "";
                    }
                    if (myArr.length > 2) {
                        n = my_Get_Num(myArr[2].trim(), 1);
                        if (n == 0) {
                            n = Integer.MAX_VALUE;
                        }
                        if (n > 1) {
                            for (int k = 0; k < n; k++) {
                                doACT(str);
                                if (state.m_bACT_ERROR) break;
                            }
                        } else {
                            doACT(str);
                        }
                    } else {
                        doACT(str);
                    }
                }
            } catch (Throwable ex) { }
        } else if (ch == '?') {
            myArr = myACT.split("\\?|:|\\/");
            if (myArr.length == 3 || myArr.length == 4) {
                String[] myArr2 = myArr[1].split("=");
                if (myArr2.length == 2) {
                    char ch2 = my_Get_Position(myArr2[0].trim(), -1);
                    mMap.myMacroInf = mMap.myMacroInf + " ▲ " + myACT;
                    if (myArr2[1].trim().indexOf(ch2) >= 0) {
                        return myDo_Line(myArr[2].trim(), isUnDo, isLoop);
                    } else {
                        if (myArr.length == 4) {
                            return myDo_Line(myArr[3].trim(), isUnDo, isLoop);
                        }
                    }
                }
            }
        } else if (ch == '(') {
            try {
                myArr = myACT.split("\\(|\\)");
                n = my_Get_Num(myArr[1].trim(), -1);
                for (int k = 0; k < n; k++) unDo1();
            } catch (Throwable ex) { }
        } else if (ch == '@') {
            my_Get_Position(myACT, 1);
        } else if (ch == '+') {
            my_Get_Position(myACT, 2);
        } else if (ch == '[') {
            my_Get_Position(myACT, 0);
        } else if (ch == '%') {
            return myGet_GoTo(myACT);
        } else if (ch == '^') {
            if (!isUnDo && !isLoop) {
                myMaps.isMacroDebug = true;
            }
        } else if (ch == '<') {
            n = myACT.indexOf('>');
            if (n >= 0) {
                m = my_Get_Num(myACT.substring(n).replaceAll(">~", " ").trim(), 1);
                myArr = myACT.substring(0, n).replaceAll("<", " ").split(";");
            } else {
                m = 1;
                myArr = myACT.replaceAll("<", " ").split(";");
            }
            int len = myArr.length;
            for (int t = 0; t < m; t++) {
                for (int k = 0; k < len; k++) {
                    n = myDo_Line(myArr[k].trim(), isUnDo, isLoop);
                    if (n >= 0) return n;
                }
            }
        } else if (ch == ':') {
        } else {
            doACT(myACT);
        }
        return -1;
    }

    public static class AsyncCountBoxsTask extends SwingWorker<short[][], Void> {
        boolean m_bNoSolution = false;
        boolean[][] mk15, mk16;
        short[][] mk14;
        byte[][] mk0;
        char[][] mArray;
        char[][] mArray0;
        byte[][] freezeBoxs;
        IntStack intStack;
        int m_Rows, m_Cols;

        private final WeakReference<myGameView> mViewReference;

        public AsyncCountBoxsTask(myGameView mView) {
            super();
            mViewReference = new WeakReference<myGameView>(mView);

            // 原版 onPreExecute()：清掉上一轮的逆推死锁数据、复位「无解」标志，并把「更多」染红
            mView.state.mark14 = null;
            mView.state.mark15 = null;
            mView.state.mark16 = null;
            mView.state.mArray9 = null;
            m_bNoSolution = false;
            mView.bt_More.setTextColor(0xffcc0000);

            // 原版是 execute(myMaps.curMap.Rows, myMaps.curMap.Cols)，尺寸在 UI 线程捕获。
            // 本方法只在 initMap() 里、且在 EDT 上被调用，所以这里读到的就是本关的尺寸；
            // 若挪到 doInBackground 里读，后台线程真正起跑时关卡可能已经切走。
            m_Rows = myMaps.curMap != null ? myMaps.curMap.Rows : 0;
            m_Cols = myMaps.curMap != null ? myMaps.curMap.Cols : 0;
        }

        /**
         * 本任务是否仍是当前关卡的任务。
         *
         * <p>{@code SwingWorker.cancel(true)} 在 {@code doInBackground()} <b>已经跑完</b>之后
         * 是空操作（返回 false，{@code isCancelled()} 保持 false）。而「通关后跳下一关」正好发生在
         * 上一关算完的瞬间：{@code initMap()} 的取消拦不住 {@code done()} 排队执行，
         * 于是旧关卡的「这是一个无解的关卡！」会弹在新关卡上，旧关卡的
         * {@code state.mark14/15/16}、{@code state.mArray9} 也会覆盖掉新关卡的死锁数据。
         * 所以 {@code process()} / {@code done()} 都必须先过这一关。
         */
        boolean isCurrent() {
            myGameView view = mViewReference.get();
            return view != null && view.mTask == this;
        }

        @Override
        protected short[][] doInBackground() {
            myGameView view = mViewReference.get();
            if (view == null) return null;
            if (m_Rows <= 0 || m_Cols <= 0) return null;  // 尺寸取自构造器；为 0 说明当时没有关卡

            try {
                int mRow = view.state.m_nRow;
                int mCol = view.state.m_nCol;

                if (isCancelled()) return null;

                mk0  = new byte[m_Rows][m_Cols];
                mk15 = new boolean[m_Rows][m_Cols];
                mk16 = new boolean[m_Rows][m_Cols];
                mk14 = new short[m_Rows][m_Cols];
                freezeBoxs = new byte[m_Rows][m_Cols];
                intStack = new IntStack(200);

                mArray = new char[m_Rows][m_Cols];
                mArray0 = new char[m_Rows][m_Cols];

                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        mArray0[i][j] = view.state.m_cArray0[i][j];
                        if (mArray0[i][j] == '#' || mArray0[i][j] == '_') mArray[i][j] = '#';
                        else mArray[i][j] = '-';
                    }
                }

                setBlock4();

                int pos, r, c;
                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if ((mArray0[i][j] == '$' || mArray0[i][j] == '*') && freezeBoxs[i][j] == 0) {
                            if (isZ_Freeze(i, j)) {
                                while (!intStack.isEmpty()) {
                                    pos = intStack.remove();
                                    r = pos >>> 16;
                                    c = pos & 0x0000ffff;
                                    freezeBoxs[r][c] = 2;
                                }
                            }
                        }
                    }
                }

                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if (isBox2(i, j) && freezeBoxs[i][j] > 0) {
                            if (mArray0[i][j] == '$' && !m_bNoSolution) m_bNoSolution = true;
                            mk15[i][j] = true;
                            mArray0[i][j] = '#';
                            mArray[i][j] = '#';
                        }
                    }
                }

                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if (mArray0[i][j] == '*' && mArray[i][j] != '#' && checkNet(mArray0, i, j)) {
                            mk15[i][j] = true;
                            mk16[i][j] = true;
                            mArray0[i][j] = '#';
                            mArray[i][j] = '#';
                        }
                    }
                }

                // 原版此处是 publishProgress()：无解提醒要在扫描途中就发出去（onProgressUpdate），
                // 而不是等整个任务算完。原先挂在 done() 里，等于推迟了一整轮，
                // 正好落在「通关→跳下一关」之后，就弹在新关卡上了。
                publish();
                view.state.mark15 = mk15;
                view.state.mark16 = mk16;

                view.state.mPF = new MyPathfinder(m_Rows, m_Cols, () -> myMaps.m_Sets[17] == 1);
                view.state.mPF.FindBlock(false, mArray, mRow, mCol);
                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if (mArray0[i][j] == '$' || mArray0[i][j] == '*') {
                            mArray[i][j] = '$';
                            view.state.mPF.boxReachable(false, i, j, mRow, mCol);

                            for (r = 0; r < m_Rows; r++) {
                                for (c = 0; c < m_Cols; c++) {
                                    if (isCancelled()) return null;
                                    if (view.state.mPF.mark3[r][c]) mk14[r][c]++;
                                }
                            }
                            mArray[i][j] = '-';
                        }
                    }
                }
            } catch (Throwable ex) {
                return null;
            }
            return mk14;
        }

        /**
         * 原版 {@code onProgressUpdate()}：扫描途中一旦发现「箱子被冻死且不在目标位」就提醒一次，
         * 提醒完立刻复位 {@code m_bNoSolution}，所以同一个任务不会再提醒第二次。
         *
         * <p>原版 {@code myGameView.java:5598-5600}：
         * {@code new Builder(this, THEME_HOLO_DARK).setTitle("提醒")
         * .setMessage("这是一个无解的关卡！").setPositiveButton("确定", null)
         * .setCancelable(false).create().show();} —— {@code show()} 不阻塞。
         *
         * <p>⚠️ 这里原先是裸的 {@code new JOptionPane(…).createDialog(…) + setVisible(true)}，
         * 两个问题：① 它是<b>模态</b>框，而 {@code process()} 是 SwingWorker 在 EDT 上的回调 ——
         * 模态框会在 EDT 上开嵌套事件循环，期间 {@code publish()} 派发过来的后续
         * {@code process()} 会在<b>上一层框内部</b>重入并再弹一层；② 裸 JOptionPane 的按钮是
         * 英文 "OK"，与原版的「确定」不符。现在改成原版那一份。
         */
        @Override
        protected void process(java.util.List<Void> chunks) {
            myGameView view = mViewReference.get();
            if (view == null || !isCurrent()) return;  // 已切关：旧关卡的提醒不能弹在新关卡上
            if (m_bNoSolution) {
                m_bNoSolution = false;
                HoloAlertDialog dlg = HoloAlertDialog.createNonModal(view, "提醒");
                dlg.setMessage("这是一个无解的关卡！");
                dlg.addButton("确定", null);
                view.dialogShower.accept(dlg);
            }
        }

        @Override
        protected void done() {
            myGameView view = mViewReference.get();
            if (view == null) return;

            // 已被 initMap() 换成新任务（或 myStop() 置空）：本任务的结果属于上一关，一律丢弃 ——
            // 既不写 state.mark14/15/16、state.mArray9（否则新关卡会拿到旧关卡的死锁数据而误报），也不弹提醒。
            if (!isCurrent()) return;
            if (isCancelled()) return;

            try {
                short[][] mk = get();
                view.state.mark14 = mk;
                view.state.mArray9 = mArray;
            } catch (Throwable ignored) { }

            view.bt_More.setTextColor(0xffffffff);
        }

        private boolean isWall(int row, int col) {
            return row < 0 || col < 0 || row >= m_Rows || col >= m_Cols ||
                    mArray0[row][col] == '#' || mArray0[row][col] == '_' ||
                    freezeBoxs[row][col] == 4;
        }

        private boolean isPass(int row, int col) {
            if (row < 0 || col < 0 || row >= m_Rows || col >= m_Cols) return false;
            return mArray0[row][col] == '-' || mArray0[row][col] == '.' ||
                    mArray0[row][col] == '@' || mArray0[row][col] == '+';
        }

        private boolean isBoxOrWall(int row, int col) {
            if (row < 0 || col < 0 || row >= m_Rows || col >= m_Cols ||
                    mArray0[row][col] == '#' || mArray0[row][col] == '_' ||
                    mArray0[row][col] == '$' || mArray0[row][col] == '*') return true;
            return false;
        }

        private boolean isBox2(int row, int col) {
            if (row < 0 || col < 0 || row >= m_Rows || col >= m_Cols) return false;
            return mArray0[row][col] == '$' || mArray0[row][col] == '*';
        }

        private void setBlock4() {
            for (int i = 0; i < m_Rows; i++) {
                for (int j = 0; j < m_Cols; j++) {
                    if (isBoxOrWall(i, j) && isBoxOrWall(i, j - 1) && isBoxOrWall(i - 1, j) && isBoxOrWall(i - 1, j - 1)) {
                        if (isBox2(i, j)) freezeBoxs[i][j] = 4;
                        if (isBox2(i, j - 1)) freezeBoxs[i][j - 1] = 4;
                        if (isBox2(i - 1, j)) freezeBoxs[i - 1][j] = 4;
                        if (isBox2(i - 1, j - 1)) freezeBoxs[i - 1][j - 1] = 4;
                    } else freezeBoxs[i][j] = 0;
                }
            }
        }

        private final int[][] dA = {
                {0, -1, -1, 0}, {0, -1, 1, 0}, {0, 1, -1, 0}, {0, 1, 1, 0}
        };
        private final int[][] dB = {
                {1, 0, 0, 1}, {-1, 0, 0, 1}, {1, 0, 0, -1}, {-1, 0, 0, -1}
        };

        private boolean isZ_Freeze(int row, int col) {
            intStack.clear();
            if (isZ(row, col, dA[0]) && isZ(row, col, dB[0])) return true;
            intStack.clear();
            if (isZ(row, col, dA[1]) && isZ(row, col, dB[1])) return true;
            intStack.clear();
            if (isZ(row, col, dA[2]) && isZ(row, col, dB[2])) return true;
            intStack.clear();
            if (isZ(row, col, dA[3]) && isZ(row, col, dB[3])) return true;
            return false;
        }

        private boolean isZ(int row, int col, int[] dDir) {
            int r = row, c = col;
            boolean flg = true;
            while (true) {
                intStack.add(r << 16 | c);
                if (flg) { r += dDir[0]; c += dDir[1]; }
                else { r += dDir[2]; c += dDir[3]; }

                if (isPass(r, c)) break;
                if (isWall(r, c)) return true;
                flg = !flg;
            }
            return false;
        }

        private boolean checkNet(char[][] m_Arr, int b_new_Row, int b_new_Col) {
            int mRows = m_Arr.length, mCols = m_Arr[0].length;
            for (int i = 0; i < mRows; i++) {
                for (int j = 0; j < mCols; j++) {
                    mk0[i][j] = 0;
                }
            }

            Queue<Integer> Q = new LinkedList<Integer>();
            Queue<Integer> Q2 = new LinkedList<Integer>();

            int P, r = b_new_Row, c = b_new_Col, r1, c1, r2, c2;
            Q.offer(r << 16 | c);
            mk0[r][c] = 1;

            while (!Q.isEmpty()) {
                P = Q.poll();
                r = P >>> 16;
                c = P & 0x0000ffff;

                for (int k = 0; k < 4; k++) {
                    r1 = r + dr4(k);
                    c1 = c + dc4(k);
                    r2 = r + dr4(k) * 2;
                    c2 = c + dc4(k) * 2;

                    if (isVisited(mk0, r2, c2) || isWall(r2, c2) || isWall(r1, c1)) {
                        continue;
                    } else if (m_Arr[r2][c2] == '-' || m_Arr[r2][c2] == '.' || m_Arr[r2][c2] == '@' || m_Arr[r2][c2] == '+' || m_Arr[r2][c2] == '$') {
                        return false;
                    } else {
                        Q.offer(r2 << 16 | c2);
                        Q2.offer(r2 << 16 | c2);
                        mk0[r2][c2] = 1;
                    }
                }
            }

            while (!Q2.isEmpty()) {
                P = Q2.poll();
                r = P >>> 16;
                c = P & 0x0000ffff;
                mk15[r][c] = true;
                mk16[r][c] = true;
                m_Arr[r][c] = '#';
                mArray[r][c] = '#';
            }
            return true;
        }

        private int dr4(int k) {
            int[] d = {0, 0, -1, 1};
            return d[k];
        }

        private int dc4(int k) {
            int[] d = {-1, 1, 0, 0};
            return d[k];
        }

        private boolean isVisited(byte[][] m_Mrk, int mR, int mC) {
            return mR >= 0 && mC >= 0 && mR < m_Rows && mC < m_Cols && m_Mrk[mR][mC] > 0;
        }
    }

    public static class RunMicroTask extends SwingWorker<Void, Void> {
        private final WeakReference<myGameView> mViewReference2;
        private long myTime, myTime0;
        private final int[] mySpeed = {0, 200, 300, 500, 1000};
        private final String[] msg = {"", "正推通关！", "正逆相合！", "逆推通关！"};
        private int type = 0;
        private int mBegin, mEnd;

        public RunMicroTask(myGameView mView) {
            super();
            mViewReference2 = new WeakReference<myGameView>(mView);
        }

        public void execute(int begin, int end) {
            this.mBegin = begin;
            this.mEnd = end;
            myTime0 = System.currentTimeMillis();
            myTime = myTime0;
            type = 0;
            super.execute();
        }

        @Override
        protected Void doInBackground() {
            myGameView view = mViewReference2.get();
            if (view == null) return null;

            try {
                int n;
                char ch;
                int step = mBegin;
                while (step <= mEnd && step < myMaps.sAction.length) {
                    if (isCancelled()) return null;

                    view.mMap.myMacroInf = "";
                    if (myMaps.sAction[step].isEmpty()) {
                        step++;
                        if (view.mMap.myMacro.get(view.mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                            view.mMap.myMacro.add(step);
                        }
                        continue;
                    }

                    ch = myMaps.sAction[step].charAt(0);
                    view.state.m_bBusing = false;
                    if (ch == '*') {
                        step = view.myDo_Loop(step);
                    } else {
                        n = view.myDo_Line(myMaps.sAction[step], false, false);
                        if (n == -1) {
                            step++;
                        } else if (n < -1) {
                            return null;
                        } else {
                            step = n;
                        }
                    }

                    if (!view.bt_BK.isChecked()) {
                        if (view.mMap.myMacro.get(view.mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                            view.mMap.myMacro.add(step);
                        }
                        if (myMaps.isMacroDebug) break;
                    }
                    if (System.currentTimeMillis() - myTime > mySpeed[myMaps.m_Sets[10]]) {
                        myTime = System.currentTimeMillis();
                        publish();
                    }
                }
                if (myMaps.curMap != null && myMaps.curMap.Level_id > 0) {
                    if (view.bt_BK.isChecked()) {
                        if (view.myClearance2()) {
                            view.zhengniHE2();
                            view.saveAns2();
                            type = 3;
                        } else if (view.myMeet()) {
                            view.zhengniHE();
                            view.saveAns2();
                            type = 2;
                        }
                    } else {
                        if (view.myClearance()) {
                            view.saveAns(1);
                            type = 1;
                        } else if (view.myMeet()) {
                            view.zhengniHE();
                            view.saveAns2();
                            type = 2;
                        }
                    }
                }
            } catch (Throwable ex) { }
            return null;
        }

        @Override
        protected void process(java.util.List<Void> chunks) {
            myGameView view = mViewReference2.get();
            if (view != null) view.mMap.invalidate();
        }

        @Override
        protected void done() {
            myGameView view = mViewReference2.get();
            if (view != null) {
                view.mMap.invalidate();
                view.state.m_bBusing = false;
                myMaps.m_ActionIsRedy = false;
                MyToast.showToast(view, msg[type] + "耗时 " + ((myTime - myTime0) / 1000) + " 秒", MyToast.LENGTH_SHORT);
                if (!view.state.m_lstMovReDo.isEmpty()) {
                    view.state.m_lstMovReDo.clear();
                }
                view.StopMicro();
            }
        }
    }
}
