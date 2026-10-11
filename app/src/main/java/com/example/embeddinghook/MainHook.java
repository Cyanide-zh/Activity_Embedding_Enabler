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
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("ActivityEmbeddingHook: [Injected] Target -> " + lpparam.packageName);

        // 1. 欺骗尺寸：修改 Resources.getConfiguration
        try {
            XposedHelpers.findAndHookMethod(
                Resources.class,
                lpparam.classLoader,
                "getConfiguration",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Configuration config = (Configuration) param.getResult();
                        if (config != null) {
                            if (config.smallestScreenWidthDp < 600) {
                                config.smallestScreenWidthDp = 720;
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
            XposedBridge.log("ActivityEmbeddingHook: [Success] Hooked WindowMetrics.getBounds");
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Failed] WindowMetrics -> " + e.getMessage());
        }

        // 3. 终极扩展层拦截：使用字符串类名进行延迟 Hook（自动应对懒加载）
        try {
            XposedHelpers.findAndHookMethod(
                "androidx.window.extensions.WindowExtensionsProvider",
                lpparam.classLoader,
                "getWindowExtensions",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object originalResult = param.getResult();
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

                                        if (methodName.contains("EmbeddingComponent") || methodName.contains("SplitComponent")) {
                                            return null;
                                        }
                                        if (returnType.equals(boolean.class)) return true;
                                        if (returnType.equals(int.class)) return 0;
                                        if (returnType.equals(long.class)) return 0L;
                                        return null;
                                    }
                                );

                                param.setResult(proxyExtensions);
                                XposedBridge.log("ActivityEmbeddingHook: [Success] Successfully faked WindowExtensionsProvider via lazy hook!");
                            } catch (Throwable innerE) {
                                XposedBridge.log("ActivityEmbeddingHook: [Failed] Proxy creation error -> " + innerE.getMessage());
                            }
                        }
                    }
                }
            );
            XposedBridge.log("ActivityEmbeddingHook: [Success] Registered lazy hook for WindowExtensionsProvider");
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Info] WindowExtensionsProvider lazy hook registration failed: " + e.getMessage());
        }
    }
}
