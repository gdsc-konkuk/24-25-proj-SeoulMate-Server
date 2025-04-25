package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for scraping tourist place information from various sources.
 */
public interface PlaceScraper {
    
    /**
     * Scrapes tourist place data asynchronously.
     * 
     * @return A CompletableFuture that resolves to a list of Place objects.
     */
    CompletableFuture<List<Place>> scrapeAsync();
    
    /**
     * Scrapes tourist place data synchronously.
     * 
     * @return A list of Place objects.
     */
    List<Place> scrape();
}
