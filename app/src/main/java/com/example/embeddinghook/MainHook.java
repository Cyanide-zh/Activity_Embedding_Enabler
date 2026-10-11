package com.example.embeddinghook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 1. 排除系统核心进程，避免不必要的性能开销或潜在冲突
        if ("android".equals(lpparam.packageName)) {
            return;
        }

        // 2. Hook Jetpack WindowManager Extensions 的分屏支持状态判定
        // 现代支持 Activity Embedding 的 App 通常会调用 extensions 层的相关判断
        try {
            Class<?> splitControllerClass = XposedHelpers.findClass(
                "androidx.window.embedding.SplitController", 
                lpparam.classLoader
            );
    
            // 尝试 Hook 获取状态的方法或属性
            XposedHelpers.findAndHookMethod(
                splitControllerClass, 
                "getSplitSupportStatus", 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 如果返回的是枚举或特定对象，可以尝试将其篡改或直接返回对应可用状态
                        Object result = param.getResult();
                        if (result != null) {
                            XposedBridge.log("SplitSupportStatus current: " + result.toString());
                            // 若需要强行设为可用，可根据实际反编译出的枚举类进行替换
                        }
                    }
                }
            );
        } catch (Throwable e) {
            // 部分未集成该类库的应用会抛出 ClassNotFoundException，直接忽略即可
        }

        // 3. 补充：部分应用可能直接通过窗口宽度或 WindowMetrics 动态计算，
        // 如果上面方法未生效，可以结合前文提到的 WindowMetrics.getBounds 一起 Hook
    }
}
