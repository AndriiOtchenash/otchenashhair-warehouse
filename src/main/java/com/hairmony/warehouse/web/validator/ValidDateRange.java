package com.hairmony.warehouse.web.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Cross-field date range validator.
 * Validates that the date in {@code endField} is not before the date in {@code startField}.
 * The validation error is attached to {@code endField}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {
    String message() default "{visit.error.nextVisitDateBeforeVisitDate}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    /** Name of the start date field (must be LocalDate). */
    String startField();

    /** Name of the end date field (must be LocalDate). Validation error is placed here. */
    String endField();
}
