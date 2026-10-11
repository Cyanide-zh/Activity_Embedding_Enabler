package com.example.embeddinghook;

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

        // 1. 核心突破口：Hook WindowExtensionsProvider.getWindowExtensions()
        // 强行让其返回一个代理对象，欺骗应用当前系统具备 Window Extensions 扩展能力
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
                        if (originalResult == null) {
                            try {
                                // 获取 WindowExtensions 接口类
                                Class<?> extensionsClass = XposedHelpers.findClass(
                                    "androidx.window.extensions.WindowExtensions",
                                    lpparam.classLoader
                                );
                                
                                // 使用动态代理构造一个假的 WindowExtensions 实例
                                Object proxyExtensions = Proxy.newProxyInstance(
                                    lpparam.classLoader,
                                    new Class<?>[]{extensionsClass},
                                    (proxy, method, args) -> {
                                        String methodName = method.getName();
                                        // 打印被调用的底层扩展方法，方便调试
                                        // XposedBridge.log("Extensions method called: " + methodName);
                                        
                                        // 针对获取不同组件的方法返回对应的默认/伪造实现
                                        if (methodName.equals("getSplitComponent")) {
                                            return null; 
                                        }
                                        // 默认返回基础类型或 null
                                        if (method.getReturnType().equals(int.class)) return 0;
                                        if (method.getReturnType().equals(boolean.class)) return true;
                                        return null;
                                    }
                                );

                                param.setResult(proxyExtensions);
                                XposedBridge.log("ActivityEmbeddingHook: [Success] Faked WindowExtensions Provider in " + lpparam.packageName);
                            } catch (Throwable innerE) {
                                XposedBridge.log("ActivityEmbeddingHook: Proxy create failed -> " + innerE.getMessage());
                            }
                        }
                    }
                }
            );
        } catch (Throwable e) {
            // 部分未集成 extensions 的应用可能会找不到该 Provider，属于正常现象
            // XposedBridge.log("ActivityEmbeddingHook: WindowExtensionsProvider not found in " + lpparam.packageName);
        }

        // 2. 辅助：继续保留 WindowMetrics.getBounds 尺寸拉宽逻辑
        try {
            Class<?> windowMetricsClass = XposedHelpers.findClass("android.view.WindowMetrics", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(windowMetricsClass, "getBounds", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Rect rect = (Rect) param.getResult();
                    if (rect != null) {
                        if (rect.right < rect.bottom && rect.right < 2000) {
                            rect.right = 2560; // 强制模拟平板大宽屏
                            param.setResult(rect);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
