package com.example.embeddinghook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 1. 排除系统核心进程，避免不必要的性能开销或潜在冲突
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        // 2. Hook Jetpack WindowManager Extensions 的分屏支持状态判定
        // 现代支持 Activity Embedding 的 App 通常会调用 extensions 层的相关判断
        try {
            XposedHelpers.findAndHookMethod(
                "androidx.window.extensions.embedding.SplitController",
                lpparam.classLoader,
                "getSplitSupportStatus", // 部分版本或厂商实现中可能方法名有所不同
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 强制篡改返回值，使其返回表示支持分屏的状态（通常为布尔值或特定枚举）
                        param.setResult(true);
                        XposedBridge.log("ActivityEmbeddingHook: [Global] Faked split support to true in " + lpparam.packageName);
                    }
                }
            );
        } catch (Throwable e) {
            // 部分未集成该类库的应用会抛出 ClassNotFoundException，直接忽略即可
        }

        // 3. 补充：部分应用可能直接通过窗口宽度或 WindowMetrics 动态计算，
        // 如果上面方法未生效，可以结合前文提到的 WindowMetrics.getBounds 一起 Hook
    }
}
