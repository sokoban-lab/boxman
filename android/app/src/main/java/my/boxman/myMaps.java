package my.boxman;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

//此类仅定义公用数据
public class myMaps extends LevelData {

	static boolean isMacroDebug = false;  //单步宏
	static boolean isSelect = false;  //浏览界面是否处于多选模式
	static boolean isRecording = false;  //录制模式
	static int m_nRecording_Bggin;  //正推录制起始点
	static int m_nRecording_Bggin2;  //逆推录制起始点
	static int isSkin_200 = 200;  //皮肤顶部默认包含200高的无缝墙壁
	static boolean isSimpleSkin = false;  //是否简单皮肤350
	static boolean isHengping = false;  //是否使用横屏皮肤

	//公用变量
	static Context ctxDealFile;

	static int m_nWinWidth;  //屏幕尺寸
	static int m_nWinHeight;

	static int m_nTrun = 0;   //地图旋转角度

	static String sFile;    //关卡集文档名
	static String[] myPathList = {  //关卡截图根目录列表
			"",                         // 默认位置
			"/tencent/qq_images/",      // QQ 图片接收位置
			"/Pictures/Screenshots/",   // 自定义 1
			"/",                        // 自定义 2
			"/"                         // 自定义 3
	};

	static String[] sAction;  //动作寄存器缓存
	static boolean m_ActionIsRedy = false; //状态是否准备就绪
	static boolean m_ActionIsPos = true;   //是否从关卡的当前点执行动作
	static boolean m_ActionIsTrun = true;  //是否按关卡的当前旋转状态执行动作
	static boolean m_MapChange = true;     //是否切换了关卡

	static ArrayList<Integer> mArray = new ArrayList<Integer>();   //关卡浏览多选状态模式删除、迁移使用

	static ArrayList<String> mFile_List = new ArrayList<String>();  //关卡文档
	static ArrayList<String> mFile_List1 = new ArrayList<String>();  //皮肤文档
	static ArrayList<String> mFile_List2 = new ArrayList<String>();  //背景文档

	static ListView m_setName;  //查询用关卡集选择框 -- 关卡集选项

	static boolean m_bBianhao = false;  //自动箱子编号
	static boolean m_bBiaochi = false;  //标尺
	static boolean curJi = false;  //是否选择了关卡集，开始选择关卡
	static boolean iskinChange = false;  //皮肤是否有变化
	static boolean isSaveBlock = false;  //关卡编辑中，保存过“区块”
	static boolean isXSB = true;  //是否导入关卡
	static int m_Code = 0;  //文档导入关卡的字符编码格式：0 - 自动，1 - GBK，2 - utf-8


	static String mMatchNo = "";  //第 n 期比赛
	static String mMatchDate1 = "";  //比赛开始日期
	static String mMatchDate2 = "";  //比赛结束日期
	static String nickname = "";  //提交答案 - nickname
	static String uil = "https://sokoban.cn/";  //提交答案 - uil
	static String country = "CN";  //提交答案 - country
	static String email = "";  //提交答案 - email
	static String bk_Pic = "使用背景色";  //背景图片文档名
	static String skin_File = "默认皮肤";  //皮肤文档名
	static Bitmap markGif1 = null; //默认的在线推箱子 logo 水印
	static Bitmap markGif2 = null; //自定义水印
	static Bitmap skinGif = null; //GIF皮肤
	static Bitmap skinBit = null; //皮肤
	static Bitmap bkPict = null; //背景图片
	static Bitmap edPict = null;  //关卡编辑界面的背景图片
	static int edPictLeft, edPictTop, edPictRight, edPictBottom;  // 关卡编辑界面的背景图片的四至
	static int edRows, edCols;                                    // 识别时的关卡尺寸
	static android.graphics.Bitmap.Config cfg = Bitmap.Config.ARGB_4444;
	static Paint myPaint = new Paint();
	static Resources res;  //资源

	static Bitmap FloorPic, GoalPic, NituiPic, SelPic, BoxPic, BoxGoalPic;
	static Bitmap KedaPic, ChuanyuePic, WangweiPic, WangkouPic;
	static Bitmap ManPic_u,  ManPic_r, ManPic_d, ManPic_l, ManGoalPic_u,ManGoalPic_r,ManGoalPic_d,ManGoalPic_l;
	static Bitmap WallPic, WallPic_lurd, WallPic_lr, WallPic_l, WallPic_r, WallPic_ud, WallPic_u, WallPic_d, WallPic_lu,
			WallPic_ld, WallPic_ru, WallPic_rd, WallPic_lur, WallPic_ldr, WallPic_uld, WallPic_urd, WallPic_top;        // 无缝墙壁

	//加载皮肤
	static void loadSkins() {
		if (myMaps.skinBit != null) myMaps.skinBit.recycle();
		myMaps.skinBit = null;
		Canvas cvsTmp;
		Rect rt  = new Rect(0, 0, 50, 50);
		Rect rt2 = new Rect(0, 0, 25, 25);
		Rect rt_ = new Rect();

		isSimpleSkin = false;  // 非简版皮肤
		isSkin_200 = 200;  // 默认使用标准皮肤 -- 顶部包含200高的无缝墙壁
		isHengping = false;  // 默认使用竖屏
		try{
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inPreferredConfig = myMaps.cfg;
			opts.inJustDecodeBounds = true;  //仅提取图片宽高
			BitmapFactory.decodeFile(myMaps.sRoot+myMaps.sPath + "皮肤/" + myMaps.skin_File, opts);
			//支持 200 x 150（超简）、200 x 350（简版） 和 200 x 400（标准） 三种尺寸的皮肤
			if (opts.outWidth == 200) {
				if (opts.outHeight == 150) isSkin_200 = 0;  // 超简皮肤（顶部没有无缝墙壁）
				else if (opts.outHeight == 350) isSimpleSkin = true;  // 简版皮肤（只有两个方向的仓管员）
				else if (opts.outHeight != 400) throw new Exception();  // 默认皮肤（无缝墙壁、四个方向的仓管员）
			} else throw new Exception();
			opts.inJustDecodeBounds = false;   //提取图片
			myMaps.skinBit = BitmapFactory.decodeFile(myMaps.sRoot+myMaps.sPath + "皮肤/" + myMaps.skin_File, opts);
		} catch (Exception e) {
			if (!myMaps.skin_File.equals("默认皮肤"))
				MyToast.showToast(myMaps.ctxDealFile, "自定义皮肤无效！", Toast.LENGTH_SHORT);
			InputStream fis = myMaps.res.openRawResource(R.raw.defskin);
			myMaps.skinBit = BitmapFactory.decodeStream(fis);
		}

		myPaint.setARGB(255, 0, 0, 0);

		// 通用皮肤（超简皮肤）
		WallPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(WallPic);
		rt_.set(0, 0, 50, 50);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);    // 墙壁

		NituiPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(NituiPic);
		rt_.set(50, 0 + isSkin_200, 100, 50 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 逆推水印

		SelPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(SelPic);
		rt_.set(100, 0 + isSkin_200, 150, 50 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 选择框

		FloorPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(FloorPic);
		rt_.set(0, 50 + isSkin_200, 50, 100 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 地板

		GoalPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(GoalPic);
		rt_.set(0, 100 + isSkin_200, 50, 150 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 目标位

		BoxPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(BoxPic);
		rt_.set(50, 50 + isSkin_200, 100, 100 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 箱子

		BoxGoalPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(BoxGoalPic);
		rt_.set(50, 100 + isSkin_200, 100, 150 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 目标位上的箱子

		ManPic_u = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(ManPic_u);
		rt_.set(100, 50 + isSkin_200, 150, 100 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向上的人

		ManGoalPic_u = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(ManGoalPic_u);
		rt_.set(100, 100 + isSkin_200, 150, 150 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向上的人在目标位

		ManPic_r = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(ManPic_r);
		rt_.set(150, 50 + isSkin_200, 200, 100 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向右的人

		ManGoalPic_r = Bitmap.createBitmap(50, 50, myMaps.cfg);
		cvsTmp = new Canvas(ManGoalPic_r);
		rt_.set(150, 100 + isSkin_200, 200, 150 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向右的人在目标位

		KedaPic = Bitmap.createBitmap(25, 25, myMaps.cfg);
		cvsTmp = new Canvas(KedaPic);
		rt_.set(150, 0 + isSkin_200, 175, 25 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt2, myPaint);     // 可达提示

		ChuanyuePic = Bitmap.createBitmap(25, 25, myMaps.cfg);
		cvsTmp = new Canvas(ChuanyuePic);
		rt_.set(175, 0 + isSkin_200, 200, 25 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt2, myPaint);      // 穿越点提示

		WangweiPic = Bitmap.createBitmap(25, 25, myMaps.cfg);
		cvsTmp = new Canvas(WangweiPic);
		rt_.set(150, 25 + isSkin_200, 175, 50 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt2, myPaint);      // 网位点提示

		WangkouPic = Bitmap.createBitmap(25, 25, myMaps.cfg);
		cvsTmp = new Canvas(WangkouPic);
		rt_.set(175, 25 + isSkin_200, 200, 50 + isSkin_200);
		cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt2, myPaint);      // 网口点提示

		if (isSkin_200 == 200) {  // 无缝墙壁
			// 横条
			WallPic_r = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_r);
			rt_.set(50, 0, 100, 50);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_lr = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_lr);
			rt_.set(100, 0, 150, 50);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_l = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_l);
			rt_.set(150, 0, 200, 50);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			// 纵条
			WallPic_d = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_d);
			rt_.set(0, 50, 50, 100);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_ud = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_ud);
			rt_.set(0, 100, 50, 150);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_u = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_u);
			rt_.set(0, 150, 50, 200);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);


			// “田”字部分
			WallPic_rd = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_rd);
			rt_.set(50, 50, 100, 100);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_ldr = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_ldr);
			rt_.set(100, 50, 150, 100);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_ld = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_ld);
			rt_.set(150, 50, 200, 100);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_urd = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_urd);
			rt_.set(50, 100, 100, 150);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_lurd = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_lurd);
			rt_.set(100, 100, 150, 150);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_uld = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_uld);
			rt_.set(150, 100, 200, 150);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_ru = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_ru);
			rt_.set(50, 150, 100, 200);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_lur = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_lur);
			rt_.set(100, 150, 150, 200);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			WallPic_lu = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_lu);
			rt_.set(150, 150, 200, 200);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			// 顶
			WallPic_top = Bitmap.createBitmap(50, 50, myMaps.cfg);
			cvsTmp = new Canvas(WallPic_top);
			rt_.set(0, 200, 50, 250);
			cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

			if (!isSimpleSkin) {  // 标准皮肤 -- 多出另外两个方向的仓管员
				ManPic_l = Bitmap.createBitmap(50, 50, myMaps.cfg);
				cvsTmp = new Canvas(ManPic_l);
				rt_.set(0, 150 + isSkin_200, 50, 200 + isSkin_200);
				cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向左的人

				ManGoalPic_l = Bitmap.createBitmap(50, 50, myMaps.cfg);
				cvsTmp = new Canvas(ManGoalPic_l);
				rt_.set(50, 150 + isSkin_200, 100, 200 + isSkin_200);
				cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向左的人在目标位

				ManPic_d = Bitmap.createBitmap(50, 50, myMaps.cfg);
				cvsTmp = new Canvas(ManPic_d);
				rt_.set(100, 150 + isSkin_200, 150, 200 + isSkin_200);
				cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向下的人

				ManGoalPic_d = Bitmap.createBitmap(50, 50, myMaps.cfg);
				cvsTmp = new Canvas(ManGoalPic_d);
				rt_.set(150, 150 + isSkin_200, 200, 200 + isSkin_200);
				cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);     // 向下的人在目标位
			}
		}

		System.gc();
	}

	//加载背景
	static void loadBKPic() {
		if (myMaps.bkPict != null) myMaps.bkPict.recycle();
		myMaps.bkPict = null;
		if (myMaps.bk_Pic == null || myMaps.bk_Pic.length() <= 0 || myMaps.bk_Pic.equals("使用背景色")) {
			myMaps.bk_Pic = "使用背景色";
			return;
		}

		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
		try{
			opts.inJustDecodeBounds = true;  //仅提取图片宽高
			BitmapFactory.decodeFile(myMaps.sRoot+myMaps.sPath + "背景/" + myMaps.bk_Pic, opts);


			if (opts.outWidth > myMaps.m_nWinWidth || opts.outHeight > myMaps.m_nWinHeight) {
				int k1 = opts.outWidth / myMaps.m_nWinWidth;
				int k2 = opts.outHeight / myMaps.m_nWinHeight;
				opts.inSampleSize = (k1 > k2) ? k1 : k2;
			}
			opts.inJustDecodeBounds = false;   //提取图片
			opts.inDither=false;    /*不进行图片抖动处理*/
			opts.inPreferredConfig=null;  /*设置让解码器以最佳方式解码*/
			/* 下面两个字段需要组合使用 */
			opts.inPurgeable = true;
			opts.inInputShareable = true;
			myMaps.bkPict = BitmapFactory.decodeFile(myMaps.sRoot+myMaps.sPath + "背景/" + myMaps.bk_Pic, opts);
		} catch (Exception e) {
			if (myMaps.bkPict != null && !myMaps.bkPict.isRecycled()) {
				myMaps.bkPict.recycle();
				myMaps.bkPict = null;
			}
			System.gc();
			MyToast.showToast(myMaps.ctxDealFile, "无效的背景图片！", Toast.LENGTH_SHORT);
		}
	}
	//读入剪切板
	static String loadClipper() {
		try {
			ClipboardManager cmb = (ClipboardManager) myMaps.ctxDealFile.getSystemService(Context.CLIPBOARD_SERVICE);
			if (cmb.getPrimaryClipDescription().hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) ||
					cmb.getPrimaryClipDescription().hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML)) {
				android.content.ClipData cd = cmb.getPrimaryClip();
				android.content.ClipData.Item item = cd.getItemAt(0);
				return item.getText().toString();
			} else return "";
//			if (cmb.hasPrimaryClip() && cmb.getPrimaryClip().getItemCount() > 0) {
//				return cmb.getPrimaryClip().getItemAt(0).getText().toString();
//			} else return "";
		} catch(Exception e) {
			return "";
		}
	}

	//装填关卡集列表项
	static List<String> getData(ArrayList<Long> mySets) {
		List<String> list = new ArrayList<String>();

		for (int k = 0; k < myMaps.mSets0.size(); k++) {
			list.add(myMaps.mSets0.get(k).title);
			mySets.add(myMaps.mSets0.get(k).id);
		}

		for (int k = 0; k < myMaps.mSets1.size(); k++) {
			list.add(myMaps.mSets1.get(k).title);
			mySets.add(myMaps.mSets1.get(k).id);
		}

		for (int k = 0; k < myMaps.mSets2.size(); k++) {
			list.add(myMaps.mSets2.get(k).title);
			mySets.add(myMaps.mSets2.get(k).id);
		}

		for (int k = 0; k < myMaps.mSets3.size(); k++) {
			list.add(myMaps.mSets3.get(k).title);
			mySets.add(myMaps.mSets3.get(k).id);
		}

		return list;
	}


	//根据关卡集 id，得到关卡集名称
	static String getSetTitle(long id) {

		for (int k = 0; k < myMaps.mSets0.size(); k++) {
			if (myMaps.mSets0.get(k).id == id)  return myMaps.mSets0.get(k).title;
		}

		for (int k = 0; k < myMaps.mSets1.size(); k++) {
			if (myMaps.mSets1.get(k).id == id)  return myMaps.mSets1.get(k).title;
		}

		for (int k = 0; k < myMaps.mSets2.size(); k++) {
			if (myMaps.mSets2.get(k).id == id)  return myMaps.mSets2.get(k).title;
		}

		for (int k = 0; k < myMaps.mSets3.size(); k++) {
			if (myMaps.mSets3.get(k).id == id)  return myMaps.mSets3.get(k).title;
		}

		return "";
	}

	//根据关卡集 id，检查是否为扩展关卡集内的关卡
	static boolean isExSet(long id) {
		for (int k = 0; k < myMaps.mSets3.size(); k++) {
			if (myMaps.mSets3.get(k).id == id)  return true;
		}

		return false;
	}

	//送入剪切板
	static void saveClipper(String str) {
		android.content.ClipboardManager cmb = (android.content.ClipboardManager) myMaps.ctxDealFile.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData mClipData = ClipData.newPlainText(null, str);
		cmb.setPrimaryClip(mClipData);
	}

	//通过转换，增强宏功能对全角字符的支持
	static String qj2bj(String src) {
		if (src == null) return src;

		StringBuilder buf = new StringBuilder(src.length());
		char[] ca = src.toCharArray();
		for (int i = 0; i < src.length(); i++) {
			if (ca[i] == '｛') {  //花括号--动作、寄存器
				buf.append('{');
			} else if (ca[i] == '｝') {
				buf.append('}');
			} else if (ca[i] == '（') {  //圆括号--单步后退
				buf.append('(');
			} else if (ca[i] == '）') {
				buf.append(')');
			} else if (ca[i] == '【') {  //方括号--坐标
				buf.append('[');
			} else if (ca[i] == '】') {
				buf.append(']');
			} else if (ca[i] == '〖') {
				buf.append('[');
			} else if (ca[i] == '〗') {
				buf.append(']');
			} else if (ca[i] == '﹝') {
				buf.append('[');
			} else if (ca[i] == '﹞') {
				buf.append(']');
			} else if (ca[i] == '〔') {
				buf.append('[');
			} else if (ca[i] == '〕') {
				buf.append(']');
			} else if (ca[i] == '［') {
				buf.append('[');
			} else if (ca[i] == '］') {
				buf.append(']');
			} else if (ca[i] == '〈') {  //尖括号--行内块
				buf.append('<');
			} else if (ca[i] == '〉') {
				buf.append('>');
			} else if (ca[i] == '＜') {
				buf.append('<');
			} else if (ca[i] == '＞') {
				buf.append('>');
			} else if (ca[i] == '‹') {
				buf.append('<');
			} else if (ca[i] == '›') {
				buf.append('>');
			} else if (ca[i] == '《') {
				buf.append('<');
			} else if (ca[i] == '》') {
				buf.append('>');
			} else if (ca[i] == '«') {
				buf.append('<');
			} else if (ca[i] == '»') {
				buf.append('>');
			} else if (ca[i] == '，') {  //逗号--坐标
				buf.append(',');
			} else if (ca[i] == '；') {  //分号--注释、语句分割
				buf.append(';');
			} else if (ca[i] == '：') {  //冒号--条件成立
				buf.append(':');
			} else if (ca[i] == '∶') {
				buf.append(':');
			} else if (ca[i] == '＝') {  //等号--关卡复位到初态
				buf.append('=');
			} else if (ca[i] == '＋') {  //加号--坐标、人在目标点
				buf.append('+');
			} else if (ca[i] == '－') {  //减号--地板、坐标
				buf.append('-');
			} else if (ca[i] == '－') {
				buf.append('-');
			} else if (ca[i] == '～') {  //波浪线--忽略移位或动作
				buf.append('~');
			} else if (ca[i] == '＾') {  //尖号--调试中断符号
				buf.append('^');
			} else if (ca[i] == '∧') {
				buf.append('^');
			} else if (ca[i] == '＠') {  //@--坐标、人
				buf.append('@');
			} else if (ca[i] == '％') {  //百分号--跳转符号，相当于goto
				buf.append('%');
			} else if (ca[i] == '／') {  //正斜线--条件不成立，即：相当于“否则”、else
				buf.append('/');
			} else if (ca[i] == '！') {  //感叹号--暂时未用
				buf.append('!');
			} else if (ca[i] == '＆') {  //&--暂时未用
				buf.append('&');
			} else if (ca[i] == '＃') {  //#号--墙
				buf.append('#');
			} else if (ca[i] == '＿') {  //下划线--墙外
				buf.append('_');
			} else if (ca[i] == '．') {  //圆点--目标点
				buf.append('.');
			} else if (ca[i] == '·') {
				buf.append('.');
			} else if (ca[i] == '。') {
				buf.append('.');
			} else if (ca[i] == '＄') {  //$--箱子
				buf.append('$');
			} else if (ca[i] == '￥') {
				buf.append('$');
			} else if (ca[i] == '＊') {  //星号--箱子在目标点
				buf.append('*');
			} else if (ca[i] == '※') {
				buf.append('*');
			} else if (ca[i] == '×') {
				buf.append('*');
			} else if (ca[i] == '？') {  //？-条件符号
				buf.append('?');
			} else if (ca[i] == '　') {  //全角空格
				buf.append(' ');
			} else if (ca[i] >= 65281 && ca[i] <= 65374) {  //处理没想到的字符
				buf.append((char) (ca[i] - 65248));
			} else {  //不处理其它字符
				buf.append(ca[i]);
			}
		}
		return buf.toString();
	}

	//是否为“宏”指令，此时，全角字符已经做过转换
	static boolean isMacro(String[] str) {
		String regex = "[=:~;@%.*#$&!<>\\{\\}\\?\\/\\(\\)\\+\\^]";  //＝：∶～；＠％．。·＊※×＃＄￥＆！【】〖〗﹝﹞〔〕［］〈〉＜＞‹›《》«»｛｝？／（）＋＾∧
		Matcher m;
		for (int k = 0; k < str.length; k++) {
			m = Pattern.compile(regex).matcher(str[k]);
			if (m.matches()) {
				return true;
			}
		}
		return false;
	}

	//从文档读入“宏”
	static String readMacroFile(String fn) {
		String my_Name;
		try {
			my_Name = new StringBuilder(myMaps.sRoot).append(myMaps.sPath).append("宏/").append(fn).toString();

			File file = new File(my_Name);
//			InputStreamReader read = new InputStreamReader(new FileInputStream(file), myMaps.getFileEncode(file));  //考虑到编码格式
			InputStreamReader read = new InputStreamReader(new FileInputStream(file), myMaps.getTxtEncode(new FileInputStream(file)));  //考虑到编码格式

			BufferedReader bufferedReader = new BufferedReader(read);

			StringBuilder str = new StringBuilder();
			String line;

			while ((line = bufferedReader.readLine()) != null) {
				str.append(line).append('\n');
			}

			return str.toString();
		} catch (Exception e) {
			MyToast.showToast(ctxDealFile, "无效的宏文档！", Toast.LENGTH_SHORT);
		}
		return "";
	}

	//取得"宏/"文件夹下的文档列表，最新文档列在最前面
	static void mMacroList() {
		File targetDir = new File(myMaps.sRoot + myMaps.sPath + "宏/");
		myMaps.mFile_List.clear();
		if (!targetDir.exists()) targetDir.mkdirs();  //创建"宏/"文件夹
		else {
			String[] filelist = targetDir.list();
			Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
			for (int i = 0; i < filelist.length; i++) {
				int dot = filelist[i].lastIndexOf('.');
				if ((dot > -1) && (dot < (filelist[i].length()))) {
					String prefix = filelist[i].substring(filelist[i].lastIndexOf(".") + 1);
					if (prefix.equalsIgnoreCase("txt"))
						myMaps.mFile_List.add(filelist[i]);
				}
			}
		}
	}

	//取得截图列表
	static void edPicList(String fn) {
		File targetDir = new File(fn);
		myMaps.mFile_List.clear();
		if (targetDir.exists()) {
			File[] fs = targetDir.listFiles();
			Arrays.sort(fs, new Comparator< File>(){            // 截图文档列表按创建时间排序
				public int compare(File f1, File f2) {
					long diff = f1.lastModified() - f2.lastModified();
					if (diff > 0)
						return -1;
					else if (diff == 0)
						return 0;
					else
						return 1;
				}
				public boolean equals(Object obj) {
					return true;
				}
			});
			for (int i = 0; i < fs.length; i++) {
				int dot = fs[i].getName().lastIndexOf('.');
				if ((dot > -1) && (dot < (fs[i].length()))) {
					String prefix = fs[i].getName().substring(fs[i].getName().lastIndexOf(".") + 1);
					if (prefix.equalsIgnoreCase("jpg") || prefix.equalsIgnoreCase("bmp") || prefix.equalsIgnoreCase("png"))
						myMaps.mFile_List.add(fs[i].getName());
				}
			}
//			String[] filelist = targetDir.list();
//			Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
//			for (int i = 0; i < filelist.length; i++) {
//				int dot = filelist[i].lastIndexOf('.');
//				if ((dot > -1) && (dot < (filelist[i].length()))) {
//					String prefix = filelist[i].substring(filelist[i].lastIndexOf(".") + 1);
//					if (prefix.equalsIgnoreCase("jpg") || prefix.equalsIgnoreCase("bmp") || prefix.equalsIgnoreCase("png"))
//						myMaps.mFile_List.add(filelist[i]);
//				}
//			}
		}
	}

	//"导入/"下的文档列表
	static void newSetList(){
		File targetDir = new File(sRoot+sPath + "导入/");
		mFile_List.clear();
		if (!targetDir.exists()) targetDir.mkdirs();  //创建"导入/"文件夹
		else {
			String[] filelist = targetDir.list();
			Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
			for (int i = 0; i < filelist.length; i++) {
				int dot = filelist[i].lastIndexOf('.');
				if ((dot >-1) && (dot < (filelist[i].length()))){
					String prefix=filelist[i].substring(filelist[i].lastIndexOf(".")+1);
					if (prefix.equalsIgnoreCase("xsb") ||
							prefix.equalsIgnoreCase("txt") ||
							prefix.equalsIgnoreCase("sok") ||
							prefix.equalsIgnoreCase("txz"))
						mFile_List.add(filelist[i]);
				}
			}
		}
	}

//	//快手全部关卡集列表
//	static void allSetList(){
//		mFile_List.clear();
//		mArray2.clear();
//
//		for (int i = 0; i < myMaps.mSets0.size(); i++) {
//			mFile_List.add(myMaps.mSets0.get(i).title);
//			myMaps.mArray2.add(myMaps.mSets0.get(i).id);
//		}
//		for (int i = 0; i < myMaps.mSets1.size(); i++) {
//			mFile_List.add(myMaps.mSets1.get(i).title);
//			myMaps.mArray2.add(myMaps.mSets1.get(i).id);
//		}
//		for (int i = 0; i < myMaps.mSets2.size(); i++) {
//			mFile_List.add(myMaps.mSets2.get(i).title);
//			myMaps.mArray2.add(myMaps.mSets2.get(i).id);
//		}
//		for (int i = 0; i < myMaps.mSets3.size(); i++) {
//			mFile_List.add(myMaps.mSets3.get(i).title);
//			myMaps.mArray2.add(myMaps.mSets3.get(i).id);
//		}
//		mFile_List.add("答案库中的自由关卡");
//	}

	//解析字符串中的XSB
	static boolean loadXSB(String str) {
		String[] Arr;
		try {
			Arr = str.split("\r\n|\n\r|\n|\r|\\|");

			String[] new_map = new String[m_nMaxRow];
			int row = 0;
			int col = 0;

			boolean flg = false;
			int p = 0;
			for (int k = 0; k < Arr.length && k < m_nMaxRow; k++) {
				if (isXSB(Arr[k])) {
					flg = true;
					new_map[row] = Arr[k];
					if (col < Arr[k].length()) col = Arr[k].length();  //最大宽度
					if (col > m_nMaxCol) col = m_nMaxCol;  //最大宽度
					row++;
				} else {  //可以跳过开头的无效行
					if (flg) {
						p = k;
						break;  //匹配 XSB 行，XSB 行必须连续
					}
				}
			}

			boolean flg2 = false;
			StringBuilder g_Title = new StringBuilder();  //标题
			StringBuilder g_Author = new StringBuilder();  //作者
			StringBuilder g_Comment = new StringBuilder();  //"注释"
			for (int k = p; k < Arr.length && k < m_nMaxRow; k++) {
				if (Arr[k].trim().toLowerCase(Locale.getDefault()).startsWith("title:")) {  //匹配 Title，标题
					g_Title.append(Arr[k].substring(Arr[k].indexOf(":")+1).trim());
				} else if (Arr[k].trim().toLowerCase(Locale.getDefault()).startsWith("author:")) {  //匹配 Author，作者
					g_Author.append(Arr[k].substring(Arr[k].indexOf(":")+1).trim());
				} else if (Arr[k].trim().toLowerCase(Locale.getDefault()).startsWith("comment:")) {  //匹配 Comment，"注释"块开始
					if (!Arr[k].substring(Arr[k].indexOf(":")+1).trim().equals(""))
						g_Comment.append(Arr[k].substring(Arr[k].indexOf(":")+1).trim()).append('\n');
					flg2 = true;
				} else if (Arr[k].trim().toLowerCase(Locale.getDefault()).startsWith("comment-end:")) {
					flg2 = false;
				} else if (Arr[k].trim().toLowerCase(Locale.getDefault()).startsWith("comment_end:")) {
					flg2 = false;
				} else if (Arr[k].trim().toLowerCase(Locale.getDefault()).startsWith("solution:")) {
					flg2 = false;
				} else {
					if (flg2) g_Comment.append(Arr[k]).append('\n');
				}
			}

			curMap = new mapNode(row, col, new_map, g_Title.toString(), g_Author.toString(), g_Comment.toString());

			return true;
		} catch (Exception e) {
			return false;
		}
	}

	//解析字符串中的第一个动作串（Lurd）并返回，flg = -1：仅逆推 lurd；flg = 1：仅正推 lurd；flg = 0：正逆推 lurd
	static String loadLURD(String str, int flg) {
		int index = str.toLowerCase(Locale.getDefault()).indexOf("solution");
		if (index >= 0) {
			str = str.substring(index + 8);
			str = str.substring(str.indexOf(")") + 1);
			str = str.substring(str.indexOf(":") + 1);
		} else {
			index = str.toLowerCase(Locale.getDefault()).indexOf("comment-end:");
			if (index >= 0) {
				str = str.substring(index + 12);
			} else {
				index = str.toLowerCase(Locale.getDefault()).indexOf("comment_end:");
				if (index >= 0) {
					str = str.substring(index + 12);
				} else {
					index = str.toLowerCase(Locale.getDefault()).indexOf("comment:");
					if (index >= 0) {
						return "";
					} else {
						index = str.toLowerCase(Locale.getDefault()).indexOf("author:");
						int index2 = str.toLowerCase(Locale.getDefault()).indexOf("title:");
						index = (index > index2 ? index : index2);
						if (index >= 0) {
							str = str.substring(index);
							index2 = str.toLowerCase(Locale.getDefault()).indexOf("\n");
							if (index2 >= 0) {
								str = str.substring(index2);
							} else {
								index2 = str.toLowerCase(Locale.getDefault()).indexOf("\r");
								if (index2 >= 0) {
									str = str.substring(index2);
								}
							}
						}
					}
				}
			}
		}
		//返回“串”中的第一个动作段
		try {
			index = 0;
			while ("lurdLURD[".indexOf(str.charAt(index)) < 0) index++;
			int index2 = index;
			try {
				while ("lurdLURD[0123456789,-] \t\n\r".indexOf(str.charAt(index2)) >= 0) index2++;
//				index2++;
			} catch (Exception e) {
				index2 = str.length();
			}
			str = str.substring(index, index2);

			index = str.indexOf("[");
			if (flg == 0) return str;  //返回正、逆推动作串
			else if (flg < 0) {  //仅返回逆推动作串
				if (index < 0) return str;  //没找到，则把全部动作视为逆推动作
				else return str.substring(index);
			} else {  //仅返回正推动作串
				if (index < 0) return str;  //没找到，则把全部动作视为正推动作
				else return str.substring(0, index);
			}
		} catch (Exception e) {
			return "";
		}
	}

	//解析创编中的关卡
	static void read_DirBuilder(){

		myMaps.m_lstMaps.clear();  //关卡集链表

		myMaps.m_Sets[0] = 3;
		myMaps.sFile = "创编关卡";
		myMaps.J_Title = "创编关卡";
		myMaps.J_Author = "";
		myMaps.J_Comment = "";
		myMaps.m_Set_id = -1;

		File myDir = new File(myMaps.sRoot+myMaps.sPath + "创编关卡/");  //创编的关卡文件夹
		if (!myDir.exists()) myDir.mkdirs();

		//取得"创编关卡"文件夹下的全部关卡
		String[] filelist = myDir.list();
		Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);

		InputStream fin;
		String my_Name;
		for (int i = 0; i < filelist.length; i++) {
			int dot = filelist[i].lastIndexOf('.');
			if ((dot > -1) && (dot < (filelist[i].length()))){
				String prefix = filelist[i].substring(filelist[i].lastIndexOf(".")+1);
				if (prefix.equalsIgnoreCase("xsb") ||
						prefix.equalsIgnoreCase("txt") ||
						prefix.equalsIgnoreCase("sok") ||
						prefix.equalsIgnoreCase("txz")) {

					//依次读入各文档中的关卡
					try{
						my_Name = new StringBuilder(myMaps.sRoot).append(myMaps.sPath).append("创编关卡/").append(filelist[i]).toString();
						fin = new FileInputStream(my_Name);

						int len = fin.available();
						byte[] Buf = new byte[len];
						fin.read(Buf);
						fin.close();

						if (myMaps.loadXSB(new String(Buf))) {
							myMaps.curMap.fileName = filelist[i];  //关卡文档名
							myMaps.m_lstMaps.add(myMaps.curMap);  //加入列表
						}
					} catch(Exception e) {
//						myMaps.curJi = false;
					}
				}
			}
		}
	}

	//判断是否为有效的 XSB 行，目前效率最高的判断方法
	static boolean isXSB(String str){
		if (str == null || str.matches("\\s*")) return false;  //排除空行的可能 || str.trim().isEmpty()

		int n = str.length();
		for(int k = 0; k < n; k++) { //判断是否全部由有效字符组成
			if (" _#-.$*@+".indexOf(str.charAt(k)) < 0){
				return false;
			}
		}
		return true;
	}

	//检查字符串是否为动作串（Lurd）
	static boolean isLURD(String str) {

		if (str == null || str.trim().isEmpty()) return false;  //排除空串的可能

		int n = str.length();
		for(int k = 0; k < n; k++) { //判断是否全部由有效字符组成
			if ("lurdLURD \t\n\r".indexOf(str.charAt(k)) < 0){
				return false;
			}
		}
		return true;
	}

	//检查字符串是否为逆推动作串（Lurd）
	static boolean isLURD2(String str) {

		if (str == null) return false;  //排除空串的可能 || str.trim().isEmpty()

		int n, m = str.indexOf(';');
		if (m < 0) {
			n = str.length();
		} else{
			n = m;
		}
		for(int k = 0; k < n; k++) { //判断是否全部由有效字符组成
			if ("lurdLURD[0123456789,-] \t\n\r".indexOf(str.charAt(k)) < 0){
				return false;
			}
		}
		return true;
	}

	//取得关卡现场
	static String getLocale(char[][] tm_cArray) {
		StringBuilder s = new StringBuilder();

		//关卡正推现场XSB
		for (int i = 0; i < tm_cArray.length; i++) {
			for (int j = 0; j < tm_cArray[0].length; j++) {
				s.append(tm_cArray[i][j]);
			}
			if (i < tm_cArray.length-1) s.append('\n');
		}
		return s.toString();
	}

//	//获取文件编码格式.
//	public static String getFileEncode(File fileName) {
//	    if (myMaps.isUTF8) return "UTF-8";
//
//		String charsetName = "GBK";
//		try {
//			BufferedInputStream bin = new BufferedInputStream(new FileInputStream(fileName));
//			int p = (bin.read() << 8) + bin.read();
//			//其中的 0xefbb、0xfffe、0xfeff、0x5c75这些都是这个文件的前面两个字节的16进制数
//			switch (p) {
//				case 0xefbb:
//					charsetName = "UTF-8";
//					break;
//				case 0xfffe:
//					charsetName = "Unicode";
//					break;
//				case 0xfeff:
//					charsetName = "UTF-16BE";
//					break;
//				case 0x5c75:
//					charsetName = "ANSI|ASCII" ;
//					break ;
//				default:
//					charsetName = "GBK";
//			}
//		} catch ( Exception e ) { }
//
//		return charsetName;
//	}

	static String getTxtEncode(FileInputStream in) {

		if (myMaps.m_Code == 2) return "UTF-8";
		else if (myMaps.m_Code == 1) return "GBK";

		String charsetName = Charset.defaultCharset().name();

		UnicodeInputStream uin = new UnicodeInputStream(in, charsetName);

		try {
			if("UTF-8".equals(uin.getEncoding())){
				uin.close();
				return "UTF-8";
			}
			uin.close();

			byte[] head = new byte[3];
			in.read(head);
			if (head[0] == -1 && head[1] == -2 )
				charsetName = "UTF-16";
			if (head[0] == -2 && head[1] == -1 )
				charsetName = "Unicode";
			//带BOM
			if(head[0]==-17 && head[1]==-69 && head[2] ==-65)
				charsetName = "UTF-8";
			if("Unicode".equals(charsetName))
				charsetName = "UTF-16";
		} catch ( Exception e ) { }

		return charsetName;
	}

	//列表框 Adapter
	public static class MyAdapter extends BaseAdapter {
		private Context context;
		private ArrayList<String> data;

		public MyAdapter(Context context,ArrayList<String> data){
			this.context = context;
			this.data = data;
		}

		@Override
		public int getCount() {
			return data.size();
		}

		@Override
		public Object getItem(int position) {
			return data.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView tv = new TextView(context);
			tv.setTextSize(16);
			tv.setText(data.get(position));
			//判断position位置是否被选中，改变颜色
			if(m_setName.isItemChecked(position)) {
				tv.setBackgroundColor(Color.parseColor("#0088aa"));
			} else {
				tv.setBackgroundColor(Color.parseColor("#363636"));
			}
			return tv;
		}
	}


}
