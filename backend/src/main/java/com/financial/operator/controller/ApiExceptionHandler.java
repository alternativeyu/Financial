package com.financial.operator.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        if (isAppApi(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(appError(1001, e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", 400, "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = "参数错误";
        if (e.getBindingResult().getFieldError() != null) {
            String field = e.getBindingResult().getFieldError().getField();
            String defaultMessage = e.getBindingResult().getFieldError().getDefaultMessage();
            message = field + (defaultMessage == null || defaultMessage.isBlank() ? "参数错误" : defaultMessage);
        }
        if (isAppApi(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(appError(1001, message));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", 400, "message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e, HttpServletRequest request) {
        if (isAppApi(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(appError(9999, "系统异常"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", e.getMessage()));
    }

    private Map<String, Object> appError(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    private boolean isAppApi(HttpServletRequest request) {
        return request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/app/");
    }
}
