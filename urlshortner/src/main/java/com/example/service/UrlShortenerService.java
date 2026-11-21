package com.example.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.example.dto.LinkMetadata;
import com.example.exception.AliasUnavailableException;
import com.example.exception.LinkNotFoundException;
import com.example.model.LinkStatus;
import com.example.model.ShortLink;

@Service
public class UrlShortenerService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 7;

    private final Map<String, ShortLink> linksByCode = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> hitCounts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public UrlShortenerService(Clock clock) {
        this.clock = clock;
    }

    public ShortLink shorten(String url, String alias, Long ttl) {
        Instant now = clock.instant();

        if (alias != null) {
           
            ShortLink link = new ShortLink(alias, url, now, ttl);
            ShortLink existing = linksByCode.putIfAbsent(alias, link);
            if (existing == null) {
                hitCounts.put(alias, new AtomicLong());
                return link;
            }
        
            throw new AliasUnavailableException(alias);
        }

        while (true) {
            ShortLink link = new ShortLink(generateCode(), url, now, ttl);
            if (linksByCode.putIfAbsent(link.code(), link) == null) {
                hitCounts.put(link.code(), new AtomicLong());
                return link;
            }
        }
    }

    /**
     * Returns the original URL for a usable (active, unexpired) code and counts the hit.
     * Inactive and expired codes are treated as if they did not exist.
     */
    public Optional<String> resolve(String code) {
        Optional<String> url = Optional.ofNullable(linksByCode.get(code))
                .filter(link -> link.isUsable(clock.instant()))
                .map(ShortLink::url);
        url.ifPresent(u -> {
            AtomicLong hits = hitCounts.get(code);
            if (hits != null) {
                hits.incrementAndGet();
            }
        });
        return url;
    }

    public LinkMetadata getMetadata(String code) {
        ShortLink link = linksByCode.get(code);
        if (link == null) {
            throw new LinkNotFoundException(code);
        }
        AtomicLong hits = hitCounts.get(code);
        LinkStatus status = link.isUsable(clock.instant()) ? LinkStatus.ACTIVE : LinkStatus.INACTIVE;
        return new LinkMetadata(link.code(), link.url(), link.createdAt(), link.expiresAt(),
                hits == null ? 0 : hits.get(), status);
    }

    public LinkMetadata deactivate(String code) {
        if (linksByCode.computeIfPresent(code, (c, link) -> link.deactivated()) == null) {
            throw new LinkNotFoundException(code);
        }
        return getMetadata(code);
    }

    /**
     * Removes every expired or deactivated link from the map.
     *
     * @return the number of links removed
     */
    public int removeExpiredLinks() {
        Instant now = clock.instant();
        int removed = 0;
        for (Map.Entry<String, ShortLink> entry : linksByCode.entrySet()) {
            if (!entry.getValue().isUsable(now) && linksByCode.remove(entry.getKey(), entry.getValue())) {
                hitCounts.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
