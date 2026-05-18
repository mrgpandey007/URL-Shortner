package com.urlshortener.exception;

/**
 * Thrown when a short code doesn't exist in DB or Redis.
 * Maps to HTTP 404 in the GlobalExceptionHandler.
 */
public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String shortCode) {
        super("No URL found for short code: " + shortCode);
    }
}
