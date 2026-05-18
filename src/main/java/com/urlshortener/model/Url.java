package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Maps directly to the 'urls' table in PostgreSQL.
 *
 * CRITICAL DESIGN DECISION: short_code has a unique index.
 * Every redirect = a lookup by short_code. Without this index,
 * PostgreSQL does a full table scan — O(n) per request.
 * With it — B-tree lookup, O(log n). At 10M rows that's the
 * difference between 10ms and 0.01ms per query.
 */
@Entity
@Table(
    name = "urls",
    indexes = {
        // This is the index mentioned in your design. Don't forget it.
        // Without this line, everything else is pointless at scale.
        @Index(name = "idx_short_code", columnList = "short_code", unique = true)
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    // 6 chars of Base62. Fixed length, small footprint, fast index.
    @Column(name = "short_code", nullable = false, length = 10, unique = true)
    private String shortCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // click_count: can be used for analytics and rate limiting.
    // Note: incrementing this on every click creates write contention at high traffic.
    // Production fix: use async batched updates or a separate analytics table.
    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    // Optional: expiry support — useful for marketing campaign links
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
