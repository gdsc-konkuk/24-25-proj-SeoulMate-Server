package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import gdgoc.konkuk.sweetsan.seoulmateserver.service.ScraperService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduler for running the scraper periodically. Can be configured to run at specific intervals. Also runs at startup
 * if database is empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperScheduler {

    private final ScraperService scraperService;

    @Value("${scraper.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${scraper.initial.enabled:true}")
    private boolean initialScrapingEnabled;

    /**
     * Fixed delay scheduled task to run the scraper.
     */
    @Scheduled(cron = "${scraper.scheduler.cron:0 0 0 * * SUN}")
    public void scheduledScraping() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        log.info("Scheduled scraping started at {}", now.format(formatter));

        try {
            // Get current count before scraping
            long beforeCount = scraperService.getPlaceCount();

            // Run the scraper
            int newPlaces = scraperService.scrapeAndSave();

            // Get updated count
            long afterCount = scraperService.getPlaceCount();

            log.info("Scheduled scraping completed. Before: {} places, After: {} places, New: {} places",
                    beforeCount, afterCount, newPlaces);
        } catch (Exception e) {
            log.error("Error during scheduled scraping", e);
        }
    }

    /**
     * Initial data collection check that runs on application startup. Only runs scraping if no place data exists in the
     * database.
     */
    @PostConstruct
    public void onApplicationEvent() {
        if (!schedulerEnabled || !initialScrapingEnabled) {
            log.info("Initial scraping is disabled. Skipping initial run.");
            return;
        }

        log.info("Checking if initial scraping is needed");

        try {
            // Only run initial scraping if no data exists
            long count = scraperService.getPlaceCount();

            if (count == 0) {
                log.info("Database is empty, starting initial scraping");
                scraperService.scrapeAndSaveAsync().thenAccept(newCount ->
                        log.info("Initial scraping completed. Added {} places to database", newCount)
                );
            } else {
                log.info("Database already contains {} places, skipping initial scraping", count);
            }
        } catch (Exception e) {
            log.error("Error during startup scraping check", e);
        }
    }
}
