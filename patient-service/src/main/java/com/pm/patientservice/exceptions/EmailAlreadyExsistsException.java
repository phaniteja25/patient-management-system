package com.pm.patientservice.exceptions;

public class EmailAlreadyExsistsException extends RuntimeException {
    public EmailAlreadyExsistsException(String message) {
        super(message);
    }
}
