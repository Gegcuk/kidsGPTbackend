package uk.gegc.kidsgptbackend.exception;

public class CredentialUpdateException extends RuntimeException {
    public CredentialUpdateException(String message) {
        super(message);
    }
    
    public CredentialUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
} 