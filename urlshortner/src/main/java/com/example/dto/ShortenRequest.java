package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ShortenRequest(
        @Schema(example = "https://example.com/some/very/long/path")
        @NotBlank
        @Pattern(regexp = "^https?://\\S+$", message = "must be an http or https URL")
        String url,

        @Schema(example = "my-promo", nullable = true,
                description = "Optional custom short code. A random code is generated when omitted.")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "must be 3-32 letters, digits, '-' or '_'")
        String alias,

        @Schema(example = "3600", nullable = true,
                description = "Optional lifetime in seconds. The link never expires when omitted.")
        @Positive
        Long ttl) {
}
