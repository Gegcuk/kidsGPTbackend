package uk.gegc.kidsgptbackend.exception;

public class ConversationFormatException extends RuntimeException {
    
    public ConversationFormatException(String message) {
        super(message);
    }
    
    public ConversationFormatException(String message, Throwable cause) {
        super(message, cause);
    }
} 