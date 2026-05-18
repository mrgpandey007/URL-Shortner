package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST Controller — the entry point for all HTTP requests.
 *
 * ENDPOINTS:
 *
 * POST /api/shorten          → Shorten a URL
 * GET  /{shortCode}          → Redirect to original URL
 * GET  /api/stats/{shortCode}→ Get click stats
 * DELETE /api/{shortCode}    → Delete a short URL
 *
 * WHY 301 vs 302 for redirect? This is a real interview question.
 *
 * 301 = Permanent Redirect
 * - Browser caches the redirect. Future clicks go directly to original URL.
 * - Consequence: your system is bypassed. Click count is NEVER updated.
 * - Good for SEO. Bad for analytics.
 *
 * 302 = Temporary Redirect
 * - Browser always asks your server first.
 * - Every click goes through your system → you can count it.
 * - Bad for SEO. Good for analytics.
 *
 * We use 302 because click tracking is a core feature.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final UrlService urlService;

    /**
     * POST /api/shorten
     * Request body: { "url": "https://...", "customAlias": "optional", "expiryHours": 24 }
     * Response: 201 Created with ShortenResponse body
     */
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shortenUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /{shortCode}
     * The redirect endpoint — the hot path. Must be as fast as possible.
     *
     * Flow: resolve from Redis → record click → return 302
     *
     * Note: recordClick is called AFTER resolving the URL.
     * If we reversed the order, a failed resolution would still increment the counter.
     */
    // Regex restricts this to Base62 codes only (letters + digits, 4-20 chars).
    // Without this, /{shortCode} is a greedy wildcard that intercepts EVERYTHING —
    // including /index.html, /favicon.ico, /style.css — before the static file
    // handler runs. Spring MVC evaluates more-specific patterns first, so the regex
    // keeps static resources reachable while still catching all valid short codes.
    @GetMapping("/{shortCode:[a-zA-Z0-9]{4,20}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        log.debug("Redirect request for: {}", shortCode);

        // Resolved from Redis (1ms) or DB (3-5ms)
        String originalUrl = urlService.resolveUrl(shortCode);

        // Track the click asynchronously (doesn't slow down redirect)
        // In production, this would be a @Async call or a message queue publish
        urlService.recordClick(shortCode);

        // 302 Temporary Redirect — forces browser to always check our server
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, originalUrl)
            .build();
    }

    /**
     * GET /api/stats/{shortCode}
     * Returns metadata: original URL, click count, creation date, expiry
     */
    @GetMapping("/api/stats/{shortCode}")
    public ResponseEntity<ShortenResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.getStats(shortCode));
    }

    /**
     * DELETE /api/{shortCode}
     * Deletes the URL mapping from DB and evicts from Redis cache.
     * Returns 204 No Content on success.
     */
    @DeleteMapping("/api/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
