// PageResult.java
package freshdesk_connector.razorpay_assignment.service.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResult<T>(
        List<T> tickets,
        List<T> conversations,
        Long ticketId,
        Integer returned,
        @JsonProperty("total_matches") Integer totalMatches,
        @JsonProperty("has_more") Boolean hasMore,
        @JsonProperty("truncated") Boolean truncated,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("search_scope") SearchScope searchScope,
        ListingScan scan,
        Map<String, String> window,
        @JsonProperty("private_notes_excluded") Integer privateNotesExcluded,
        @JsonProperty("private_notes_included") Boolean privateNotesIncluded,
        Meta meta
) {}