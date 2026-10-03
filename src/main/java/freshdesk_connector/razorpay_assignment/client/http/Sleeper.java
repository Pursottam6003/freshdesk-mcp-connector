package freshdesk_connector.razorpay_assignment.client.http;

@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}