package com.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String BASE62_CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int BASE = 62;
    private static final int MIN_LENGTH = 6;

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

        while (sb.length() < MIN_LENGTH) {
            sb.append(BASE62_CHARS.charAt(0));
        }

        return sb.reverse().toString();
    }

    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + BASE62_CHARS.indexOf(c);
        }
        return result;
    }
}
