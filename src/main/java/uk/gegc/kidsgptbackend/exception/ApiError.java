package uk.gegc.kidsgptbackend.exception;

public class ApiError extends RuntimeException {
    public ApiError(String message) {
        super(message);
    }
}
