package com.app.footballprediction.exception;

/**
 * Thrown when a requested resource cannot be found.
 * Maps to HTTP 404 in the global exception handler.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String identifier;
    private final ErrorCode errorCode;

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s not found: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
        this.errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    }

    public ResourceNotFoundException(String resourceType, String identifier, ErrorCode errorCode) {
        super(String.format("%s not found: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
        this.errorCode = errorCode;
    }

    public ResourceNotFoundException(String resourceType, String identifier, Throwable cause) {
        super(String.format("%s not found: %s", resourceType, identifier), cause);
        this.resourceType = resourceType;
        this.identifier = identifier;
        this.errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getIdentifier() {
        return identifier;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

