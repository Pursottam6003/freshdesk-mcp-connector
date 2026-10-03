package freshdesk_connector.razorpay_assignment.tool;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolErrorMapper {

    public record ToolErrorResult(
            boolean isError,
            Map<String, Object> structuredError
    ) {}

    public static ToolErrorResult toToolError(Throwable t) {
        Map<String, Object> err = new LinkedHashMap<>();
        if (t instanceof FreshdeskException fe) {
            err.put("code", fe.getErrorCode().name());
            err.put("message", fe.getMessage());
            err.put("retryable", fe.isRetryable());
            if (fe.getRetryAfterSeconds() != null) {
                err.put("retryAfterSeconds", fe.getRetryAfterSeconds());
            }
            if (fe.getDetails() != null) {
                err.put("details", fe.getDetails());
            }
        } else if (t instanceof IllegalArgumentException iae) {
            err.put("code", ErrorCode.INVALID_REQUEST.name());
            err.put("message", iae.getMessage());
            err.put("retryable", false);
        } else {
            err.put("code", ErrorCode.UNEXPECTED_RESPONSE.name());
            err.put("message", t.getMessage() != null ? t.getMessage() : "Internal server failure");
            err.put("retryable", false);
        }

        return new ToolErrorResult(true, err);
    }
}