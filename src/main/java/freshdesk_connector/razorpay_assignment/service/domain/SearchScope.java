// SearchScope.java
package freshdesk_connector.razorpay_assignment.service.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SearchScope(
        @JsonProperty("server_filters_applied") List<String> serverFiltersApplied,
        @JsonProperty("keyword_matching") String keywordMatching,
        @JsonProperty("keyword_matching_note") String keywordMatchingNote,
        @JsonProperty("candidate_limit") int candidateLimit,
        @JsonProperty("candidates_total_upstream") int candidatesTotalUpstream,
        @JsonProperty("candidates_scanned") int candidatesScanned,
        boolean exhaustive,
        @JsonProperty("truncated_by_upstream_cap") boolean truncatedByUpstreamCap,
        @JsonProperty("indexing_note") String indexingNote
) {}