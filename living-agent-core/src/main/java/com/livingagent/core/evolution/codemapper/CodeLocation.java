package com.livingagent.core.evolution.codemapper;

import java.lang.annotation.*;

/**
 * 代码位置注解
 * 在关键类上标注代码位置信息，用于错误到代码的映射
 * 让大脑在遇到异常时能快速定位到具体代码文件和文档
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CodeLocation {
    /** 所属模块，如 core/brain, core/evolution */
    String module();
    /** 模块描述 */
    String description() default "";
    /** 相关文档引用 */
    String docRef() default "";
    /** 风险等级：LOW/MEDIUM/HIGH/CRITICAL */
    String riskLevel() default "MEDIUM";
}
