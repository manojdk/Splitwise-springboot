package com.splitwise.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A date an expense or payment can really have: from 2000-01-01 to a year from the caller's today (in their time
 * zone, see {@link com.splitwise.web.CallerToday}). Catches typos like 2099 or 1925, which would otherwise sit at
 * the top or bottom of every list for good. Null is allowed (it means today).
 */
@Documented
@Constraint(validatedBy = PlausibleDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlausibleDate {

    String message() default "must be between 2000-01-01 and a year from today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
