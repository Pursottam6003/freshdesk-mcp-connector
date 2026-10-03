package freshdesk_connector.razorpay_assignment.service;

import  freshdesk_connector.razorpay_assignment.client.FreshdeskClient;
import  freshdesk_connector.razorpay_assignment.client.dto.FdConversation;
import  freshdesk_connector.razorpay_assignment.client.dto.FdTicket;
import  freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import  freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import  freshdesk_connector.razorpay_assignment.client.http.RetryExecutor;
import  freshdesk_connector.razorpay_assignment.config.FreshdeskProperties;
import  freshdesk_connector.razorpay_assignment.service.domain.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TicketService {
    private final FreshdeskClient client;
    private final FreshdeskProperties properties;
    private final ListingStrategy listingStrategy;
    private final SearchStrategy searchStrategy;

    public TicketService(FreshdeskClient client, FreshdeskProperties properties) {
        this.client = client;
        this.properties = properties;
        this.listingStrategy = new ListingStrategy(client, properties);
        this.searchStrategy = new SearchStrategy(client);
    }

    public PageResult<Ticket> listTickets(TicketStatus status, Integer limit, String cursor, String updatedSince) {
        return listingStrategy.listTickets(status, limit, cursor, updatedSince);
    }

    public PageResult<Ticket> searchTickets(SearchQuery query) {
        return searchStrategy.search(query);
    }

    public Ticket getTicket(long ticketId) {
        RetryExecutor.ExecutionTracker tracker = new RetryExecutor.ExecutionTracker();
        FdTicket fd = client.getTicket(ticketId, tracker);
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

    public PageResult<Conversation> getTicketConversations(long ticketId, Boolean includePrivateNotes) {
        boolean requestedPrivate = Boolean.TRUE.equals(includePrivateNotes);
        if (requestedPrivate && !properties.conversations().allowPrivateNotes()) {
            throw new FreshdeskException(ErrorCode.ACCESS_DENIED_BY_POLICY,
                    "Access denied by operator policy: private notes retrieval is disabled (FRESHDESK_ALLOW_PRIVATE_NOTES=false).");
        }

        RetryExecutor.ExecutionTracker tracker = new RetryExecutor.ExecutionTracker();
        int maxPages = properties.conversations().maxPages();
        int maxBodyLength = properties.conversations().maxBodyLength();

        List<Conversation> domainConversations = new ArrayList<>();
        int page = 1;
        boolean truncated = false;
        int privateNotesExcluded = 0;

        while (page <= maxPages) {
            FreshdeskClient.UpstreamPage<FdConversation> upstreamPage = client.listConversations(ticketId, page, tracker);
            List<FdConversation> rawList = upstreamPage.data();
            if (rawList.isEmpty()) {
                break;
            }

            for (FdConversation raw : rawList) {
                boolean isPrivate = (raw.isPrivate() == null) || Boolean.TRUE.equals(raw.isPrivate());

                if (isPrivate && !requestedPrivate) {
                    privateNotesExcluded++;
                    continue;
                }

                String rawBody = raw.bodyText() != null ? raw.bodyText() : (raw.body() != null ? raw.body() : "");
                boolean bodyTruncated = rawBody.length() > maxBodyLength;
                String truncatedBody = bodyTruncated ? rawBody.substring(0, maxBodyLength) : rawBody;

                domainConversations.add(new Conversation(
                        raw.id(),
                        raw.userId(),
                        truncatedBody,
                        bodyTruncated,
                        raw.incoming(),
                        raw.createdAt()
                ));
            }

            if (!upstreamPage.hasNextPage()) {
                break;
            }

            page++;
            if (page > maxPages) {
                truncated = true;
            }
        }

        Meta meta = new Meta(client.isMockSource() ? "mock" : "freshdesk", tracker.getUpstreamCalls(), tracker.getRetries());

        return new PageResult<>(
                null, domainConversations, ticketId, domainConversations.size(), null, null,
                truncated, null, null, null, null, privateNotesExcluded, requestedPrivate, meta
        );
    }
}