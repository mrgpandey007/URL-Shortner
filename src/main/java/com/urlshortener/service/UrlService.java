package com.urlshortener.service;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.urlshortener.config.RedisConfig.URL_CACHE;

/**
 * Core business logic. This is where all the interesting decisions live.
 *
 * CACHING STRATEGY:
 * @Cacheable on resolveUrl → On first call, result is stored in Redis with key=shortCode.
 * Subsequent calls return from Redis without touching the database.
 * @CacheEvict on deleteUrl → When a URL is deleted, its Redis entry is removed immediately.
 * If we didn't evict, deleted URLs would still redirect for up to 24h (stale cache).
 *
 * TRANSACTION STRATEGY:
 * @Transactional on methods that write — ensures DB operations are atomic.
 * If the click count increment fails after the lookup, neither commits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;

    @Value("${app.base-url}")
    private String baseUrl;  // e.g., "https://short.ly"

    /**
     * SHORTEN A URL
     *
     * Flow:
     * 1. Check if this URL was already shortened (return existing if so)
     * 2. Handle custom alias if provided
     * 3. Save to DB → get auto-generated ID
     * 4. Encode ID to Base62 → update short_code in DB
     * 5. Return the short URL
     *
     * WHY save first then encode?
     * Because we need the DB-generated unique ID to produce a collision-free Base62 code.
     * The ID uniqueness guarantee comes from the DB auto-increment — not from us.
     */
    @Transactional
    public ShortenResponse shortenUrl(ShortenRequest request) {
        String originalUrl = request.getUrl();

        // Idempotency check: if we already shortened this URL, return the same short code.
        // Prevents database bloat from duplicate entries.
        if (request.getCustomAlias() == null || request.getCustomAlias().isBlank()) {
            Optional<Url> existing = urlRepository.findByOriginalUrl(originalUrl);
            if (existing.isPresent()) {
                log.info("URL already shortened. Returning existing code: {}", existing.get().getShortCode());
                return mapToResponse(existing.get());
            }
        }

        String shortCode;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            // Custom alias flow
            shortCode = request.getCustomAlias();
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(shortCode);
            }
        } else {
            // Auto-generate flow: save first to get the DB ID, then encode
            shortCode = null; // temporary placeholder
        }

        // Build and save the entity
        Url url = Url.builder()
            .originalUrl(originalUrl)
            .shortCode(shortCode != null ? shortCode : "TEMP") // overwritten below
            .expiresAt(request.getExpiryHours() != null
                ? LocalDateTime.now().plusHours(request.getExpiryHours())
                : null)
            .build();

        Url saved = urlRepository.save(url);

        // If auto-generating, now we have the DB ID → encode it
        if (shortCode == null) {
            String generatedCode = base62Encoder.encode(saved.getId());
            saved.setShortCode(generatedCode);
            saved = urlRepository.save(saved); // Update with real short code
        }

        log.info("Shortened URL. Original: {} → Code: {}", originalUrl, saved.getShortCode());
        return mapToResponse(saved);
    }

    /**
     * RESOLVE A SHORT CODE → ORIGINAL URL
     *
     * @Cacheable: On first call, hits DB, stores in Redis.
     * Future calls with same shortCode → served from Redis in ~1ms.
     * The cache key is the shortCode itself.
     *
     * Interview question: "What if the URL expires after caching?"
     * Answer: We check isExpired() even on cache hit. TTL alone isn't enough
     * because a 24h cache TTL could serve an expired link if expiry < cache TTL.
     */
    @Cacheable(value = URL_CACHE, key = "#shortCode", unless = "#result == null")
    public String resolveUrl(String shortCode) {
        log.debug("Cache miss for code: {}. Hitting database.", shortCode);

        Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (url.isExpired()) {
            log.warn("Attempt to access expired URL: {}", shortCode);
            throw new UrlNotFoundException(shortCode + " (link expired)");
        }

        return url.getOriginalUrl();
    }

    /**
     * Track the click — runs AFTER the redirect is issued.
     * Called asynchronously from the controller to not slow down the redirect.
     *
     * Why separate from resolveUrl?
     * Because resolveUrl is cached. If we incremented inside it,
     * the increment would only happen on cache misses — completely wrong.
     */
    @Transactional
    public void recordClick(String shortCode) {
        try {
            urlRepository.incrementClickCount(shortCode);
        } catch (Exception e) {
            // Click tracking is not critical path — log and swallow
            log.error("Failed to record click for {}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * GET STATS for a short URL
     */
    public ShortenResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return mapToResponse(url);
    }

    /**
     * DELETE a short URL
     * @CacheEvict ensures the Redis entry is removed immediately.
     * Without this, deleted URLs would still resolve for up to 24h.
     */
    @Transactional
    @CacheEvict(value = URL_CACHE, key = "#shortCode")
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));
        urlRepository.delete(url);
        log.info("Deleted URL with code: {}", shortCode);
    }

    private ShortenResponse mapToResponse(Url url) {
        return ShortenResponse.builder()
            .shortUrl(baseUrl + "/" + url.getShortCode())
            .shortCode(url.getShortCode())
            .originalUrl(url.getOriginalUrl())
            .createdAt(url.getCreatedAt())
            .expiresAt(url.getExpiresAt())
            .clickCount(url.getClickCount())
            .build();
    }
}
