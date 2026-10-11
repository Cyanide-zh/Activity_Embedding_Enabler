package com.example.embeddinghook;

import android.graphics.Rect;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 排除系统核心进程
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("ActivityEmbeddingHook: [Injected] Target package -> " + lpparam.packageName);

        // 1. 尝试 Hook WindowMetrics 的 getBounds 方法
        try {
            Class<?> windowMetricsClass = XposedHelpers.findClass("android.view.WindowMetrics", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(windowMetricsClass, "getBounds", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Rect rect = (Rect) param.getResult();
                    if (rect != null) {
                        // 如果是竖屏状态，强行拉宽右边界到 2560 像素，欺骗应用当前是大屏设备
                        if (rect.right < rect.bottom) {
                            if (rect.right < 2000) {
                                rect.right = 2560;
                                param.setResult(rect);
                                // 为了避免日志过载，这里可以按需打印一次
                                // XposedBridge.log("ActivityEmbeddingHook: Faked WindowMetrics bounds width to 2560 in " + lpparam.packageName);
                            }
                        }
                    }
                }
            });
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked WindowMetrics.getBounds for " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] Hook WindowMetrics.getBounds -> " + e.getMessage());
        }

        // 2. 尝试 Hook Jetpack WindowManager 的 WindowMetricsCalculatorCompat 尺寸计算
        try {
            Class<?> calculatorCompatClass = XposedHelpers.findClass(
                "androidx.window.layout.WindowMetricsCalculatorCompat",
                lpparam.classLoader
            );
            
            // 尝试 Hook 常见的方法计算，例如 computeWindowMetrics
            XposedHelpers.findAndHookMethod(
                calculatorCompatClass,
                "computeWindowMetrics",
                android.app.Activity.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("ActivityEmbeddingHook: [Hit] computeWindowMetrics invoked in " + lpparam.packageName);
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked WindowMetricsCalculatorCompat for " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Info] WindowMetricsCalculatorCompat not found or skipped in " + lpparam.packageName);
        }

        // 3. 尝试 Hook SplitController 的初始化或相关配置
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Found SplitController in " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Info] SplitController not present in " + lpparam.packageName);
        }
    }
}
