package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import gdgoc.konkuk.sweetsan.seoulmateserver.service.ScraperService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for running the web scraper on a regular basis. By default, it runs once a week to update the tourist place
 * data.
 */
@Component
public class ScraperScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ScraperScheduler.class);

    private final ScraperService scraperService;

    @Value("${scraper.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Autowired
    public ScraperScheduler(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    /**
     * Scheduled task to run the scraper weekly. The cron expression "0 0 0 * * SUN" means it runs at midnight
     * (00:00:00) every Sunday. This can be configured in application.yml with property: scraper.scheduler.cron
     */
    @Scheduled(cron = "${scraper.scheduler.cron:0 0 0 * * SUN}")
    public void scheduledScraping() {
        if (!schedulerEnabled) {
            logger.info("Scraper scheduler is disabled. Skipping scheduled run.");
            return;
        }

        logger.info("Starting scheduled scraping task");
        try {
            int placesCount = scraperService.scrapeAndSave();
            logger.info("Scheduled scraping completed. Total places saved: {}", placesCount);
        } catch (Exception e) {
            logger.error("Error during scheduled scraping", e);
        }
    }

    /**
     * 애플리케이션 시작 시 초기 데이터 수집 여부를 확인하고 필요 시 실행 주석 해제하여 활성화할 수 있습니다.
     */
    @PostConstruct
    public void initialScraping() {
        if (!schedulerEnabled) {
            logger.info("Scraper scheduler is disabled. Skipping initial run.");
            return;
        }

        logger.info("Checking if initial scraping is needed");
        try {
            // 처음 시작할 때 데이터가 없는 경우에만 스크래핑 실행
            long placeCount = scraperService.getPlaceCount();
            if (placeCount == 0) {
                logger.info("No place data found. Starting initial scraping...");
                scraperService.scrapeAndSaveAsync()
                        .thenAccept(count -> logger.info("Initial scraping completed. Added {} places.", count))
                        .exceptionally(ex -> {
                            logger.error("Error during initial scraping", ex);
                            return null;
                        });
            } else {
                logger.info("Found {} existing places. Skipping initial scraping.", placeCount);
            }
        } catch (Exception e) {
            logger.error("Error checking initial scraping need", e);
        }
    }
}
