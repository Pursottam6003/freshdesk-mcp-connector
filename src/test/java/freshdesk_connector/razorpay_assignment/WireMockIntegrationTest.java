package freshdesk_connector.razorpay_assignment;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.service.SearchQuery;
import freshdesk_connector.razorpay_assignment.service.TicketService;
import freshdesk_connector.razorpay_assignment.service.domain.PageResult;
import freshdesk_connector.razorpay_assignment.service.domain.Ticket;
import freshdesk_connector.razorpay_assignment.service.domain.TicketStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WireMockIntegrationTest {
    private static WireMockServer wireMockServer;

    @Autowired
    private TicketService ticketService;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("freshdesk.domain", () -> "testcorp");
        registry.add("freshdesk.api-key", () -> "valid-test-key");
        registry.add("freshdesk.base-url", () -> wireMockServer.baseUrl());
        registry.add("freshdesk.retry.max-attempts", () -> 2);
        registry.add("freshdesk.retry.base-backoff-ms", () -> 100);
    }

    @BeforeEach
    void reset() {
        wireMockServer.resetAll();
    }

    @Test
    void scenario1_getTicket_verifiesAuthAndNoIncludeConversations() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v2/tickets/123"))
                .withHeader("Authorization", matching("Basic dmFsaWQtdGVzdC1rZXk6WA==")) // base64("valid-test-key:X")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 123,
                                  "subject": "Wiremock Test",
                                  "description_text": "Plain desc",
                                  "status": 2,
                                  "priority": 3,
                                  "tags": ["test"]
                                }
                                """)));

        Ticket ticket = ticketService.getTicket(123L);
        assertEquals(123L, ticket.id());
        assertEquals("Wiremock Test", ticket.subject());

        // Verify request never had include=conversations
        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/v2/tickets/123"))
                .withoutHeader("include"));
    }

    @Test
    void scenario2_searchTickets_scopeAndKeywordMatching() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v2/search/tickets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "total": 1,
                                  "results": [
                                    {
                                      "id": 999,
                                      "subject": "Payment failure in billing",
                                      "status": 2,
                                      "priority": 4,
                                      "tags": ["payment", "stripe"]
                                    }
                                  ]
                                }
                                """)));

        PageResult<Ticket> result = ticketService.searchTickets(
                new SearchQuery("payment", TicketStatus.OPEN, null, null, null, null, 10));

        assertEquals(1, result.tickets().size());
        assertNotNull(result.searchScope());
        assertTrue(result.searchScope().exhaustive());
        assertEquals(300, result.searchScope().candidateLimit());
    }

    @Test
    void scenario3_retryOn429ThenSuccess() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v2/tickets/429"))
                .inScenario("RateLimit")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "1")
                        .withBody("{\"message\":\"Rate limited\"}"))
                .willSetStateTo("Succeeded"));

        wireMockServer.stubFor(get(urlEqualTo("/api/v2/tickets/429"))
                .inScenario("RateLimit")
                .whenScenarioStateIs("Succeeded")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 429, \"subject\": \"Recovered\"}")));

        Ticket ticket = ticketService.getTicket(429L);
        assertEquals("Recovered", ticket.subject());
    }

    @Test
    void scenario4_401AuthenticationFailed() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v2/tickets/401"))
                .willReturn(aResponse().withStatus(401)));

        FreshdeskException ex = assertThrows(FreshdeskException.class, () -> ticketService.getTicket(401L));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, ex.getErrorCode());
    }

    @Test
    void scenario5_404NotFound() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v2/tickets/404"))
                .willReturn(aResponse().withStatus(404)));

        FreshdeskException ex = assertThrows(FreshdeskException.class, () -> ticketService.getTicket(404L));
        assertEquals(ErrorCode.TICKET_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void scenario6_500InternalErrorNotRetried() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v2/tickets/500"))
                .willReturn(aResponse().withStatus(500)));

        FreshdeskException ex = assertThrows(FreshdeskException.class, () -> ticketService.getTicket(500L));
        assertEquals(ErrorCode.UNEXPECTED_RESPONSE, ex.getErrorCode());
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/api/v2/tickets/500")));
    }
}