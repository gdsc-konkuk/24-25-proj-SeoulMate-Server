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
 * Scheduler for running the web scraper on a regular basis.
 * Manages both initial and scheduled scraping tasks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperScheduler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ScraperService scraperService;

    @Value("${scraper.scheduler.enabled:true}")
    private boolean schedulerEnabled;
    
    @Value("${scraper.initial.enabled:true}")
    private boolean initialScrapingEnabled;

    /**
     * Scheduled task to run the scraper weekly.
     * The cron expression "0 0 0 * * SUN" means it runs at midnight (00:00:00) every Sunday.
     * This can be configured in application.yml with property: scraper.scheduler.cron
     */
    @Scheduled(cron = "${scraper.scheduler.cron:0 0 0 * * SUN}")
    public void scheduledScraping() {
        if (!schedulerEnabled) {
            log.info("Scraper scheduler is disabled. Skipping scheduled run.");
            return;
        }

        String startTime = LocalDateTime.now().format(DATE_FORMATTER);
        log.info("Starting scheduled scraping task at {}", startTime);
        
        try {
            int placesCount = scraperService.scrapeAndSave();
            
            String endTime = LocalDateTime.now().format(DATE_FORMATTER);
            log.info("Scheduled scraping completed at {}. Total places saved: {}", endTime, placesCount);
        } catch (Exception e) {
            log.error("Error during scheduled scraping", e);
        }
    }

    /**
     * Initial data collection check that runs on application startup.
     * Only runs scraping if no place data exists in the database.
     */
    @PostConstruct
    public void initialScraping() {
        if (!schedulerEnabled || !initialScrapingEnabled) {
            log.info("Initial scraping is disabled. Skipping initial run.");
            return;
        }

        log.info("Checking if initial scraping is needed");
        
        try {
            // Only run initial scraping if no data exists
            long placeCount = scraperService.getPlaceCount();
            
            if (placeCount == 0) {
                log.info("No place data found. Starting initial scraping...");
                
                // Run async to avoid blocking application startup
                scraperService.scrapeAndSaveAsync()
                        .thenAccept(count -> log.info("Initial scraping completed. Added {} places.", count))
                        .exceptionally(ex -> {
                            log.error("Error during initial scraping", ex);
                            return null;
                        });
            } else {
                log.info("Found {} existing places. Skipping initial scraping.", placeCount);
            }
        } catch (Exception e) {
            log.error("Error checking initial scraping need", e);
        }
    }
    
    /**
     * Force a manual scrape operation.
     * This method can be called from a controller endpoint to trigger a scrape on demand.
     * 
     * @param async Whether to run the scrape asynchronously
     * @return Message indicating scrape started
     */
    public String manualScrape(boolean async) {
        log.info("Received request for manual scraping (async: {})", async);
        
        try {
            if (async) {
                scraperService.scrapeAndSaveAsync()
                    .thenAccept(count -> log.info("Manual async scraping completed. Added {} places.", count))
                    .exceptionally(ex -> {
                        log.error("Error during manual async scraping", ex);
                        return null;
                    });
                    
                return "Manual scraping started asynchronously";
            } else {
                int count = scraperService.scrapeAndSave();
                return String.format("Manual scraping completed. Added %d places.", count);
            }
        } catch (Exception e) {
            log.error("Error during manual scraping", e);
            return "Error during manual scraping: " + e.getMessage();
        }
    }
}
