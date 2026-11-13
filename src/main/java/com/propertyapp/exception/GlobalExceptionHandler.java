package com.propertyapp.exception;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.ErrorResponse;
import com.propertyapp.dto.publicapi.GoneListingDTO;
import com.propertyapp.util.CorrelationIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class    GlobalExceptionHandler {
    
    /** 410 with just enough to offer similar homes (ADR-0009 Amendment 2). Not an error worth logging. */
    @ExceptionHandler(ListingGoneException.class)
    public ResponseEntity<ApiResponse<GoneListingDTO>> handleListingGone(ListingGoneException ex) {
        ApiResponse<GoneListingDTO> body = ApiResponse.<GoneListingDTO>builder()
                .success(false)
                .message(ex.getMessage())
                .data(ex.getListing())
                .correlationId(CorrelationIdUtils.get())
                .build();
        return new ResponseEntity<>(body, HttpStatus.GONE);
    }

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
    
    /**
     * Unknown path, wrong method, wrong body type: the catch-all below answered these with 500
     * "unexpected error" (found S13 with POST /api/auth/login, which does not exist).
     */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleNoRoute(Exception ex, HttpServletRequest request) {
        HttpStatus status = ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException
                ? HttpStatus.METHOD_NOT_ALLOWED
                : ex instanceof org.springframework.web.HttpMediaTypeNotSupportedException
                        ? HttpStatus.UNSUPPORTED_MEDIA_TYPE : HttpStatus.NOT_FOUND;
        log.warn("{} {}: {}", status.value(), request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(status == HttpStatus.NOT_FOUND ? "Not found" : status.getReasonPhrase())
                .error(status.getReasonPhrase())
                .status(status.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        return new ResponseEntity<>(error, status);
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

    // Catches unique-constraint violations that slip past the application-level check
    // (e.g. two requests racing to claim the same phone/email simultaneously).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("This value is already in use. Please choose a different one.")
                .error("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
    
    // Two people changed the same row at once; the loser should reload, not see a server error.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex,
            HttpServletRequest request
    ) {
        log.warn("Concurrent update on {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Someone else just changed this. Please reload and try again.")
                .error("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Invalid value for " + ex.getName())
                .error("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.error("Malformed request body: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Malformed or invalid request body")
                .error("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
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
    
    /** A missing query parameter is the caller's mistake: 400, not 500. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message("Required parameter '" + ex.getParameterName() + "' is missing")
                .error("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit hit on {}", request.getRequestURI());
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Too Many Requests")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.get())
                .build();
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
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
