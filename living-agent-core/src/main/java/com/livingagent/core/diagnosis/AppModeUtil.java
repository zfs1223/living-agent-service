package com.livingagent.core.diagnosis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P12-C: 降级模式工具。
 * 读取/设置 System.getProperty("app.mode")，供各组件判断是否降级运行。
 */
public final class AppModeUtil {

    private static final Logger log = LoggerFactory.getLogger(AppModeUtil.class);
    private static final String APP_MODE_KEY = "app.mode";
    private static final String DEGRADED = "degraded";
    private static final String NORMAL = "normal";

    private AppModeUtil() {}

    public static boolean isDegraded() {
        return DEGRADED.equals(System.getProperty(APP_MODE_KEY));
    }

    public static void setDegraded(String reason) {
        System.setProperty(APP_MODE_KEY, DEGRADED);
        log.warn("App mode set to DEGRADED: {}", reason);
    }

    public static void clearDegraded() {
        System.setProperty(APP_MODE_KEY, NORMAL);
        log.info("App mode restored to NORMAL");
    }

    public static String getMode() {
        return System.getProperty(APP_MODE_KEY, NORMAL);
    }
}
