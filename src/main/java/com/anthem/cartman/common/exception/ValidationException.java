package com.anthem.cartman.common.exception;

import java.util.List;

/** Thrown when a request breaks a business rule. Maps to HTTP 400. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, List<?> details) {
        super(message + " " + details);
    }
}
