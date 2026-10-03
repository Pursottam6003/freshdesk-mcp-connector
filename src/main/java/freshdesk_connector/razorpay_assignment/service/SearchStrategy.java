package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.FreshdeskClient;
import freshdesk_connector.razorpay_assignment.client.dto.FdTicket;
import freshdesk_connector.razorpay_assignment.client.http.RetryExecutor;
import freshdesk_connector.razorpay_assignment.service.domain.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchStrategy {
    private final FreshdeskClient client;

    public SearchStrategy(FreshdeskClient client) {
        this.client = client;
    }

    public PageResult<Ticket> search(SearchQuery sq) {
        int limit = sq.limit() <= 0 ? 20 : Math.min(sq.limit(), 100);
        FreshdeskQueryBuilder.BuildResult buildResult = FreshdeskQueryBuilder.build(sq);

        RetryExecutor.ExecutionTracker tracker = new RetryExecutor.ExecutionTracker();
        List<FdTicket> upstreamCandidates = new ArrayList<>();
        int totalCandidatesUpstream = 0;
        int page = 1;
        boolean truncatedByUpstreamCap = false;

        // Freshdesk Search is bounded to max 10 pages (300 results)
        while (page <= 10) {
            FreshdeskClient.UpstreamPage<FdTicket> res = client.searchTickets(buildResult.queryStr(), page, tracker);
            totalCandidatesUpstream = res.totalCount();
            upstreamCandidates.addAll(res.data());

            if (!res.hasNextPage() || upstreamCandidates.size() >= totalCandidatesUpstream) {
                break;
            }
            page++;
        }

        if (totalCandidatesUpstream > 300) {
            truncatedByUpstreamCap = true;
        }

        List<Ticket> filtered = new ArrayList<>();
        String[] keywords = (sq.query() == null || sq.query().isBlank())
                ? new String[0]
                : sq.query().toLowerCase().trim().split("\\s+");

        for (FdTicket candidate : upstreamCandidates) {
            if (matchesKeywords(candidate, keywords)) {
                filtered.add(mapTicket(candidate));
            }
        }

        int totalMatches = filtered.size();
        List<Ticket> resultTickets = filtered.stream().limit(limit).toList();
        boolean exhaustive = !truncatedByUpstreamCap && (totalCandidatesUpstream <= 300);

        SearchScope searchScope = new SearchScope(
                buildResult.serverFiltersApplied(),
                "client_side_subject_and_tags_only",
                "Not Freshdesk full-text search; description and conversation text are not searched.",
                300,
                totalCandidatesUpstream,
                upstreamCandidates.size(),
                exhaustive,
                truncatedByUpstreamCap,
                "Freshdesk search results can lag recent updates by a few minutes; archived tickets are excluded."
        );

        Meta meta = new Meta(client.isMockSource() ? "mock" : "freshdesk", tracker.getUpstreamCalls(), tracker.getRetries());

        return new PageResult<>(
                resultTickets, null, null, resultTickets.size(), totalMatches, null, truncatedByUpstreamCap,
                null, searchScope, null, null, null, null, meta
        );
    }

    private boolean matchesKeywords(FdTicket ticket, String[] keywords) {
        if (keywords.length == 0) return true;
        String subject = ticket.subject() == null ? "" : ticket.subject().toLowerCase();
        List<String> tags = ticket.tags() == null ? List.of() : ticket.tags().stream().map(String::toLowerCase).toList();

        return Arrays.stream(keywords).allMatch(kw ->
                subject.contains(kw) || tags.stream().anyMatch(t -> t.contains(kw))
        );
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