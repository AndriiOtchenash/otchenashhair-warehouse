package com.hairmony.warehouse.web.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.stream.Collectors;

/**
 * Global exception handler for MVC controllers.
 * Catches uncaught infrastructure exceptions and converts them to user-friendly flash messages.
 * Business exceptions (IllegalStateException) are still handled locally in each controller.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * EntityNotFoundException — resource not found (e.g. unknown ID in URL).
     * Redirects to home with an error flash message.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleEntityNotFound(EntityNotFoundException ex,
                                       RedirectAttributes redirectAttributes) {
        log.warn("Entity not found: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/";
    }

    /**
     * DataIntegrityViolationException — DB unique constraint, FK violation, column truncation.
     * Covers cases where DTO @Size limits were bypassed (e.g. direct API call).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex,
                                      RedirectAttributes redirectAttributes) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String cause = ex.getMostSpecificCause().getMessage();
        String userMessage;
        if (cause != null && cause.contains("unique") || cause != null && cause.contains("duplicate")) {
            userMessage = "Помилка: значення вже існує (унікальне поле). Перевірте, чи не дублюєте запис.";
        } else {
            userMessage = "Помилка збереження даних. Можливо, перевищено допустиму довжину поля або порушено цілісність даних.";
        }
        redirectAttributes.addFlashAttribute("errorMessage", userMessage);
        return "redirect:/";
    }

    /**
     * ConstraintViolationException — fired when @Validated is used on a controller
     * with @Min/@Max/@Size on @RequestParam (e.g. snooze days).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public String handleConstraintViolation(ConstraintViolationException ex,
                                            RedirectAttributes redirectAttributes) {
        log.warn("Request parameter constraint violation: {}", ex.getMessage());
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getMessage())
                .collect(Collectors.joining("; "));
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:/";
    }

    /**
     * MethodArgumentTypeMismatchException — wrong type for @PathVariable or @RequestParam
     * (e.g. /products/abc instead of /products/123).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                     RedirectAttributes redirectAttributes) {
        log.warn("Type mismatch for parameter '{}': value='{}'", ex.getName(), ex.getValue());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Невірний формат параметра запиту: " + ex.getName());
        return "redirect:/";
    }
}
