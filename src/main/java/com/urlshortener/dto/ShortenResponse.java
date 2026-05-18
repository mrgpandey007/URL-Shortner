package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What we send back to the client after shortening.
 * Keep it informative — the client may want to show stats or expiry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {

    private String shortUrl;       // e.g., https://short.ly/aX9kL
    private String shortCode;      // e.g., aX9kL
    private String originalUrl;    // The original long URL (confirm to client)
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;  // null if never expires
    private Long clickCount;
}
