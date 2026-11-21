package com.example.model;

import java.time.Instant;

/**
 * @param ttl    lifetime in seconds, or {@code null} if the link never expires
 * @param active {@code false} once the link has been deactivated
 */
public record ShortLink(String code, String url, Instant createdAt, Long ttl, boolean active) {

    public ShortLink(String code, String url, Instant createdAt, Long ttl) {
        this(code, url, createdAt, ttl, true);
    }

    public Instant expiresAt() {
        return ttl == null ? null : createdAt.plusSeconds(ttl);
    }

    public boolean isExpired(Instant now) {
        return ttl != null && !now.isBefore(expiresAt());
    }

    /** A link can be used only while it is active and not expired. */
    public boolean isUsable(Instant now) {
        return active && !isExpired(now);
    }

    public ShortLink deactivated() {
        return new ShortLink(code, url, createdAt, ttl, false);
    }
}
