package freshdesk_connector.razorpay_assignment.mock;


import freshdesk_connector.razorpay_assignment.client.dto.FdConversation;
import freshdesk_connector.razorpay_assignment.client.dto.FdTicket;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("mock")
public class MockDataStore {
    private final Map<Long, FdTicket> tickets = new LinkedHashMap<>();
    private final Map<Long, List<FdConversation>> conversations = new ConcurrentHashMap<>();

    public MockDataStore() {
        initMockData();
    }

    private void initMockData() {
        Instant now = Instant.now();

        // 10 Curated Payment Failure Tickets
        for (long i = 1; i <= 10; i++) {
            FdTicket t = new FdTicket(
                    i,
                    "Payment failure for invoice #" + (1000 + i) + " - stripe charge declined",
                    "Customer reported card declined on checkout with code card_declined.",
                    "<p>Customer reported card declined on checkout with code card_declined.</p>",
                    2, // Open
                    4, // Urgent
                    100L + i,
                    200L,
                    300L,
                    400L,
                    "Incident",
                    List.of("fictional-demo", "payment", "stripe", "billing"),
                    Map.of("cf_environment", "production", "cf_plan", "enterprise"),
                    now.minus(i, ChronoUnit.DAYS),
                    now.minus(i, ChronoUnit.HOURS),
                    now.plus(1, ChronoUnit.DAYS),
                    now.plus(4, ChronoUnit.HOURS)
            );
            tickets.put(i, t);

            List<FdConversation> convList = new ArrayList<>();
            convList.add(new FdConversation(
                    i * 100 + 1,
                    100L + i,
                    "Hello, my payment failed at the checkout screen. Please help!",
                    "<p>Hello, my payment failed at the checkout screen. Please help!</p>",
                    true,
                    false,
                    now.minus(i, ChronoUnit.DAYS),
                    now.minus(i, ChronoUnit.DAYS),
                    "support@example.test",
                    List.of("customer" + i + "@example.test")
            ));
            convList.add(new FdConversation(
                    i * 100 + 2,
                    200L,
                    "INTERNAL NOTE: Gateway log shows 3D Secure failure. Fraud score 0.8.",
                    "<p>INTERNAL NOTE: Gateway log shows 3D Secure failure. Fraud score 0.8.</p>",
                    false,
                    true, // Private note
                    now.minus(i, ChronoUnit.DAYS).plus(10, ChronoUnit.MINUTES),
                    now.minus(i, ChronoUnit.DAYS).plus(10, ChronoUnit.MINUTES),
                    "support@example.test",
                    List.of()
            ));
            convList.add(new FdConversation(
                    i * 100 + 3,
                    200L,
                    "Hi, we noticed your card issuer declined the request. Could you try an alternative card?",
                    "<p>Hi, we noticed your card issuer declined the request. Could you try an alternative card?</p>",
                    false,
                    false,
                    now.minus(i, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES),
                    now.minus(i, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES),
                    "support@example.test",
                    List.of("customer" + i + "@example.test")
            ));
            conversations.put(i, convList);
        }

        // 50 Filler Tickets (Total 60 tickets)
        for (long i = 11; i <= 60; i++) {
            int status = (int) (2 + (i % 4)); // 2: Open, 3: Pending, 4: Resolved, 5: Closed
            int priority = (int) (1 + (i % 4));
            FdTicket t = new FdTicket(
                    i,
                    "General inquiry topic #" + i + " regarding account settings",
                    "User asked how to invite team members.",
                    "<p>User asked how to invite team members.</p>",
                    status,
                    priority,
                    100L + i,
                    201L,
                    301L,
                    401L,
                    "Question",
                    List.of("fictional-demo", "account"),
                    Map.of("cf_environment", "staging"),
                    now.minus(i * 12, ChronoUnit.HOURS),
                    now.minus(i, ChronoUnit.HOURS),
                    now.plus(2, ChronoUnit.DAYS),
                    now.plus(8, ChronoUnit.HOURS)
            );
            tickets.put(i, t);
        }
    }

    public Optional<FdTicket> getTicket(long id) {
        return Optional.ofNullable(tickets.get(id));
    }

    public List<FdTicket> getAllTicketsSorted() {
        return tickets.values().stream()
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    public List<FdConversation> getConversations(long ticketId) {
        return conversations.getOrDefault(ticketId, List.of());
    }
}