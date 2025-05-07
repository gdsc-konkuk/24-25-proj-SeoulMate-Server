package gdgoc.konkuk.sweetsan.seoulmateserver.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler for handling various exceptions across the application. Provides consistent error responses
 * for different types of exceptions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles all unhandled exceptions.
     *
     * @param ex      the exception
     * @param request the current request
     * @return a ResponseEntity with error details
     */
    @ExceptionHandler(Exception.class)
    public final ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles access denied exceptions.
     *
     * @param ex      the exception
     * @param request the current request
     * @return a ResponseEntity with error details
     */
    @ExceptionHandler(AccessDeniedException.class)
    public final ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                           WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handles invalid token exceptions.
     *
     * @param ex      the exception
     * @param request the current request
     * @return a ResponseEntity with error details
     */
    @ExceptionHandler(InvalidTokenException.class)
    public final ResponseEntity<ErrorResponse> handleInvalidTokenException(InvalidTokenException ex,
                                                                           WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles resource not found exceptions.
     *
     * @param ex      the exception
     * @param request the current request
     * @return a ResponseEntity with error details
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public final ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex,
                                                                               WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Standard error response class for all API errors.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Standard error response returned by API")
    public static class ErrorResponse {

        @Schema(description = "Timestamp when the error occurred", example = "2023-03-01T14:30:15.123")
        private LocalDateTime timestamp;

        @Schema(description = "Error message", example = "Resource not found")
        private String message;

        @Schema(description = "Detailed error information", example = "uri=/api/users/123")
        private String details;
    }
}
