package uk.gegc.kidsgptbackend.exception;

public class ModerationServiceException extends RuntimeException {
  public ModerationServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
