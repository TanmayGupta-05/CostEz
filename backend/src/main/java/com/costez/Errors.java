package com.costez;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> api(ApiError e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                e.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .findFirst()
                    .orElse("Invalid input")));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate(DataIntegrityViolationException e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "This record conflicts with existing data. Check for duplicates."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> input(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Invalid input. Check amounts, dates and required fields."));
  }
}
