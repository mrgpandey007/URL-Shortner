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

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public ShortenResponse shortenUrl(ShortenRequest request) {
        String originalUrl = request.getUrl();

        if (request.getCustomAlias() == null || request.getCustomAlias().isBlank()) {
            Optional<Url> existing = urlRepository.findByOriginalUrl(originalUrl);
            if (existing.isPresent()) {
                log.info("URL already shortened. Returning existing code: {}", existing.get().getShortCode());
                return mapToResponse(existing.get());
            }
        }

        String shortCode;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            shortCode = request.getCustomAlias();
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(shortCode);
            }
        } else {
            shortCode = null;
        }

        Url url = Url.builder()
            .originalUrl(originalUrl)
            .shortCode(shortCode != null ? shortCode : "TEMP")
            .expiresAt(request.getExpiryHours() != null
                ? LocalDateTime.now().plusHours(request.getExpiryHours())
                : null)
            .build();

        Url saved = urlRepository.save(url);

        if (shortCode == null) {
            String generatedCode = base62Encoder.encode(saved.getId());
            saved.setShortCode(generatedCode);
            saved = urlRepository.save(saved);
        }

        log.info("Shortened URL. Original: {} → Code: {}", originalUrl, saved.getShortCode());
        return mapToResponse(saved);
    }

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

    @Transactional
    public void recordClick(String shortCode) {
        try {
            urlRepository.incrementClickCount(shortCode);
        } catch (Exception e) {
            log.error("Failed to record click for {}: {}", shortCode, e.getMessage());
        }
    }

    public ShortenResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return mapToResponse(url);
    }

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
