package com.example.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.dto.LinkMetadata;
import com.example.exception.AliasUnavailableException;
import com.example.exception.LinkNotFoundException;
import com.example.model.LinkStatus;
import com.example.model.ShortLink;
import com.example.service.UrlShortenerService;

@WebMvcTest(UrlShortenerController.class)
class UrlShortenerControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final String NOT_FOUND_MESSAGE =
            "Short code 'missing' was not found. It may have expired and been removed, or it never existed.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService service;

    @Test
    void shortenReturnsCreatedWithShortUrl() throws Exception {
        when(service.shorten("https://example.com/some/long/path", null, null))
                .thenReturn(new ShortLink("abc1234", "https://example.com/some/long/path", NOW, null));

        mockMvc.perform(post("/api/shortUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/some/long/path"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/abc1234"))
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/some/long/path"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void shortenWithAliasAndTtlReturnsExpiry() throws Exception {
        when(service.shorten("https://example.com", "my-promo", 3600L))
                .thenReturn(new ShortLink("my-promo", "https://example.com", NOW, 3600L));

        mockMvc.perform(post("/api/shortUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "my-promo", "ttl": 3600}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/my-promo"))
                .andExpect(jsonPath("$.code").value("my-promo"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-21T11:00:00Z"));
    }

    @Test
    void shortenRejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/shortUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortenRejectsInvalidAlias() throws Exception {
        mockMvc.perform(post("/api/shortUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "has spaces!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortenRejectsNonPositiveTtl() throws Exception {
        mockMvc.perform(post("/api/shortUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "ttl": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortenReturnsConflictWhenAliasTaken() throws Exception {
        when(service.shorten("https://example.com", "taken", null))
                .thenThrow(new AliasUnavailableException("taken"));

        mockMvc.perform(post("/api/shortUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "taken"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Alias is already in use or reserved: taken"));
    }

    @Test
    void redirectsToOriginalUrl() throws Exception {
        when(service.resolve("my-promo")).thenReturn(Optional.of("https://example.com"));

        mockMvc.perform(get("/my-promo"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownExpiredOrInactiveCodeReturnsNotFoundWithMessage() throws Exception {
        when(service.resolve("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NOT_FOUND_MESSAGE));
    }

    @Test
    void metadataReturnsLinkDetails() throws Exception {
        when(service.getMetadata("my-promo")).thenReturn(new LinkMetadata(
                "my-promo", "https://example.com", NOW, NOW.plusSeconds(3600), 5, LinkStatus.ACTIVE));

        mockMvc.perform(get("/api/shortUrl/my-promo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("my-promo"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-21T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-21T11:00:00Z"))
                .andExpect(jsonPath("$.hitCount").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void metadataForUnknownCodeReturnsNotFound() throws Exception {
        when(service.getMetadata("missing")).thenThrow(new LinkNotFoundException("missing"));

        mockMvc.perform(get("/api/shortUrl/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NOT_FOUND_MESSAGE));
    }

    @Test
    void deactivateReturnsInactiveMetadata() throws Exception {
        when(service.deactivate("my-promo")).thenReturn(new LinkMetadata(
                "my-promo", "https://example.com", NOW, null, 5, LinkStatus.INACTIVE));

        mockMvc.perform(post("/api/shortUrl/my-promo/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("my-promo"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivateUnknownCodeReturnsNotFound() throws Exception {
        when(service.deactivate("missing")).thenThrow(new LinkNotFoundException("missing"));

        mockMvc.perform(post("/api/shortUrl/missing/deactivate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NOT_FOUND_MESSAGE));
    }
}
