package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.FreshdeskClient;
import freshdesk_connector.razorpay_assignment.client.dto.FdConversation;
import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.config.FreshdeskProperties;
import freshdesk_connector.razorpay_assignment.service.domain.Conversation;
import freshdesk_connector.razorpay_assignment.service.domain.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class TicketServiceTest {
    private FreshdeskClient client;
    private FreshdeskProperties properties;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(FreshdeskClient.class);
        properties = new FreshdeskProperties(
                "mock", "key", null, 5000, 10000,
                new FreshdeskProperties.RetryConfig(3, 100, 1000, 30),
                new FreshdeskProperties.ListingConfig(5),
                new FreshdeskProperties.ConversationsConfig(false, 5, 2000)
        );
        ticketService = new TicketService(client, properties);
    }

    @Test
    void testPrivateNotesExcludedByDefaultAndCounted() {
        FdConversation publicC = new FdConversation(1L, 10L, "Hello customer", "<p>Hello</p>", true, false, Instant.now(), Instant.now(), null, null);
        FdConversation privateC = new FdConversation(2L, 20L, "SECRET_INTERNAL_KEY", "<p>SECRET</p>", false, true, Instant.now(), Instant.now(), null, null);

        when(client.listConversations(eq(100L), eq(1), any()))
                .thenReturn(new FreshdeskClient.UpstreamPage<>(List.of(publicC, privateC), false, 2));

        PageResult<Conversation> result = ticketService.getTicketConversations(100L, false);

        assertEquals(1, result.conversations().size());
        assertEquals("Hello customer", result.conversations().get(0).body());
        assertEquals(1, result.privateNotesExcluded());
        assertFalse(result.privateNotesIncluded());

        // Verify secret text never entered conversation body
        assertFalse(result.conversations().get(0).body().contains("SECRET_INTERNAL_KEY"));
    }

    @Test
    void testPrivateNotesOptInRefusedWhenOperatorGateDisabled() {
        FreshdeskException ex = assertThrows(FreshdeskException.class, () ->
                ticketService.getTicketConversations(100L, true));
        assertEquals(ErrorCode.ACCESS_DENIED_BY_POLICY, ex.getErrorCode());
    }
}