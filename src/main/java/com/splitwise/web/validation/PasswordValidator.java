package com.splitwise.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<Password, String> {

    public static final int MIN_CHARACTERS = 8;
    /** All that bcrypt reads. */
    public static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String message = null;
        if (value.codePointCount(0, value.length()) < MIN_CHARACTERS) {
            message = "must be at least " + MIN_CHARACTERS + " characters";
        } else if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            message = "must be at most " + MAX_BYTES + " bytes (letters with accents and symbols take 2 to 4 each)";
        }
        if (message == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
