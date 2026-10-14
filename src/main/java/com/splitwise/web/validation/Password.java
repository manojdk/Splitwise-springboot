package com.splitwise.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A new password: at least 8 characters and at most 72 bytes in UTF-8. bcrypt reads only the first 72 bytes, so a
 * longer password would have a tail that is silently ignored; counting characters is not enough, since a letter
 * like "é" or a symbol like "₹" takes 2 or 3 bytes. Null is left to {@code @NotNull}/{@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    String message() default "must be at least 8 characters and at most 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
