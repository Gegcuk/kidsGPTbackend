package uk.gegc.kidsgptbackend.shared.exception;

public class ApiError extends RuntimeException {
    public ApiError(String message) {
        super(message);
    }
}
