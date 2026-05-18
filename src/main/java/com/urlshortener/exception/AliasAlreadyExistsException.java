package com.urlshortener.exception;

/**
 * Thrown when a client requests a custom alias that already exists.
 * Maps to HTTP 409 Conflict.
 */
public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String alias) {
        super("Custom alias already taken: " + alias);
    }
}
