package com.splitwise.web.validation;

import com.splitwise.web.CallerToday;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class PlausibleDateValidator implements ConstraintValidator<PlausibleDate, LocalDate> {

    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    public static final int MAX_YEARS_AHEAD = 1;

    private final CallerToday today;

    public PlausibleDateValidator(CallerToday today) {
        this.today = today;
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String message = null;
        if (value.isBefore(EARLIEST)) {
            message = "must be on or after " + EARLIEST;
        } else if (value.isAfter(today.get().plusYears(MAX_YEARS_AHEAD))) {
            message = "must be at most a year from today";
        }
        if (message == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
