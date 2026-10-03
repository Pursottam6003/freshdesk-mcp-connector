package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.FreshdeskClient;
import freshdesk_connector.razorpay_assignment.client.dto.FdTicket;
import freshdesk_connector.razorpay_assignment.client.http.RetryExecutor;
import freshdesk_connector.razorpay_assignment.config.FreshdeskProperties;
import freshdesk_connector.razorpay_assignment.service.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListingStrategy {
    private final FreshdeskClient client;
    private final FreshdeskProperties properties;

    public ListingStrategy(FreshdeskClient client, FreshdeskProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public PageResult<Ticket> listTickets(TicketStatus status, Integer requestedLimit, String cursor, String updatedSince) {
        int limit = (requestedLimit == null || requestedLimit <= 0) ? 25 : Math.min(requestedLimit, 100);
        String fingerprint = CursorCodec.computeFingerprint(status == null ? null : status.name(), updatedSince);
        CursorCodec.ListCursorPayload decodedCursor = CursorCodec.decodeListCursor(cursor, fingerprint);

        int currentUpstreamPage = decodedCursor.nextUpstreamPage();
        int skipInPage = decodedCursor.skipInPage();
        int maxPagesBudget = properties.listing().maxPagesPerCall();

        RetryExecutor.ExecutionTracker tracker = new RetryExecutor.ExecutionTracker();
        List<Ticket> collected = new ArrayList<>();
        int pagesFetched = 0;
        int totalTicketsScanned = 0;
        boolean upstreamExhausted = false;
        int nextUpstreamPage = currentUpstreamPage;
        int nextSkipInPage = 0;

        while (collected.size() < limit && pagesFetched < maxPagesBudget) {
            FreshdeskClient.UpstreamPage<FdTicket> upstreamPage = client.listTickets(currentUpstreamPage, 100, updatedSince, tracker);
            pagesFetched++;
            List<FdTicket> items = upstreamPage.data();
            if (items.isEmpty()) {
                upstreamExhausted = true;
                break;
            }

            int startIndex = (pagesFetched == 1) ? skipInPage : 0;
            for (int i = startIndex; i < items.size(); i++) {
                totalTicketsScanned++;
                FdTicket fdTicket = items.get(i);
                if (matchesStatus(fdTicket, status)) {
                    collected.add(mapTicket(fdTicket));
                    if (collected.size() == limit) {
                        if (i + 1 < items.size()) {
                            nextUpstreamPage = currentUpstreamPage;
                            nextSkipInPage = i + 1;
                        } else {
                            nextUpstreamPage = currentUpstreamPage + 1;
                            nextSkipInPage = 0;
                            if (!upstreamPage.hasNextPage()) {
                                upstreamExhausted = true;
                            }
                        }
                        break;
                    }
                }
            }

            if (collected.size() < limit) {
                if (!upstreamPage.hasNextPage()) {
                    upstreamExhausted = true;
                    break;
                }
                currentUpstreamPage++;
                nextUpstreamPage = currentUpstreamPage;
                nextSkipInPage = 0;
            }
        }

        boolean hasMore = !upstreamExhausted && (collected.size() == limit || pagesFetched >= maxPagesBudget);
        String nextCursor = hasMore ? CursorCodec.encodeListCursor(nextUpstreamPage, nextSkipInPage, fingerprint) : null;

        ListingScan scan = new ListingScan(pagesFetched, totalTicketsScanned, maxPagesBudget);
        Map<String, String> window = Map.of(
                "updated_since", updatedSince != null ? updatedSince : "none",
                "note", "Without updated_since, Freshdesk list endpoint returns tickets created in the last 30 days only."
        );
        Meta meta = new Meta(client.isMockSource() ? "mock" : "freshdesk", tracker.getUpstreamCalls(), tracker.getRetries());

        return new PageResult<>(
                collected, null, null, collected.size(), null, hasMore, false, nextCursor, null, scan, window, null, null, meta
        );
    }

    private boolean matchesStatus(FdTicket ticket, TicketStatus status) {
        if (status == null) return true;
        Integer statusCode = ticket.status();
        if (statusCode == null) return false;
        if (status == TicketStatus.UNRESOLVED) {
            return statusCode == 2 || statusCode == 3 || statusCode == 6 || statusCode == 7;
        }
        return statusCode == status.getCode();
    }

    private Ticket mapTicket(FdTicket fd) {
        return new Ticket(
                fd.id(),
                fd.subject(),
                fd.descriptionText() != null ? fd.descriptionText() : fd.description(),
                TicketStatus.fromCode(fd.status()),
                TicketPriority.fromCode(fd.priority()),
                fd.requesterId(),
                fd.responderId(),
                fd.groupId(),
                fd.type(),
                fd.tags(),
                fd.customFields(),
                fd.createdAt(),
                fd.updatedAt()
        );
    }
}