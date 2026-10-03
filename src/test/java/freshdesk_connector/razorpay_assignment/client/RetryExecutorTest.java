package freshdesk_connector.razorpay_assignment.client;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.client.http.RetryExecutor;
import freshdesk_connector.razorpay_assignment.client.http.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryExecutorTest {

    @Test
    void testHonorsRetryAfterAndSucceeds() {
        RetryPolicy policy = new RetryPolicy(3, 100, 1000, 30);
        AtomicInteger sleeps = new AtomicInteger(0);
        RetryExecutor executor = new RetryExecutor(policy, millis -> sleeps.addAndGet((int) millis));

        AtomicInteger attempts = new AtomicInteger(0);
        RetryExecutor.ExecutionTracker tracker = new RetryExecutor.ExecutionTracker();

        String res = executor.execute("test-op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new FreshdeskException(ErrorCode.RATE_LIMITED, "Too many requests", true, 3, null, null);
            }
            return "SUCCESS";
        }, tracker);

        assertEquals("SUCCESS", res);
        assertEquals(2, attempts.get());
        assertEquals(3000, sleeps.get()); // 3s Retry-After honored
        assertEquals(2, tracker.getUpstreamCalls());
        assertEquals(1, tracker.getRetries());
    }

    @Test
    void testFailsFastWhenRetryAfterExceedsMaxWait() {
        RetryPolicy policy = new RetryPolicy(3, 100, 1000, 10);
        RetryExecutor executor = new RetryExecutor(policy, millis -> {});

        FreshdeskException ex = assertThrows(FreshdeskException.class, () ->
                executor.execute("test-fail-fast", () -> {
                    throw new FreshdeskException(ErrorCode.RATE_LIMITED, "Wait 60s", true, 60, null, null);
                }, null));

        assertEquals(ErrorCode.RATE_LIMITED, ex.getErrorCode());
        assertEquals(60, ex.getRetryAfterSeconds());
    }

    @Test
    void testDoesNotRetry500OrNonRetryableErrors() {
        RetryPolicy policy = new RetryPolicy(3, 100, 1000, 30);
        RetryExecutor executor = new RetryExecutor(policy, millis -> {});

        AtomicInteger attempts = new AtomicInteger(0);
        assertThrows(FreshdeskException.class, () ->
                executor.execute("test-500", () -> {
                    attempts.incrementAndGet();
                    throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "500 Internal Server Error", false);
                }, null));

        assertEquals(1, attempts.get());
    }
}