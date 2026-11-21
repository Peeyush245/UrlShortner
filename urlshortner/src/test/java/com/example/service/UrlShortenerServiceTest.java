package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.dto.LinkMetadata;
import com.example.exception.AliasUnavailableException;
import com.example.exception.LinkNotFoundException;
import com.example.model.LinkStatus;
import com.example.model.ShortLink;

class UrlShortenerServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
    private final UrlShortenerService service = new UrlShortenerService(clock);

    @Test
    void shortenedCodeResolvesToOriginalUrl() {
        ShortLink link = service.shorten("https://example.com", null, null);

        assertThat(link.code()).hasSize(7).matches("[a-zA-Z0-9]+");
        assertThat(link.expiresAt()).isNull();
        assertThat(service.resolve(link.code())).contains("https://example.com");
    }

    @Test
    void sameUrlGetsDistinctCodes() {
        assertThat(service.shorten("https://example.com", null, null).code())
                .isNotEqualTo(service.shorten("https://example.com", null, null).code());
    }

    @Test
    void unknownCodeResolvesToEmpty() {
        assertThat(service.resolve("missing")).isEmpty();
    }

    @Test
    void customAliasIsUsedAsCode() {
        ShortLink link = service.shorten("https://example.com", "my-promo", null);

        assertThat(link.code()).isEqualTo("my-promo");
        assertThat(service.resolve("my-promo")).contains("https://example.com");
    }

    @Test
    void duplicateAliasIsRejected() {
        service.shorten("https://example.com/a", "taken", null);

        assertThatThrownBy(() -> service.shorten("https://example.com/b", "taken", null))
                .isInstanceOf(AliasUnavailableException.class);
        assertThat(service.resolve("taken")).contains("https://example.com/a");
    }

    @Test
    void expiredAliasIsReusableOnlyAfterCleanup() {
        service.shorten("https://example.com/old", "promo", 60L);
        clock.advance(Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.shorten("https://example.com/new", "promo", null))
                .isInstanceOf(AliasUnavailableException.class);

        service.removeExpiredLinks();
        service.shorten("https://example.com/new", "promo", null);

        assertThat(service.resolve("promo")).contains("https://example.com/new");
    }

    @Test
    void metadataReportsLinkDetailsAndCountsOnlySuccessfulHits() {
        service.shorten("https://example.com", "meta", 3600L);
        service.resolve("meta");
        service.resolve("meta");

        LinkMetadata metadata = service.getMetadata("meta");

        assertThat(metadata.code()).isEqualTo("meta");
        assertThat(metadata.originalUrl()).isEqualTo("https://example.com");
        assertThat(metadata.createdAt()).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
        assertThat(metadata.expiresAt()).isEqualTo(Instant.parse("2026-11-21T11:00:00Z"));
        assertThat(metadata.hitCount()).isEqualTo(2);
        assertThat(metadata.status()).isEqualTo(LinkStatus.ACTIVE);

        service.deactivate("meta");
        service.resolve("meta");
        assertThat(service.getMetadata("meta").hitCount()).isEqualTo(2);
    }

    @Test
    void metadataForUnknownCodeThrowsNotFound() {
        assertThatThrownBy(() -> service.getMetadata("missing"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void metadataShowsExpiredLinkAsInactive() {
        service.shorten("https://example.com", "short", 60L);
        clock.advance(Duration.ofSeconds(60));

        assertThat(service.getMetadata("short").status()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    void deactivatedCodeNoLongerResolves() {
        service.shorten("https://example.com", "promo", null);

        LinkMetadata metadata = service.deactivate("promo");

        assertThat(metadata.status()).isEqualTo(LinkStatus.INACTIVE);
        assertThat(service.resolve("promo")).isEmpty();
    }

    @Test
    void deactivatingTwiceIsHarmless() {
        service.shorten("https://example.com", "promo", null);
        service.deactivate("promo");

        assertThat(service.deactivate("promo").status()).isEqualTo(LinkStatus.INACTIVE);
    }

    @Test
    void deactivatingUnknownCodeThrowsNotFound() {
        assertThatThrownBy(() -> service.deactivate("missing"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void cleanupRemovesDeactivatedLinks() {
        service.shorten("https://example.com", "promo", null);
        service.shorten("https://example.com/keep", "keep", null);
        service.deactivate("promo");

        assertThat(service.removeExpiredLinks()).isEqualTo(1);
        assertThatThrownBy(() -> service.getMetadata("promo"))
                .isInstanceOf(LinkNotFoundException.class);
        assertThat(service.resolve("keep")).contains("https://example.com/keep");
    }

    @Test
    void linkStopsResolvingOnceTtlPasses() {
        ShortLink link = service.shorten("https://example.com", null, 60L);
        assertThat(link.expiresAt()).isEqualTo(Instant.parse("2026-11-21T10:01:00Z"));

        clock.advance(Duration.ofSeconds(59));
        assertThat(service.resolve(link.code())).contains("https://example.com");

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.resolve(link.code())).isEmpty();
    }

    @Test
    void cleanupRemovesOnlyExpiredLinks() {
        service.shorten("https://example.com/short", "short-lived", 60L);
        service.shorten("https://example.com/long", "long-lived", 7200L);
        service.shorten("https://example.com/forever", "forever", null);

        clock.advance(Duration.ofHours(1));

        assertThat(service.removeExpiredLinks()).isEqualTo(1);
        assertThat(service.resolve("long-lived")).contains("https://example.com/long");
        assertThat(service.resolve("forever")).contains("https://example.com/forever");
        assertThat(service.removeExpiredLinks()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
