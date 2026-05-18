package com.hairmony.warehouse.web.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDate;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startField;
    private String endField;
    private String message;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.startField = annotation.startField();
        this.endField   = annotation.endField();
        this.message    = annotation.message();
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext ctx) {
        if (obj == null) return true;
        try {
            var wrapper = new BeanWrapperImpl(obj);
            LocalDate start = (LocalDate) wrapper.getPropertyValue(startField);
            LocalDate end   = (LocalDate) wrapper.getPropertyValue(endField);
            if (start == null || end == null) return true; // let @NotNull handle nulls
            if (end.isBefore(start)) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate(message)
                        .addPropertyNode(endField)
                        .addConstraintViolation();
                return false;
            }
            return true;
        } catch (Exception e) {
            return true; // field not found or wrong type — skip silently
        }
    }
}
