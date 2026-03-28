package com.payments.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ResourceNotFoundException extends PaymentException {
    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " not found with id: " + id, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resource, String field, String value) {
        super(resource + " not found with " + field + ": " + value, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
