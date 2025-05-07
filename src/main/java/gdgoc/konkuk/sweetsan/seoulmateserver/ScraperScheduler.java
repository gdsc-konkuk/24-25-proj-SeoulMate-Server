package gdgoc.konkuk.sweetsan.seoulmateserver;

import gdgoc.konkuk.sweetsan.seoulmateserver.service.ScraperService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for running the place data collection process periodically. Can be configured to run at specific intervals.
 * Also runs at startup if database is empty.
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
     * Fixed delay scheduled task to run the data collection.
     */
    @Scheduled(cron = "${scraper.scheduler.cron:0 0 0 * * SUN}")
    public void scheduledScraping() {
        if (!schedulerEnabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        log.info("Scheduled place data collection started at {}", now.format(formatter));

        // Get current count before collection
        long beforeCount = scraperService.getPlaceCount();

        // Run the data collection asynchronously
        scraperService.scrapeAndSave()
                .thenAccept(newPlaces -> {
                    // Get updated count
                    long afterCount = scraperService.getPlaceCount();
                    log.info(
                            "Scheduled place data collection completed. Before: {} places, After: {} places, New: {} places",
                            beforeCount, afterCount, newPlaces);
                })
                .exceptionally(e -> {
                    log.error("Error during scheduled place data collection", e);
                    return null;
                });
    }

    /**
     * Initial data collection check that runs on application startup. Only runs collection if no place data exists in
     * the database.
     */
    @PostConstruct
    public void onApplicationEvent() {
        if (!schedulerEnabled || !initialScrapingEnabled) {
            log.info("Initial place data collection is disabled. Skipping initial run.");
            return;
        }

        log.info("Checking if initial place data collection is needed");

        // Only run initial collection if no data exists
        long count = scraperService.getPlaceCount();

        if (count == 0) {
            log.info("Database is empty, starting initial place data collection");
            scraperService.scrapeAndSave()
                    .thenAccept(newCount ->
                            log.info("Initial place data collection completed. Added {} places to database", newCount)
                    );
        } else {
            log.info("Database already contains {} places, skipping initial data collection", count);
        }
    }
}
