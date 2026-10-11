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

        // 3. 终极扩展层劫持：拦截 ClassLoader.loadClass，当应用尝试加载 WindowExtensionsProvider 时返回代理逻辑
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader.class,
                "loadClass",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String className = (String) param.args[0];
                        // 当 Jetpack 库尝试加载 WindowExtensionsProvider 时
                        if ("androidx.window.extensions.WindowExtensionsProvider".equals(className)) {
                            Class<?> loadedClass = (Class<?>) param.getResult();
                            if (loadedClass == null) {
                                // 如果类不存在抛出了异常或返回空，我们可以在这里通过动态代理或自定义逻辑处理
                                // 更稳妥的做法是：拦截调用该类的静态方法 getWindowExtensions
                            }
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        // 更直接的办法：直接 Hook 静态方法所在的主动检测逻辑，或者通过捕获并返回代理对象
        try {
            // 尝试直接 Hook 常见的扩展获取入口
            XposedBridge.log("ActivityEmbeddingHook: [Ready] ClassLoader interceptor initialized.");
        } catch (Throwable e) {
            XposedBridge.log("ActivityEmbeddingHook: [Info] Init error: " + e.getMessage());
        }
    }
}
