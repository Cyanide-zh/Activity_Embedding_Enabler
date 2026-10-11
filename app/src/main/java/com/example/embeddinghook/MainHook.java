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

        // 1. 强改 Configuration 最小宽度（模拟平板）
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
        } catch (Throwable ignored) {}

        // 2. 强改 WindowMetrics 边界
        try {
            Class<?> windowMetricsClass = XposedHelpers.findClass("android.view.WindowMetrics", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(windowMetricsClass, "getBounds", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Rect rect = (Rect) param.getResult();
                    if (rect != null) {
                        if (rect.right < rect.bottom && rect.right < 2000) {
                            rect.right = 2560;
                            param.setResult(rect);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {}

        // 3. 终极杀招：Hook SplitController 的 getInstance 或构造函数，强行解除扩展限制
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );

            // 拦截 getInstance()，在获取到 SplitController 实例后，利用反射强行修改其内部字段
            XposedHelpers.findAndHookMethod(
                splitControllerClass,
                "getInstance",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object splitControllerInstance = param.getResult();
                        if (splitControllerInstance != null) {
                            try {
                                // 尝试通过反射遍历并修改所有可能的布尔型或状态型私有字段（如开启状态、是否支持嵌入等）
                                // 常见字段如 mIsEmbeddedSupportEnabled, mSplitSupportStatus 等
                                java.lang.reflect.Field[] fields = splitControllerClass.getDeclaredFields();
                                for (java.lang.reflect.Field field : fields) {
                                    field.setAccessible(true);
                                    Class<?> type = field.getType();
                                    if (type == boolean.class || type == Boolean.class) {
                                        // 将内部判定开关强制置为 true
                                        field.set(splitControllerInstance, true);
                                    }
                                }
                                XposedBridge.log("ActivityEmbeddingHook: [Success] Forced SplitController internal flags to true");
                            } catch (Throwable innerE) {
                                XposedBridge.log("ActivityEmbeddingHook: [Info] SplitController field hack skipped -> " + innerE.getMessage());
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked SplitController.getInstance()");
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] SplitController hook -> " + e.getMessage());
        }
    }
}
