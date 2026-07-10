package com.ayrotek.exception;

public class EmsTimeoutException extends RuntimeException {

    public EmsTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
