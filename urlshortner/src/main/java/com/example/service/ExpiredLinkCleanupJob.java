package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredLinkCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredLinkCleanupJob.class);

    private final UrlShortenerService urlShortenerService;

    public ExpiredLinkCleanupJob(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    //@Scheduled(cron = "${urlshortener.cleanup.cron:0 0 * * * *}")
    @Scheduled(cron = "0 * * * * *")
    public void removeExpiredLinks() {
        int removed = urlShortenerService.removeExpiredLinks();
        log.info("Cleanup removed {} expired or deactivated link(s)", removed);
    }
}
