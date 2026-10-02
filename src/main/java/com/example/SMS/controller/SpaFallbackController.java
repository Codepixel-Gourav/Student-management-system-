package com.example.SMS.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class SpaFallbackController {

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleMissingResource(
            NoResourceFoundException exception,
            HttpServletRequest request) throws NoResourceFoundException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isClientRoute(request, path)) {
            return new ModelAndView("forward:/index.html");
        }

        throw exception;
    }

    private boolean isClientRoute(HttpServletRequest request, String path) {
        String normalizedPath = path.toLowerCase();
        String finalSegment = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        return "GET".equals(request.getMethod())
                && request.getHeader("Accept") != null
                && request.getHeader("Accept").contains("text/html")
                && !normalizedPath.equals("/api")
                && !normalizedPath.startsWith("/api/")
                && !normalizedPath.equals("/actuator")
                && !normalizedPath.startsWith("/actuator/")
                && !finalSegment.contains(".");
    }
}
