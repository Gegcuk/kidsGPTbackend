package uk.gegc.kidsgptbackend.shared.exception;

public class ConversationFormatException extends RuntimeException {
    
    public ConversationFormatException(String message) {
        super(message);
    }
    
    public ConversationFormatException(String message, Throwable cause) {
        super(message, cause);
    }
} 