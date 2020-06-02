## xposed 中hook OkHttpClient网络请求模块

#### okhttp中hook的点有很多，这里选取`okhttp3.OkHttpClient.newCall()`作为入口，这样可以获取所有的原始请求

由于`classloader`的机制，我们可以这样处理hook点
```java
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
                });
            }
        });
```

这样我们通过`param.args[0]`拿到了Request，但是此·Request·非彼 Request，如果直接强转，将会有`okhttp3.Request cannot be cast to okhttp3.Request`，这里说明一点，我们hook的是okhttp3，采用一点取巧的方式，可以将被hook的`Request` 不管是3.1还是3.9的版本，统统变成我们指定的`Request`。

#### 鱼目混珠大法

```java
String str = toJson(param.args[0]);
Request request = gson.fromJson(str, Request.class);
```

采用序列化回去，再反序列化回来，拿到了想要的Request，接下来接可以做一点小事情，比如修改下某个参数，是很easy的


#### 日志功能
通过hook `newCall`可以拿到origin request，但拿不到完整的request，因为未执行到拦截器逻辑，且不说有相当一部分仁兄喜欢在拦截器中添加公共参数，这里我们根据拦截器的逻辑再寻找一个hook点。毫无疑问`CallServerInterceptor` 是个不错的hook点。很容易拿到想要的数据

```java
RealInterceptorChain realChain = gson.fromJson(str, RealInterceptorChain.class);
                        if (realChain != null) {
                            String requestStr = toJson(realChain.request());
                            Log.i(TAG, "RealInterceptorChain request json string " + requestStr);
                        }
```