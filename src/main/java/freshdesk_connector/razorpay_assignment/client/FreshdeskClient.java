package freshdesk_connector.razorpay_assignment.client;

import freshdesk_connector.razorpay_assignment.client.dto.FdConversation;
import freshdesk_connector.razorpay_assignment.client.dto.FdSearchResponse;
import freshdesk_connector.razorpay_assignment.client.dto.FdTicket;
import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.client.http.FreshdeskHttp;
import freshdesk_connector.razorpay_assignment.client.http.RetryExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FreshdeskClient {
    private final FreshdeskHttp http;
    private final ObjectMapper objectMapper;

    public record UpstreamPage<T>(
            List<T> data,
            boolean hasNextPage,
            int totalCount
    ) {}

    public FreshdeskClient(FreshdeskHttp http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    public FdTicket getTicket(long ticketId, RetryExecutor.ExecutionTracker tracker) {
        String uri = "/api/v2/tickets/" + ticketId;
        FreshdeskHttp.HttpResponseHolder resp = http.get(uri, tracker);
        try {
            return objectMapper.readValue(resp.body(), FdTicket.class);
        } catch (IOException e) {
            throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "Failed to parse ticket JSON", false, null, null, e);
        }
    }

    public UpstreamPage<FdTicket> listTickets(int page, int perPage, String updatedSince, RetryExecutor.ExecutionTracker tracker) {
        StringBuilder sb = new StringBuilder("/api/v2/tickets?");
        sb.append("order_by=created_at&order_type=desc&per_page=").append(perPage).append("&page=").append(page);
        if (updatedSince != null && !updatedSince.isBlank()) {
            sb.append("&updated_since=").append(URLEncoder.encode(updatedSince, StandardCharsets.UTF_8));
        }

        FreshdeskHttp.HttpResponseHolder resp = http.get(sb.toString(), tracker);
        try {
            List<FdTicket> list = objectMapper.readValue(resp.body(), new TypeReference<>() {});
            return new UpstreamPage<>(list, resp.hasNextPage(), -1);
        } catch (IOException e) {
            throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "Failed to parse ticket listing JSON", false, null, null, e);
        }
    }

    public UpstreamPage<FdTicket> searchTickets(String query, int page, RetryExecutor.ExecutionTracker tracker) {
        String encodedQuery = URLEncoder.encode("\"" + query + "\"", StandardCharsets.UTF_8);
        String uri = "/api/v2/search/tickets?query=" + encodedQuery + "&page=" + page;

        FreshdeskHttp.HttpResponseHolder resp = http.get(uri, tracker);
        try {
            FdSearchResponse sr = objectMapper.readValue(resp.body(), FdSearchResponse.class);
            return new UpstreamPage<>(sr.results(), page < 10 && (page * 30 < sr.total()), sr.total());
        } catch (IOException e) {
            throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "Failed to parse search tickets response", false, null, null, e);
        }
    }

    public UpstreamPage<FdConversation> listConversations(long ticketId, int page, RetryExecutor.ExecutionTracker tracker) {
        String uri = "/api/v2/tickets/" + ticketId + "/conversations?page=" + page;
        FreshdeskHttp.HttpResponseHolder resp = http.get(uri, tracker);
        try {
            List<FdConversation> list = objectMapper.readValue(resp.body(), new TypeReference<>() {});
            return new UpstreamPage<>(list, resp.hasNextPage(), -1);
        } catch (IOException e) {
            throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "Failed to parse ticket conversations JSON", false, null, null, e);
        }
    }

    public boolean isMockSource() {
        return http.isMock();
    }
}