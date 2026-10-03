package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CursorCodecTest {

    @Test
    void testEncodeDecodeRoundTrip() {
        String fingerprint = CursorCodec.computeFingerprint("OPEN", "2026-01-01T00:00:00Z");
        String encoded = CursorCodec.encodeListCursor(3, 45, fingerprint);

        assertNotNull(encoded);
        CursorCodec.ListCursorPayload decoded = CursorCodec.decodeListCursor(encoded, fingerprint);
        assertEquals(3, decoded.nextUpstreamPage());
        assertEquals(45, decoded.skipInPage());
        assertEquals(fingerprint, decoded.filterFingerprint());
    }

    @Test
    void testRejectsFingerprintMismatch() {
        String fp1 = CursorCodec.computeFingerprint("OPEN", null);
        String fp2 = CursorCodec.computeFingerprint("CLOSED", null);

        String encoded = CursorCodec.encodeListCursor(2, 10, fp1);
        FreshdeskException ex = assertThrows(FreshdeskException.class, () -> CursorCodec.decodeListCursor(encoded, fp2));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("fingerprint mismatch"));
    }

    @Test
    void testRejectsTamperedCursor() {
        assertThrows(FreshdeskException.class, () -> CursorCodec.decodeListCursor("bad-base64-content%%%", "any"));
    }
}