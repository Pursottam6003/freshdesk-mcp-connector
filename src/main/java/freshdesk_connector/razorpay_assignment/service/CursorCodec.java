package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class CursorCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record ListCursorPayload(
            String type,
            int nextUpstreamPage,
            int skipInPage,
            String filterFingerprint
    ) {}

    public static String encodeListCursor(int nextUpstreamPage, int skipInPage, String filterFingerprint) {
        try {
            ListCursorPayload payload = new ListCursorPayload("list", nextUpstreamPage, skipInPage, filterFingerprint);
            byte[] jsonBytes = MAPPER.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
        } catch (Exception e) {
            throw new FreshdeskException(ErrorCode.INVALID_REQUEST, "Failed to encode cursor", false, null, null, e);
        }
    }

    public static ListCursorPayload decodeListCursor(String cursor, String expectedFilterFingerprint) {
        if (cursor == null || cursor.isBlank()) {
            return new ListCursorPayload("list", 1, 0, expectedFilterFingerprint);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            ListCursorPayload payload = MAPPER.readValue(decoded, ListCursorPayload.class);
            if (!"list".equals(payload.type())) {
                throw new FreshdeskException(ErrorCode.INVALID_REQUEST, "Invalid cursor type: expected 'list' cursor but got '" + payload.type() + "'");
            }
            if (!expectedFilterFingerprint.equals(payload.filterFingerprint())) {
                throw new FreshdeskException(ErrorCode.INVALID_REQUEST, "Cursor filter fingerprint mismatch. The cursor cannot be reused with changed query parameters.");
            }
            return payload;
        } catch (FreshdeskException fe) {
            throw fe;
        } catch (Exception e) {
            throw new FreshdeskException(ErrorCode.INVALID_REQUEST, "Malformed or tampered pagination cursor", false, null, null, e);
        }
    }

    public static String computeFingerprint(String status, String updatedSince) {
        try {
            String raw = (status == null ? "" : status.trim().toLowerCase()) + "|" +
                    (updatedSince == null ? "" : updatedSince.trim());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (Exception e) {
            return "default";
        }
    }
}