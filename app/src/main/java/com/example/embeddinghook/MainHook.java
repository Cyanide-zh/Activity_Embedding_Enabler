package com.example.embeddinghook;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        // 策略一：尝试 Hook 常见的 SplitController 支持判断方法（兼容不同版本方法名）
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );
            
            // 遍历并尝试 Hook 可能存在的状态方法，避免死磕一个不存在的方法名
            // 部分版本可能没有对外暴露该方法，如果找不到会进入 catch
            XposedHelpers.findAndHookMethod(
                splitControllerClass,
                "isSplitSupported", // 尝试更常见的旧版或别名方法
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(true);
                        XposedBridge.log("ActivityEmbeddingHook: Faked isSplitSupported to true in " + lpparam.packageName);
                    }
                }
            );
        } catch (Throwable ignored) {
            // 该类或方法不存在时静默跳过
        }

        // 策略二（万能核心）：Hook WindowMetrics 的 getBounds，将屏幕横向宽度拉大，
        // 从而让 Jetpack WindowManager 内部自带的尺寸计算逻辑误以为当前是平板/折叠屏大屏
        try {
            Class<?> windowMetricsClass = XposedHelpers.findClass("android.view.WindowMetrics", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(windowMetricsClass, "getBounds", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Rect rect = (Rect) param.getResult();
                    if (rect != null) {
                        // 如果当前是竖屏状态（宽小于高），强行把宽度拉大（例如 2000 像素以上）
                        if (rect.right < rect.bottom) {
                            if (rect.right < 2000) {
                                rect.right = 2000; // 模拟大屏宽度
                                param.setResult(rect);
                            }
                        }
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook WindowMetrics error: " + e.getMessage());
        }

        // 策略三：Hook DisplayMetrics 调整密度，防止拉宽后 UI 变成微缩版
        try {
            XposedHelpers.findAndHookMethod(
                DisplayMetrics.class,
                "setTo",
                DisplayMetrics.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        DisplayMetrics metrics = (DisplayMetrics) param.thisObject;
                        // 适当调小 densityDpi（例如降到 240-320），让应用以大屏 UI 密度渲染
                        if (metrics.densityDpi > 320) {
                            metrics.densityDpi = 320;
                            metrics.density = 320f / DisplayMetrics.DENSITY_DEFAULT;
                        }
                    }
                }
            );
        } catch (Throwable e) {
            // 忽略
        }
    }
}
