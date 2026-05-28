package com.propertyapp.exception;

import com.propertyapp.dto.common.ErrorResponse;
import com.propertyapp.util.CorrelationIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class    GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.error("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Resource Not Found")
                .status(HttpStatus.NOT_FOUND.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request
    ) {
        log.error("Bad request: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request
    ) {
        log.error("Unauthorized: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Unauthorized")
                .status(HttpStatus.UNAUTHORIZED.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(
            DisabledException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Account not verified. Please verify your email.")
                .error("Account Disabled")
                .status(HttpStatus.UNAUTHORIZED.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UnableToSendNotificationException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(
            UnableToSendNotificationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("UnableToSendNotification")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request
    ) {
        log.error("Duplicate resource: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Duplicate Resource")
                .status(HttpStatus.CONFLICT.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        log.error("Business exception: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Business Logic Error")
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> errors = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.add(fieldName + ": " + errorMessage);
        });
        
        log.error("Validation errors: {}", errors);
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Validation failed")
                .error("Validation Error")
                .status(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .errors(errors)
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        log.error("Authentication failed: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Invalid credentials")
                .error("Authentication Failed")
                .status(HttpStatus.UNAUTHORIZED.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.error("Access denied: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("You don't have permission to access this resource")
                .error("Access Denied")
                .status(HttpStatus.FORBIDDEN.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected error occurred", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("An unexpected error occurred")
                .error("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}