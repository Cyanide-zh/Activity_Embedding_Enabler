package com.example.embeddinghook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 1. 严格排除系统核心进程（system_server），防止引发系统级 Bootloop
        // 允许系统应用（如设置）和所有第三方应用正常加载
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        try {
            // 2. 寻找目标类 SplitController
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );

            // 3. Hook 获取分屏支持状态的方法
            XposedHelpers.findAndHookMethod(
                splitControllerClass,
                "getSplitSupportStatus",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // 4. 获取新版 API 的 SplitSupportStatus 类及 SPLIT_AVAILABLE 静态实例
                            Class<?> statusClass = XposedHelpers.findClass(
                                "androidx.window.embedding.SplitSupportStatus",
                                lpparam.classLoader
                            );
                            Object splitAvailable = XposedHelpers.getStaticObjectField(statusClass, "SPLIT_AVAILABLE");

                            if (splitAvailable != null) {
                                // 5. 强制将结果替换为可用状态
                                param.setResult(splitAvailable);
                                XposedBridge.log("ActivityEmbeddingHook: Faked SplitSupportStatus to SPLIT_AVAILABLE in " + lpparam.packageName);
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("ActivityEmbeddingHook: Failed to set SPLIT_AVAILABLE in " + lpparam.packageName + " -> " + e.getMessage());
                        }
                    }
                }
            );
        } catch (Throwable e) {
            // 忽略未集成 Jetpack WindowManager 的应用（静默失败，避免日志污染）
        }
    }
}
