package com.ayrotek.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NodeNotActiveException extends RuntimeException {
    public NodeNotActiveException(String message) {
        super(message);
    }
}
