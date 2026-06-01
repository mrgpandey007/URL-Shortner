package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenRequest {

    @NotBlank(message = "URL cannot be blank")
    @Size(max = 2048, message = "URL too long — max 2048 characters")
    @Pattern(
        regexp = "^https?://.*",
        message = "URL must start with http:// or https://"
    )
    private String url;

    @Size(max = 20, message = "Custom alias too long — max 20 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9-_]*$",
        message = "Custom alias can only contain letters, numbers, hyphens, underscores"
    )
    private String customAlias;

    private Integer expiryHours;
}
