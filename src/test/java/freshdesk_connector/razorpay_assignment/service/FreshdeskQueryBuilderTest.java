package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.service.domain.TicketPriority;
import freshdesk_connector.razorpay_assignment.service.domain.TicketStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FreshdeskQueryBuilderTest {

    @Test
    void testBuildsUnresolvedAndPriorities() {
        SearchQuery sq = new SearchQuery(
                "payment",
                TicketStatus.UNRESOLVED,
                List.of(TicketPriority.HIGH, TicketPriority.URGENT),
                "billing",
                "Incident",
                "2026-01-01",
                20
        );

        FreshdeskQueryBuilder.BuildResult res = FreshdeskQueryBuilder.build(sq);
        assertTrue(res.queryStr().contains("(status:2 OR status:3 OR status:6 OR status:7)"));
        assertTrue(res.queryStr().contains("(priority:3 OR priority:4)"));
        assertTrue(res.queryStr().contains("tag:'billing'"));
        assertTrue(res.queryStr().contains("type:'Incident'"));
        assertTrue(res.queryStr().contains("created_at:>'2026-01-01'"));

        assertTrue(res.serverFiltersApplied().contains("status:unresolved"));
        assertTrue(res.serverFiltersApplied().contains("priority:high|urgent"));
    }

    @Test
    void testGuards512CharLimit() {
        String longTag = "a".repeat(520);
        SearchQuery sq = new SearchQuery(null, null, null, longTag, null, null, 20);
        FreshdeskException ex = assertThrows(FreshdeskException.class, () -> FreshdeskQueryBuilder.build(sq));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }
}