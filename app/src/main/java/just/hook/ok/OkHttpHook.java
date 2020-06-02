package just.hook.ok;

import android.annotation.SuppressLint;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import okhttp3.internal.http.RealInterceptorChain;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by hana on 2020/6/1
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint({"ObsoleteSdkInt", "SetWorldReadable", "SetWorldWritable"})
public class OkHttpHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String TAG = "hana";
    private static final String package_name = "co.runner.app";
    private static final byte[] lock = new byte[1];
    private Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @Override
    public void handleLoadPackage(final LoadPackageParam lp) throws Throwable {
        if (!package_name.equals(lp.packageName)) {
            return;
        }
        Log.i(TAG, "handleLoadPackage:" + lp.packageName);
        hookOriginNewCall(lp);
        //hookLastInterceptor(lp);

    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {

    }

    private void hookOriginNewCall(final LoadPackageParam lp) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ClassLoader cl = ((Context) param.args[0]).getClassLoader();

                Class<?> aClass = cl.loadClass("okhttp3.OkHttpClient");
                Class<?> requestClass = cl.loadClass("okhttp3.Request");
                findAndHookMethod(aClass, "newCall", requestClass, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        try {
                            //okhttp3.Request cannot be cast to okhttp3.Request
                            String str = toJson(param.args[0]);
                            Log.i(TAG, "request json string " + str);
                            //Request request = gson.fromJson(str, Request.class);
                            //set result 修改请求参数
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Log.i(TAG, "OkHttpClient afterHookedMethod package name = " + lp.packageName);
                    }
                });
            }
        });
    }

    private boolean classInDexFound = false;
    private final OnFindDexListener findDexListener = new OnFindDexListener() {
        @Override
        public void onFind(ClassLoader classLoader) {
            if (classInDexFound) {
                Log.i(TAG, "found in hide dex");
                return;
            }

            realHookLastInterceptor(classLoader);
        }
    };

    //java.lang.ClassNotFoundException: Didn't find class "okhttp3.Interceptor.Chain" on path: DexPathList
    private void hookLastInterceptor(final LoadPackageParam lp) {
        Log.i(TAG, "start hookLastInterceptor:" + lp.packageName);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ClassLoader cl = ((Context) param.args[0]).getClassLoader();

                try {
                    cl.loadClass("okhttp3.internal.http.CallServerInterceptor");
                    cl.loadClass("okhttp3.Interceptor.Chain");
                    classInDexFound = true;
                    realHookLastInterceptor(cl);
                } catch (Throwable e) {
                    if (e instanceof ClassNotFoundException) {
                        findHideDex(findDexListener);
                    } else {
                        e.printStackTrace();
                    }
                }

            }
        });
    }


    private void realHookLastInterceptor(final ClassLoader cl) {
        try {
            Class<?> aClass = cl.loadClass("okhttp3.internal.http.CallServerInterceptor");
            Class<?> chainClass = cl.loadClass("okhttp3.Interceptor.Chain");
            findAndHookMethod(aClass, "intercept", chainClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    classInDexFound = true;
                    try {
                        //RealInterceptorChain realChain = (RealInterceptorChain) chain;
                        String str = gson.toJson(param.args[0]);
                        Log.i(TAG, "RealInterceptorChain json string " + str);
                        RealInterceptorChain realChain = gson.fromJson(str, RealInterceptorChain.class);
                        if (realChain != null) {
                            String requestStr = toJson(realChain.request());
                            Log.i(TAG, "RealInterceptorChain request json string " + requestStr);
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Throwable e) {
            //ignore
        }
    }

    /**
     * 查找其他dex中class
     */
    private void findHideDex(final OnFindDexListener listener) {
        XposedBridge.hookAllMethods(ContextWrapper.class, "attachBaseContext", new XC_MethodHook() {

            public void beforeHookedMethod(MethodHookParam param) {
                ClassLoader classLoader = ((Context) param.args[0]).getClassLoader();
                if (classLoader == null) return;
                if (listener != null) listener.onFind(classLoader);
            }
        });
        XposedBridge.hookAllConstructors(ClassLoader.class, new XC_MethodHook() {
            public void beforeHookedMethod(MethodHookParam param) {
                ClassLoader classLoader = (ClassLoader) param.args[0];
                if (classLoader == null) return;
                if (listener != null) listener.onFind(classLoader);
            }
        });
    }


    private String toJson(Object object) {
        synchronized (lock) {
            return gson.toJson(object);
        }
    }

    private <T> T fromJson(String string, Class<T> classOfT) {
        synchronized (lock) {
            return gson.fromJson(string, classOfT);
        }
    }

    public interface OnFindDexListener {
        void onFind(ClassLoader classLoader);
    }

}