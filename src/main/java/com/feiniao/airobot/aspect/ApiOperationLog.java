package com.feiniao.airobot.aspect;

import java.lang.annotation.*;

/**
 * @Description: 日志切面注解
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface ApiOperationLog {

    /**
     * API 功能描述
     *
     * @return
     */
    String description() default "";

}


