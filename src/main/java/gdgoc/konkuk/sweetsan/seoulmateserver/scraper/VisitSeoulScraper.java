package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import com.microsoft.playwright.*;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.base.AbstractScraper;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.strategies.VisitSeoulStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PlaceScraper that scrapes tourist place data from Visit Seoul website. Uses strategy pattern to
 * separate core scraping logic from the execution flow.
 */
@Slf4j
@Component
public class VisitSeoulScraper extends AbstractScraper {

    private final VisitSeoulStrategy strategy;

    @Autowired
    public VisitSeoulScraper(VisitSeoulStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public List<Place> scrape() {
        log.info("Starting Visit Seoul website scraping process");
        List<Place> places = new ArrayList<>();

        // Configure retry mechanism
        final int MAX_RETRIES = 2;
        int attempt = 0;
        boolean success = false;
        
        while (!success && attempt < MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {} for scraping Visit Seoul website", attempt);
                    // Wait before retrying
                    Thread.sleep(5000 * attempt);
                }
                
                attempt++;
                Browser browser = null;
                
                try {
                    // Create browser
                    browser = createBrowser();
                    
                    // Create browser context
                    BrowserContext context = createBrowserContext(browser);
                    
                    // Execute scraping strategy with timeout
                    log.info("Executing scraping strategy (attempt {}/{})", attempt, MAX_RETRIES);
                    places = strategy.execute(browser, context);
                    
                    // Check if we got at least some data
                    if (places != null && !places.isEmpty()) {
                        log.info("Completed scraping. Total places scraped: {}", places.size());
                        success = true;
                    } else {
                        log.warn("Scraping returned empty result (attempt {}/{})", attempt, MAX_RETRIES);
                    }
                } finally {
                    // Ensure browser is closed properly
                    if (browser != null) {
                        try {
                            browser.close();
                            log.info("Browser closed successfully");
                        } catch (Exception e) {
                            log.warn("Error closing browser", e);
                        }
                    }
                }
            } catch (PlaywrightException e) {
                log.error("Playwright error while scraping (attempt {}/{}): {}", 
                        attempt, MAX_RETRIES, e.getMessage());
                if (e.getMessage().contains("timeout") && attempt < MAX_RETRIES) {
                    log.info("Timeout detected, will retry with increased timeouts");
                } else if (attempt >= MAX_RETRIES) {
                    log.error("Maximum retry attempts reached. Giving up.");
                }
            } catch (Exception e) {
                log.error("Error while scraping Visit Seoul website (attempt {}/{})", 
                        attempt, MAX_RETRIES, e);
                if (attempt >= MAX_RETRIES) {
                    log.error("Maximum retry attempts reached. Giving up.");
                }
            }
        }
        
        // If we ended up with no places after all retries, return empty list
        if (places == null) {
            places = new ArrayList<>();
        }

        return places;
    }
}
