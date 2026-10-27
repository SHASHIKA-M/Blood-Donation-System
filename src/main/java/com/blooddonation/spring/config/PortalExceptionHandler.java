package com.blooddonation.spring.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = com.blooddonation.spring.controller.PortalController.class)
public class PortalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public String handleAccessDenied(RuntimeException ex, RedirectAttributes redirects) {
        if ("Not authenticated".equals(ex.getMessage())) {
            redirects.addFlashAttribute("error", "Please log in to access this portal.");
            return "redirect:/login";
        }
        redirects.addFlashAttribute("error", "Access denied for your role.");
        return "redirect:/login";
    }
}
