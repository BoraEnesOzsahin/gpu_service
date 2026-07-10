package com.ayrotek.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NodeNotActiveException.class)
    public ResponseEntity<Object> handleNodeNotActiveException(NodeNotActiveException ex, WebRequest request) {
        Map<String, Object> body = Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", "NODE_NOT_ACTIVE",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(EmsConnectionException.class)
    public ResponseEntity<Object> handleEmsConnectionException(EmsConnectionException ex, WebRequest request) {
        Map<String, Object> body = Map.of(
                "status", HttpStatus.BAD_GATEWAY.value(),
                "error", "EMS_CONNECTION_FAILED",
                "message", "Could not connect to EMS. Please try again later."
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(EmsTimeoutException.class)
    public ResponseEntity<Object> handleEmsTimeoutException(EmsTimeoutException ex, WebRequest request) {
        Map<String, Object> body = Map.of(
                "status", HttpStatus.GATEWAY_TIMEOUT.value(),
                "error", "EMS_TIMEOUT",
                "message", "EMS did not respond in time. Please try again later."
        );
        return new ResponseEntity<>(body, HttpStatus.GATEWAY_TIMEOUT);
    }
}
