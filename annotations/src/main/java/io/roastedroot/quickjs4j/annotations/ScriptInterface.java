package io.roastedroot.quickjs4j.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScriptInterface {
    Class<?> context() default Void.class;

    String[] excluded() default {
        "equals",
        "toString",
        "wait",
        "getClass",
        "hashCode",
        "notify",
        "notifyAll",
        "clone",
        "finalize"
    };
}
