package uk.gegc.kidsgptbackend.shared.exception;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
