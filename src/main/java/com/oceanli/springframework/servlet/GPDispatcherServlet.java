package com.oceanli.springframework.servlet;

import com.oceanli.springframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPDispatcherServlet extends HttpServlet {

    //配置文件地址
    private static final String contextConfigLocation = "contextConfigLocation";
    //配置文件
    private Properties contextConfig = new Properties();
    //类列表
    private List<String> classNames = new ArrayList<String>();
    //IOC容器
    private Map<String, Object> ioc = new ConcurrentHashMap<String, Object>();
    //handlerMapping
    //private Map<String, Method> handlerMapping = new ConcurrentHashMap<String, Method>();
    private List<Handler> handlerMappings = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatcher(req, resp);
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        /*String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!!");
            return;
        }
        Method method = this.handlerMapping.get(url);
        //请求参数的Map
        Map<String,String[]> params = req.getParameterMap();
        //方法的形参类型列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //保存需要赋值参数的位置
        Object [] paramValues = new Object[parameterTypes.length];
        //获取方法形参上的注解二维数组：方法有多个参数，每个参数都有可能有注解，此处写死每个参数取第一个注解
        Annotation[][] pa = method.getParameterAnnotations();
        for (int i = 0; i < pa.length; i++) {
            Annotation[] annotations = pa[i];
            if (annotations == null || annotations.length == 0) {
                continue;
            }
            Annotation annotation = annotations[0];
            if (annotation instanceof GPRequestParam) {
                paramValues[i] = Arrays.toString(params.get(((GPRequestParam) annotation).value()))
                        .replaceAll("\\[|\\]","")
                        .replaceAll("\\s",",");
            }
        }
        for (int i = 0; i<parameterTypes.length; i++) {
            Class parameterType = parameterTypes[i];
            if (parameterType.equals(HttpServletRequest.class)) {
                paramValues[i] = req;
            } else if (parameterType.equals(HttpServletResponse.class)){
                paramValues[i] = resp;
            } else if (parameterType.equals(String.class)) {


                *//*GPRequestParam annotation = (GPRequestParam)parameterType.getAnnotation(GPRequestParam.class);
                if (params.containsKey(annotation.value())) {
                    for (Map.Entry<String,String[]> param : params.entrySet()){
                        String value = Arrays.toString(param.getValue())
                                .replaceAll("\\[|\\]","")
                                .replaceAll("\\s",",");
                        paramValues[i] = value;
                    }
                }*//*
            }
        }
        //投机取巧的方式
        String beanName = toFirstLowerString(method.getDeclaringClass().getSimpleName());
        try {
            method.invoke(ioc.get(beanName), paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }*/

        try {
            String url = req.getRequestURI();
            String contextPath = req.getContextPath();
            //请求参数的Map
            Map<String,String[]> params = req.getParameterMap();
            url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

            Handler handler = getHandler(url);
            if (handler == null) {
                resp.getWriter().write("404 Not Found!!!");
                return;
            }
            Map<String, Integer> paramIndexMapping = handler.paramIndexMapping;
            //方法的形参类型列表
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            //保存需要赋值参数的位置
            Object [] paramValues = new Object[parameterTypes.length];
            //循环handler下的参数列表，请求参数中存在需要赋值的参数，则复制到参数中
            for (Map.Entry<String, Integer> entry : paramIndexMapping.entrySet()) {

                Integer index = entry.getValue();
                String paramName = entry.getKey();
                if (paramName.equals(HttpServletRequest.class.getName())) {
                    paramValues[index] = req;
                    continue;
                }
                if (paramName.equals(HttpServletResponse.class.getName())) {
                    paramValues[index] = resp;
                    continue;
                }
                //若没有匹配则继续
                if (!params.containsKey(paramName)) {
                    continue;
                }
                String[] paramValue = params.get(paramName);
                String value = Arrays.toString(paramValue).replaceAll("\\[|\\]",
                        "").replaceAll(",\\s", ",");
                paramValues[index] = convert(parameterTypes[index], value);
            }
            handler.method.invoke(handler.controller, paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    private Object convert(Class<?> type,String value) {
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(String url) {

        if (handlerMappings.isEmpty()) {
            return null;
        }
        //正则匹配Url
        for (Handler h : handlerMappings) {
            Matcher matcher = h.pattern.matcher(url);
            if (matcher.matches()) {
                return h;
            }
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        String initParameter = config.getInitParameter(contextConfigLocation);
        loadConfig(initParameter);

        //2.扫描相关的类
        scanClass(contextConfig.getProperty("scanPackage"));
        //3.初始化
        initClass();
        System.out.println(ioc);
        //4.依赖注入
        doAutowired();
        //5.完成初始化handlerMapping
        initHandlerMapping();
    }

    private void initHandlerMapping() {
        /*if(ioc.isEmpty())return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }
            //获取 Controller 的 url 配置
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                GPRequestMapping annotation = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = annotation.value().trim();
            }
            Method[] methods = clazz.getMethods();
            for (Method method: methods) {
                //方法上没有GPRequestMapping注解的跳过
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }
                //获取method上的url配置
                GPRequestMapping annotation = method.getAnnotation(GPRequestMapping.class);
                String value = annotation.value();
                String reqUrl = (baseUrl + "/" + value).replaceAll("/+", "/");
                handlerMapping.put(reqUrl, method);
            }
        }*/

        if(ioc.isEmpty())return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //没有GPController注解的类则跳过
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }
            //获取 Controller 的 url 配置
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                GPRequestMapping annotation = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = annotation.value().trim();
            }
            Method[] methods = clazz.getMethods();
            for (Method method: methods) {
                //方法上没有GPRequestMapping注解的跳过
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }
                //获取method上的url配置
                GPRequestMapping annotation = method.getAnnotation(GPRequestMapping.class);
                String value = annotation.value();
                String reqUrl = ("/" + baseUrl + "/" + value).replaceAll("/+", "/");
                //匹配正则，加到handler中
                Pattern pattern = Pattern.compile(reqUrl);
                Handler handler = new Handler(entry.getValue(), method, pattern);
                handlerMappings.add(handler);
                System.out.println(handler);
            }

        }


    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //拿到每个管理的类的所有被声明的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (!field.isAnnotationPresent(GPAutowired.class)) {
                        continue;
                    }
                    GPAutowired annotation = field.getAnnotation(GPAutowired.class);
                    String beanName = annotation.value().trim();
                    if ("".equals(beanName)) {
                        beanName = field.getType().getName();
                    }
                    //不管愿意不愿意，强制赋值
                    field.setAccessible(true);
                    Object o = ioc.get(beanName);
                    //注入属性的值
                    try {
                        field.set(entry.getValue(), o);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
        }

    }

    private void initClass() {

        if (classNames == null || classNames.size() == 0) {
            return;
        }
        //循环所有需要通过spring管理的类
        for(String className: classNames) {
            try {
                //加载类
                Class<?> clazz = Class.forName(className);
                //判断类是否有GPController注解
                if (clazz.isAnnotationPresent(GPController.class)) {

                    String simpleName = clazz.getSimpleName();
                    //首字母小写
                    String beanName = toFirstLowerString(simpleName);
                    //放入IOC容器中
                    ioc.put(beanName, clazz.newInstance());
                    //判断类是否存在有GPService注解
                } else if (clazz.isAnnotationPresent(GPService.class)) {
                    //1、获取注解中自定义的beanName
                    GPService gpService = clazz.getAnnotation(GPService.class);
                    String beanName = gpService.value();
                    //2、若自定义beanName为空，则取默认类名首字母小写
                    if("".equals(beanName.trim())){
                        beanName = toFirstLowerString(clazz.getSimpleName());
                    }
                    //存入IOC容器MAP
                    ioc.put(beanName, clazz.newInstance());
                    //取出GPService注解的类所有实现的接口，将接口全限定类名为key，service实例为value存入IOC
                    Class<?>[] interfaces = clazz.getInterfaces();
                    if (interfaces != null) {
                        for (Class i: interfaces) {
                            String iSimpleName = i.getName();
                            String iBeanName = toFirstLowerString(iSimpleName);
                            ioc.put(iBeanName, clazz.newInstance());
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String toFirstLowerString(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void scanClass(String scanPackage) {

        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                scanClass(scanPackage + "." + file.getName());
            } else {
                String name = file.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                String className = scanPackage + "." + name.replace(".class", "");
                classNames.add(className);
            }
        }
    }

    private void loadConfig(String initParameter) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(initParameter);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Handler {

        protected Object controller;
        //执行方法
        protected Method method;
        //url的正则匹配
        protected Pattern pattern;
        //方法上的形参位置
        protected Map<String, Integer> paramIndexMapping;

        public Handler(Object controller, Method method, Pattern pattern) {

            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {

            //获取方法上的注解二维数组，方法有多个参数，每个参数有多个注解
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation a: parameterAnnotations[i]) {
                    if (a instanceof GPRequestParam) {
                        String paramName = ((GPRequestParam) a).value();
                        if (!"".endsWith(paramName)) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            //此处获取HttpServletRequest， HttpServletResponse
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i=0; i<parameterTypes.length; i++) {
                Class<?> clazz = parameterTypes[i];
                if (clazz.equals(HttpServletRequest.class) || clazz.equals(HttpServletResponse.class)) {
                    paramIndexMapping.put(clazz.getName(), i);
                }
            }
        }

        @Override
        public String toString() {
            return "Handler{" +
                    "controller=" + controller +
                    ", method=" + method +
                    ", pattern=" + pattern +
                    ", paramIndexMapping=" + paramIndexMapping +
                    '}';
        }
    }

}
