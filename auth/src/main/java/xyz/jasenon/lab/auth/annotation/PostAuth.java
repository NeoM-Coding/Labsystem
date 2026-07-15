package xyz.jasenon.lab.auth.annotation;

import xyz.jasenon.lab.auth.SourceType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PostAuth {

    SourceType entityType() default SourceType.none;

    String entityId() default "";

    Mode idMode() default Mode.Constant;

    String permission() default "";

}
