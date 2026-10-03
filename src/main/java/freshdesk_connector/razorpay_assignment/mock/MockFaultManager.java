package freshdesk_connector.razorpay_assignment.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("mock")
public class MockFaultManager {
    private final AtomicInteger remaining429 = new AtomicInteger(0);
    private int retryAfterSeconds = 2;
    private final AtomicInteger remaining500 = new AtomicInteger(0);
    private boolean inject401 = false;
    private int delayMillis = 0;

    public synchronized void arm429(int count, int retryAfterSec) {
        this.remaining429.set(count);
        this.retryAfterSeconds = retryAfterSec;
    }

    public synchronized void arm500(int count) {
        this.remaining500.set(count);
    }

    public synchronized void arm401(boolean enable) {
        this.inject401 = enable;
    }

    public synchronized void armDelay(int millis) {
        this.delayMillis = millis;
    }

    public synchronized void reset() {
        remaining429.set(0);
        retryAfterSeconds = 2;
        remaining500.set(0);
        inject401 = false;
        delayMillis = 0;
    }

    public int consume429() {
        return remaining429.getAndUpdate(v -> Math.max(0, v - 1));
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int consume500() {
        return remaining500.getAndUpdate(v -> Math.max(0, v - 1));
    }

    public boolean isInject401() {
        return inject401;
    }

    public int getDelayMillis() {
        return delayMillis;
    }
}