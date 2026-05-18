package com.urlshortener.util;

import org.springframework.stereotype.Component;

/**
 * Base62 Encoder — this is the algorithm you need to actually understand.
 *
 * WHY BASE62?
 * - Base62 chars: a-z (26) + A-Z (26) + 0-9 (10) = 62 characters
 * - URL-safe: no +, /, = that Base64 uses and that break URLs
 * - 6 chars = 62^6 = 56,800,235,584 (~56 billion) unique codes
 * - Human-friendly: short, readable, case-sensitive for more entropy
 *
 * HOW IT WORKS (the part you glossed over):
 * - We take a unique long ID from the database (auto-increment)
 * - Treat it like a number in base-62
 * - Convert by repeatedly dividing by 62, taking remainders
 * - Map each remainder to a character in BASE62_CHARS
 *
 * EXAMPLE: ID = 125
 *   125 / 62 = 2 remainder 1  → BASE62_CHARS[1]  = 'b'
 *     2 / 62 = 0 remainder 2  → BASE62_CHARS[2]  = 'c'
 *   Result (reversed) = "cb"
 *
 * This is NOT random — it's deterministic from the DB ID.
 * No collisions because each DB row has a unique ID.
 * That's the real insight behind using DB ID + Base62.
 */
@Component
public class Base62Encoder {

    private static final String BASE62_CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int BASE = 62;
    private static final int MIN_LENGTH = 6;

    /**
     * Encodes a database ID into a Base62 short code.
     *
     * @param id  The auto-incremented primary key from the DB. Must be > 0.
     * @return    A short alphanumeric code, minimum 6 characters.
     */
    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be a positive number. Got: " + id);
        }

        StringBuilder sb = new StringBuilder();
        long num = id;

        while (num > 0) {
            sb.append(BASE62_CHARS.charAt((int) (num % BASE)));
            num /= BASE;
        }

        // Pad to minimum length so all codes look uniform
        // e.g., ID=1 gives "a", padded to "aaaaaa"
        while (sb.length() < MIN_LENGTH) {
            sb.append(BASE62_CHARS.charAt(0));
        }

        // Reverse because we built the string from least-significant digit
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to its original ID.
     * Useful for debugging — not used in the main redirect flow.
     *
     * @param code  The short code to decode.
     * @return      The original database ID.
     */
    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + BASE62_CHARS.indexOf(c);
        }
        return result;
    }
}
