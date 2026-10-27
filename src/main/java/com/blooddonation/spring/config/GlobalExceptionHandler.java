package com.blooddonation.spring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Real-world safety net: no stacktrace leaks to users, every error is logged
 * with a correlation-friendly message and the user gets a friendly redirect.
 * Runs at LOWEST precedence so specific handlers (e.g. PortalExceptionHandler)
 * always win for their controllers.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public String handleAny(Exception ex, RedirectAttributes redirects) {
        log.error("Unhandled web error: {}", ex.getMessage(), ex);
        redirects.addFlashAttribute("error", "Something went wrong. Please try again or contact support.");
        return "redirect:/";
    }
}
