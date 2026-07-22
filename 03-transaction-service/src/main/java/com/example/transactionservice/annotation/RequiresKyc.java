package com.example.transactionservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark methods that require the user to have an APPROVED KYC status.
 * Can be applied to deposit, withdrawal, and transfer endpoints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresKyc {
}