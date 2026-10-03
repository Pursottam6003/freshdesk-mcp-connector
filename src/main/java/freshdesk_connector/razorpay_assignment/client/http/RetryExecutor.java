package freshdesk_connector.razorpay_assignment.client.http;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class RetryExecutor {
    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);
    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public RetryExecutor(RetryPolicy policy, Sleeper sleeper) {
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public <T> T execute(String operationName, Supplier<T> supplier, ExecutionTracker tracker) {
        int attempt = 0;
        while (true) {
            attempt++;
            if (tracker != null) {
                tracker.recordAttempt();
            }
            try {
                return supplier.get();
            } catch (FreshdeskException ex) {
                if (!ex.isRetryable() || attempt > policy.maxAttempts()) {
                    throw ex;
                }

                long backoffMs;
                if (ex.getRetryAfterSeconds() != null) {
                    int retrySec = ex.getRetryAfterSeconds();
                    if (retrySec > policy.maxWaitSeconds()) {
                        log.warn("Retry-After ({}s) exceeds max wait ({}s). Failing fast.", retrySec, policy.maxWaitSeconds());
                        throw ex;
                    }
                    backoffMs = retrySec * 1000L;
                } else {
                    long exponential = policy.baseBackoffMs() * (1L << (attempt - 1));
                    long capped = Math.min(exponential, policy.maxBackoffMs());
                    // 20% jitter
                    long jitter = ThreadLocalRandom.current().nextLong((long) (capped * 0.2 + 1));
                    backoffMs = capped + jitter;
                }

                if (tracker != null) {
                    tracker.recordRetry();
                }

                log.info("Operation '{}' failed with code {}. Retrying attempt {}/{} in {} ms",
                        operationName, ex.getErrorCode(), attempt, policy.maxAttempts(), backoffMs);

                try {
                    sleeper.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new FreshdeskException(ErrorCode.TIMEOUT, "Operation interrupted during retry backoff", false, null, null, ie);
                }
            }
        }
    }

    public static class ExecutionTracker {
        private int upstreamCalls = 0;
        private int retries = 0;

        public void recordAttempt() {
            upstreamCalls++;
        }

        public void recordRetry() {
            retries++;
        }

        public int getUpstreamCalls() {
            return upstreamCalls;
        }

        public int getRetries() {
            return retries;
        }
    }
}