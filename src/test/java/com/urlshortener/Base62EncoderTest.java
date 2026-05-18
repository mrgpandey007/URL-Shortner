package com.urlshortener;

import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Base62Encoder.
 * These tests validate the core algorithm — the heart of the system.
 * If these fail, everything else is irrelevant.
 */
class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    @DisplayName("Encoded output should be minimum 6 characters")
    void encode_shouldReturnMinimumSixCharacters() {
        String code = encoder.encode(1L);
        assertTrue(code.length() >= 6, "Short code must be at least 6 chars, got: " + code.length());
    }

    @Test
    @DisplayName("Encoded output should only contain Base62 characters")
    void encode_shouldOnlyContainBase62Characters() {
        for (long i = 1; i <= 1000; i++) {
            String code = encoder.encode(i);
            assertTrue(code.matches("[a-zA-Z0-9]+"),
                "Code contains invalid characters: " + code);
        }
    }

    @Test
    @DisplayName("Different IDs must produce different codes — no collisions")
    void encode_shouldProduceUniqueCodesForUniqueIds() {
        Set<String> codes = new HashSet<>();
        int count = 100_000;

        for (long i = 1; i <= count; i++) {
            String code = encoder.encode(i);
            assertFalse(codes.contains(code), "COLLISION DETECTED at ID: " + i + " → " + code);
            codes.add(code);
        }

        assertEquals(count, codes.size(), "Expected " + count + " unique codes");
    }

    @Test
    @DisplayName("Decode(encode(id)) should return original id")
    void decode_shouldReverseEncoding() {
        for (long id = 1; id <= 1000; id++) {
            String code = encoder.encode(id);
            long decoded = encoder.decode(code);
            assertEquals(id, decoded, "Decode failed for ID: " + id + " → code: " + code);
        }
    }

    @Test
    @DisplayName("Large IDs should still encode correctly")
    void encode_shouldHandleLargeIds() {
        long largeId = 10_000_000L;
        String code = encoder.encode(largeId);
        assertNotNull(code);
        assertTrue(code.length() >= 6);
        assertEquals(largeId, encoder.decode(code));
    }

    @Test
    @DisplayName("Zero and negative IDs should throw exception")
    void encode_shouldThrowForInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(0L));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(-1L));
    }

    @Test
    @DisplayName("Sequential IDs should produce different-looking codes")
    void encode_sequentialIdsShouldNotBePredictablyIncremental() {
        String code1 = encoder.encode(1L);
        String code2 = encoder.encode(2L);
        String code3 = encoder.encode(3L);

        // They should all be different
        assertNotEquals(code1, code2);
        assertNotEquals(code2, code3);

        System.out.println("ID 1 → " + code1);
        System.out.println("ID 2 → " + code2);
        System.out.println("ID 3 → " + code3);
    }
}
