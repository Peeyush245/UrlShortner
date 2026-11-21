package com.example.dto;

import java.time.Instant;

import com.example.model.LinkStatus;

public record LinkMetadata(
        String code,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        long hitCount,
        LinkStatus status) {
}
