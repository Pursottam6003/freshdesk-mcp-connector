package freshdesk_connector.razorpay_assignment.client.error;

public class FreshdeskException extends RuntimeException {
    private final ErrorCode errorCode;
    private final boolean retryable;
    private final Integer retryAfterSeconds;
    private final Object details;

    public FreshdeskException(ErrorCode errorCode, String message, boolean retryable, Integer retryAfterSeconds, Object details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
        this.details = details;
    }

    public FreshdeskException(ErrorCode errorCode, String message) {
        this(errorCode, message, false, null, null, null);
    }

    public FreshdeskException(ErrorCode errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, null, null, null);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Object getDetails() {
        return details;
    }
}