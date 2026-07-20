package io.ionic.starter;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Created by benson on 2025/6/14.
 */

public class DisplayUtil {

    private static int screenWidth = 0;
    private static int screenHeight = 0;
    public static int getScreenWidth(Context context) {
        if (screenWidth > 0){
            return screenWidth;
        }
        getScreenSize(context);
        return screenWidth;
    }

    public static int getScreenHeight(Context context) {
        if (screenHeight > 0){
            return screenHeight;
        }
        getScreenSize(context);
        return screenHeight;
    }

    private static void getScreenSize(Context context){
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();// 创建了一张白纸
        windowManager.getDefaultDisplay().getRealMetrics(outMetrics);// 给白纸设置宽高
        screenWidth = outMetrics.widthPixels;
        screenHeight = outMetrics.heightPixels;
    }

}
