package com.ayrotek.exception;

import com.ayrotek.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MacAddressNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMacAddressNotFoundException(MacAddressNotFoundException ex, WebRequest request) {
        log.error("Fatal error during initialization: {}", ex.getMessage());
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                request.getDescription(false).substring(4) // "uri=" prefixini kaldır
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleCommandExecutionException(CommandExecutionException ex, WebRequest request) {
        log.error("A system command failed to execute: {}", ex.getMessage(), ex.getCause());
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "A required system command could not be executed. Check logs for details.",
                request.getDescription(false).substring(4)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(EmsConnectionException.class)
    public ResponseEntity<ApiErrorResponse> handleEmsConnectionException(EmsConnectionException ex, WebRequest request) {
        log.error("Unable to reach EMS: {}", ex.getMessage(), ex);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpStatus.BAD_GATEWAY,
                "EMS could not be reached. Check logs for details.",
                request.getDescription(false).substring(4)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(EmsTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleEmsTimeoutException(EmsTimeoutException ex, WebRequest request) {
        log.error("Timed out while calling EMS: {}", ex.getMessage(), ex);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpStatus.GATEWAY_TIMEOUT,
                "EMS request timed out. Check logs for details.",
                request.getDescription(false).substring(4)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.GATEWAY_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred. Please contact support.",
                request.getDescription(false).substring(4)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
