package freshdesk_connector.razorpay_assignment.client.http;

public record RetryPolicy(
        int maxAttempts,
        long baseBackoffMs,
        long maxBackoffMs,
        int maxWaitSeconds
) {}