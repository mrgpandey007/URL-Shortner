package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming request body for URL shortening.
 * Validation annotations ensure bad input is rejected at the controller
 * layer — before it ever touches your service or database.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenRequest {

    @NotBlank(message = "URL cannot be blank")
    @Size(max = 2048, message = "URL too long — max 2048 characters")
    // Regex validates it starts with http:// or https://
    @Pattern(
        regexp = "^https?://.*",
        message = "URL must start with http:// or https://"
    )
    private String url;

    // Optional: client can request a custom alias instead of auto-generated one
    // e.g., short.ly/my-brand instead of short.ly/aX9kL
    @Size(max = 20, message = "Custom alias too long — max 20 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9-_]*$",
        message = "Custom alias can only contain letters, numbers, hyphens, underscores"
    )
    private String customAlias;

    // Optional: expiry in hours (null = never expires)
    private Integer expiryHours;
}
