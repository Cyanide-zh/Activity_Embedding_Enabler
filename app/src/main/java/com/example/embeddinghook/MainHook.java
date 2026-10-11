package com.example.embeddinghook;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import java.lang.reflect.Proxy;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 严格排除系统核心进程，防止引发系统级异常
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("ActivityEmbeddingHook: [Injected] Target -> " + lpparam.packageName);

        // ==========================================
        // 维度一：欺骗尺寸（将最小宽度与尺寸伪装成平板/大屏，满足门槛一）
        // ==========================================
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
                                config.smallestScreenWidthDp = 720; // 强行拉高到 720dp 触发大屏判定
                            }
                            if (config.screenWidthDp < 720) {
                                config.screenWidthDp = 1280;
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked Resources.getConfiguration");
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] getConfiguration -> " + e.getMessage());
        }

        // ==========================================
        // 维度二：欺骗 WindowMetrics 边界（满足现代 WindowManager 尺寸计算）
        // ==========================================
        try {
            Class<?> windowMetricsClass = XposedHelpers.findClass("android.view.WindowMetrics", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(windowMetricsClass, "getBounds", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Rect rect = (Rect) param.getResult();
                    if (rect != null) {
                        if (rect.right < rect.bottom && rect.right < 2000) {
                            rect.right = 2560; // 模拟大屏宽度像素
                            param.setResult(rect);
                        }
                    }
                }
            });
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked WindowMetrics.getBounds");
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] WindowMetrics -> " + e.getMessage());
        }

        // ==========================================
        // 维度三：欺骗扩展层（Extensions 校验，满足门槛二：核心熔断解除）
        // 拦截 WindowExtensionsProvider.getWindowExtensions()，返回动态代理对象
        // ==========================================
        try {
            Class<?> providerClass = XposedHelpers.findClass(
                "androidx.window.extensions.WindowExtensionsProvider",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                providerClass,
                "getWindowExtensions",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object originalResult = param.getResult();
                        // 如果原返回值为 null（普通手机的必然表现），则为其动态伪造一个代理实现
                        if (originalResult == null) {
                            try {
                                Class<?> extensionsClass = XposedHelpers.findClass(
                                    "androidx.window.extensions.WindowExtensions",
                                    lpparam.classLoader
                                );

                                Object proxyExtensions = Proxy.newProxyInstance(
                                    lpparam.classLoader,
                                    new Class<?>[]{extensionsClass},
                                    (proxy, method, args) -> {
                                        String methodName = method.getName();
                                        Class<?> returnType = method.getReturnType();

                                        // 针对常见的组件获取方法，如获取分屏/嵌入组件，可根据需要返回代理或 null
                                        if (methodName.contains("EmbeddingComponent") || methodName.contains("SplitComponent")) {
                                            return null; 
                                        }

                                        // 基础类型默认返回值防御
                                        if (returnType.equals(boolean.class)) return true;
                                        if (returnType.equals(int.class)) return 0;
                                        if (returnType.equals(long.class)) return 0L;

                                        // 对象类型默认返回 null
                                        return null;
                                    }
                                );

                                param.setResult(proxyExtensions);
                                XposedBridge.log("ActivityEmbeddingHook: [Success] Faked WindowExtensionsProvider successfully for " + lpparam.packageName);
                            } catch (Throwable innerE) {
                                XposedBridge.log("ActivityEmbeddingHook: [Failed] Proxy creation error -> " + innerE.getMessage());
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked WindowExtensionsProvider.getWindowExtensions()");
        } catch (Throwable e) {
            // 部分没有引入 Jetpack Extensions 的应用找不到该类，属于正常现象，静默跳过
            XposedBridge.log("ActivityEmbeddingHook: [Info] WindowExtensionsProvider not present in this app.");
        }
    }
}
