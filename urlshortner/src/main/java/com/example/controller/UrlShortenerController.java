package com.example.controller;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.dto.LinkMetadata;
import com.example.dto.ShortenRequest;
import com.example.dto.ShortenResponse;
import com.example.model.ShortLink;
import com.example.service.UrlShortenerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.example.exception.LinkNotFoundException;

@RestController
@Tag(name = "URL Shortener")
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    public UrlShortenerController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @Operation(summary = "Shorten a URL, optionally with a custom alias and a TTL")
    @ApiResponse(responseCode = "200", description = "Short URL created")
    @ApiResponse(responseCode = "400", description = "Invalid URL, alias or TTL")
    @ApiResponse(responseCode = "409", description = "Alias already in use or reserved")
    @PostMapping("/api/shortUrl")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortLink link = urlShortenerService.shorten(request.url(), request.alias(), request.ttl());
        URI shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(link.code())
                .toUri();
        return ResponseEntity.created(shortUrl)
                .body(new ShortenResponse(link.code(), shortUrl.toString(), link.url(), link.expiresAt()));
    }

    @Operation(summary = "Get metadata for a short code: original URL, creation time, hit count and status")
    @ApiResponse(responseCode = "200", description = "Short code metadata")
    @ApiResponse(responseCode = "404", description = "Unknown short code, or already removed by cleanup")
    @GetMapping("/api/shortUrl/{code}")
    public LinkMetadata metadata(@PathVariable String code) {
        return urlShortenerService.getMetadata(code);
    }

    @Operation(summary = "Deactivate a short code so it no longer redirects")
    @ApiResponse(responseCode = "200", description = "Short code deactivated; returns its metadata")
    @ApiResponse(responseCode = "404", description = "Unknown short code, or already removed by cleanup")
    @PostMapping("/api/shortUrl/{code}/deactivate")
    public LinkMetadata deactivate(@PathVariable String code) {
        return urlShortenerService.deactivate(code);
    }

    @Operation(summary = "Redirect a short code to its original URL")
    @ApiResponse(responseCode = "302", description = "Redirect to the original URL (see Location header)")
    @ApiResponse(responseCode = "404", description = "Unknown, expired or deactivated short code")
    @GetMapping("/{code:[a-zA-Z0-9_-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        // no-store stops browsers caching the redirect past the link's TTL.
        return urlShortenerService.resolve(code)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(url))
                        .cacheControl(CacheControl.noStore())
                        .<Void>build())
                .orElseThrow(() -> new LinkNotFoundException(code));
    }
}
