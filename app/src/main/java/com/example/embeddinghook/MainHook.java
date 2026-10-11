package com.example.embeddinghook;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import java.lang.reflect.Method;
import java.util.HashSet;
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

        // 1. 欺骗尺寸：修改 Resources.getConfiguration
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

        // 2. 欺骗尺寸：修改 WindowMetrics.getBounds
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

        // 3. 终极增强：Hook SplitController 并在获取实例后尝试注入规则或清空限制
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                splitControllerClass,
                "getInstance",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object controller = param.getResult();
                        if (controller != null) {
                            try {
                                // 尝试调用其内置的 clearSplitRules 或动态添加规则的方法（如果存在）
                                // 或者通过反射将后端状态置为可用
                                XposedBridge.log("ActivityEmbeddingHook: [Success] SplitController instance obtained in " + lpparam.packageName);
                                
                                // 遍历所有方法寻找诸如 registerRule / addRule 等方法
                                Method[] methods = splitControllerClass.getDeclaredMethods();
                                for (Method m : methods) {
                                    if (m.getName().contains("Rule") || m.getName().contains("Embedding")) {
                                        // 可以在此打印或尝试调用
                                    }
                                }
                            } catch (Throwable innerE) {
                                XposedBridge.log("ActivityEmbeddingHook: Rule inject error -> " + innerE.getMessage());
                            }
                        }
                    }
                }
            );
        } catch (Throwable e) {
            // 部分版本可能没有该方法，静默处理
        }
    }
}
