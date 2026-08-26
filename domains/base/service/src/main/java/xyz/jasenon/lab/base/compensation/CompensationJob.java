package xyz.jasenon.lab.base.compensation;

import xyz.jasenon.lab.base.api.model.CompensationTask;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CompensationJob {
    String code();
    String cron();
    String zoneId() default "Asia/Shanghai";
    CompensationTask.MisfirePolicy misfire() default CompensationTask.MisfirePolicy.FIRE_ONCE;
    boolean enabled() default true;
}
