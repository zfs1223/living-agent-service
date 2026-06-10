package com.livingagent.gateway.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一权限检查注解，替代Controller中零散的 accessGateService.canRoute() 调用。
 *
 * 使用示例：
 * @RequireAccess(resource = "brain", action = "MainBrain")
 * public ResponseEntity<?> someMethod(@RequestHeader("X-Employee-Id") String employeeId) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAccess {
    /** 资源类型：brain, model, tool */
    String resource();
    /** 资源名称/动作 */
    String action();
    /** 是否要求FULL权限（默认false） */
    boolean requireFull() default false;
}
