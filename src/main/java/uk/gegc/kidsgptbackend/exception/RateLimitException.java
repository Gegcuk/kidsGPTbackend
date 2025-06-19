package uk.gegc.kidsgptbackend.exception;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
