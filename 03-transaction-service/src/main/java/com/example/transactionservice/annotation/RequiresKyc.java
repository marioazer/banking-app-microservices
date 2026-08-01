package com.example.transactionservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// @Target(METHOD) restricts this to only ever being placed on methods, not classes or fields
// @Retention(RUNTIME) is the important one, it means this annotation survives compilation and
// is still visible at runtime, which is exactly what KycEnforcementAspect needs to detect it
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresKyc {
}