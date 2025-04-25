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

        BrowserContext context;
        try (Browser browser = createBrowser()) {
            // Create browser and context
            context = createBrowserContext(browser);

            // Execute scraping strategy
            places = strategy.execute(browser, context);

            log.info("Completed scraping. Total places scraped: {}", places.size());
        } catch (Exception e) {
            log.error("Error while scraping Visit Seoul website", e);
        }

        return places;
    }
}
