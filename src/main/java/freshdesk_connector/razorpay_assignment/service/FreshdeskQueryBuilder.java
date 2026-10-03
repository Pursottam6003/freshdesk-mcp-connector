package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.service.domain.TicketPriority;
import freshdesk_connector.razorpay_assignment.service.domain.TicketStatus;

import java.util.ArrayList;
import java.util.List;

public class FreshdeskQueryBuilder {
    private static final int MAX_QUERY_LEN = 512;

    public record BuildResult(
            String queryStr,
            List<String> serverFiltersApplied
    ) {}

    public static BuildResult build(SearchQuery sq) {
        List<String> clauses = new ArrayList<>();
        List<String> filtersApplied = new ArrayList<>();

        if (sq.status() != null) {
            if (sq.status() == TicketStatus.UNRESOLVED) {
                clauses.add("(status:2 OR status:3 OR status:6 OR status:7)");
                filtersApplied.add("status:unresolved");
            } else {
                clauses.add("status:" + sq.status().getCode());
                filtersApplied.add("status:" + sq.status().name().toLowerCase());
            }
        }

        if (sq.priorities() != null && !sq.priorities().isEmpty()) {
            if (sq.priorities().size() == 1) {
                TicketPriority p = sq.priorities().get(0);
                clauses.add("priority:" + p.getCode());
                filtersApplied.add("priority:" + p.name().toLowerCase());
            } else {
                StringBuilder pb = new StringBuilder("(");
                List<String> pNames = new ArrayList<>();
                for (int i = 0; i < sq.priorities().size(); i++) {
                    TicketPriority p = sq.priorities().get(i);
                    if (i > 0) pb.append(" OR ");
                    pb.append("priority:").append(p.getCode());
                    pNames.add(p.name().toLowerCase());
                }
                pb.append(")");
                clauses.add(pb.toString());
                filtersApplied.add("priority:" + String.join("|", pNames));
            }
        }

        if (sq.tag() != null && !sq.tag().isBlank()) {
            String sanitizedTag = sanitizeLiteral(sq.tag());
            clauses.add("tag:'" + sanitizedTag + "'");
            filtersApplied.add("tag:" + sanitizedTag);
        }

        if (sq.type() != null && !sq.type().isBlank()) {
            String sanitizedType = sanitizeLiteral(sq.type());
            clauses.add("type:'" + sanitizedType + "'");
            filtersApplied.add("type:" + sanitizedType);
        }

        if (sq.createdAfter() != null && !sq.createdAfter().isBlank()) {
            String sanitizedDate = sanitizeLiteral(sq.createdAfter());
            clauses.add("created_at:>' " + sanitizedDate + "'");
            filtersApplied.add("created_at:>" + sanitizedDate);
        }

        String fullQuery = String.join(" AND ", clauses);
        if (fullQuery.length() > MAX_QUERY_LEN) {
            throw new FreshdeskException(ErrorCode.INVALID_REQUEST,
                    "Freshdesk search query exceeded 512 character limit (was " + fullQuery.length() + ")");
        }

        return new BuildResult(fullQuery, filtersApplied);
    }

    private static String sanitizeLiteral(String input) {
        return input.replace("'", "").replace("\"", "").replace("\\", "").trim();
    }
}