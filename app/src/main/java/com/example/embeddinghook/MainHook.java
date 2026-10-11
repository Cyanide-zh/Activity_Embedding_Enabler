package com.example.embeddinghook;

import android.content.res.Configuration;
import android.graphics.Rect;
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

        XposedBridge.log("ActivityEmbeddingHook: [Injected] Target -> " + lpparam.packageName);

        // ==========================================
        // 维度一：Hook Configuration 的尺寸与最小宽度 (核心根源)
        // Jetpack WindowManager 靠 smallestScreenWidthDp >= 600dp 来判定是否启用分屏
        // ==========================================
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.res.Resources",
                lpparam.classLoader,
                "updateConfiguration",
                Configuration.class,
                android.util.DisplayMetrics.class,
                android.content.res.CompatibilityInfo.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Configuration config = (Configuration) param.args[0];
                        if (config != null) {
                            // 强行把最小宽度和屏幕宽度改到 720dp（平板/折叠屏标准）
                            if (config.smallestScreenWidthDp < 600) {
                                config.smallestScreenWidthDp = 720;
                            }
                            if (config.screenWidthDp < 720) {
                                config.screenWidthDp = 1280;
                            }
                            if (config.screenHeightDp < 720) {
                                config.screenHeightDp = 800;
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked Resources.updateConfiguration for " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] updateConfiguration -> " + e.getMessage());
        }

        // ==========================================
        // 维度二：Hook WindowMetrics 的 getBounds 边界
        // ==========================================
        try {
            Class<?> windowMetricsClass = XposedHelpers.findClass("android.view.WindowMetrics", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(windowMetricsClass, "getBounds", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Rect rect = (Rect) param.getResult();
                    if (rect != null) {
                        if (rect.right < rect.bottom && rect.right < 2000) {
                            rect.right = 2560; // 模拟大屏宽度
                            param.setResult(rect);
                        }
                    }
                }
            });
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked WindowMetrics.getBounds for " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] WindowMetrics -> " + e.getMessage());
        }

        // ==========================================
        // 维度三：安全探测 SplitController（带独立防崩保护）
        // ==========================================
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Found SplitController in " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Info] SplitController not present (Normal for some apps)");
        }
    }
}
