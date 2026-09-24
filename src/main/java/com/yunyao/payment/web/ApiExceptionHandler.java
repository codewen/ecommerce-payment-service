package com.yunyao.payment.web;

import com.yunyao.payment.application.CheckoutPersistenceService;
import com.yunyao.payment.port.CommerceToolsPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CheckoutPersistenceService.IdempotencyConflictException.class)
    ResponseEntity<ApiError> idempotency(CheckoutPersistenceService.IdempotencyConflictException error) {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", error.getMessage(), List.of());
    }

    @ExceptionHandler(CommerceToolsPort.CartConflictException.class)
    ResponseEntity<ApiError> cartConflict(CommerceToolsPort.CartConflictException error) {
        return response(HttpStatus.CONFLICT, "CART_VERSION_CONFLICT", error.getMessage(), List.of());
    }

    @ExceptionHandler(CommerceToolsPort.OutOfStockException.class)
    ResponseEntity<ApiError> outOfStock(CommerceToolsPort.OutOfStockException error) {
        return response(HttpStatus.CONFLICT, "OUT_OF_STOCK", error.getMessage(), error.skus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
        var details = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
    }

    @ExceptionHandler({IllegalArgumentException.class, java.util.NoSuchElementException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException error) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", error.getMessage(), List.of());
    }

    private static ResponseEntity<ApiError> response(HttpStatus status, String code,
                                                     String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(code, message, details, Instant.now()));
    }

    record ApiError(String code, String message, List<String> details, Instant timestamp) {}
}
