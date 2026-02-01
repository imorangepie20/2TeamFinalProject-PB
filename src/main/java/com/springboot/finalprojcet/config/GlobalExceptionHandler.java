package com.springboot.finalprojcet.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        // Check for specific duplicate email message
        if ("이미 존재하는 이메일입니다.".equals(e.getMessage())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT) // 409 Conflict
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage(),
                            "error", e.getMessage() // For frontend compatibility
                    ));
        }

        // Default handling for other RuntimeExceptions
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", false,
                        "message", e.getMessage() != null ? e.getMessage() : "Unknown Error",
                        "error", e.getMessage() != null ? e.getMessage() : "Unknown Error"));
    }
}
