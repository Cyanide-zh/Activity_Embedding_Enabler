package com.example.embeddinghook;

import android.content.res.Configuration;
import android.content.res.Resources;
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

        // 维度一：直接 Hook Resources 的构造或获取 Configuration 的地方，
        // 或者是 Hook Activity 的 attachBaseContext 统一修改其 Configuration 对象的 smallestScreenWidthDp
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onConfigurationChanged",
                Configuration.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Configuration config = (Configuration) param.args[0];
                        if (config != null) {
                            if (config.smallestScreenWidthDp < 600) {
                                config.smallestScreenWidthDp = 720; // 强行设为平板宽度
                            }
                            if (config.screenWidthDp < 720) {
                                config.screenWidthDp = 1280;
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked Activity.onConfigurationChanged for " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] onConfigurationChanged -> " + e.getMessage());
        }

        // 维度二：Hook Resources 的 getConfiguration 方法，凡是应用读取 Configuration 时，直接篡改其宽度
        try {
            XposedHelpers.findAndHookMethod(
                Resources.class,
                "getConfiguration",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Configuration config = (Configuration) param.getResult();
                        if (config != null) {
                            if (config.smallestScreenWidthDp < 600) {
                                config.smallestScreenWidthDp = 720;
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked Resources.getConfiguration for " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] getConfiguration -> " + e.getMessage());
        }

        // 维度三：Hook WindowMetrics 的 getBounds 边界
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

        // 维度四：安全探测 SplitController
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Found SplitController in " + lpparam.packageName);
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Info] SplitController not present");
        }
    }
}
