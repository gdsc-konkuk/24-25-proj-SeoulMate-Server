package gdgoc.konkuk.sweetsan.seoulmateserver.scraper.base;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for different website scraping approaches.
 * Allows for implementation of different scraping techniques for different websites.
 */
public interface ScraperStrategy {
    
    /**
     * Get the base URL for the website being scraped.
     * 
     * @return Base URL string
     */
    String getBaseUrl();
    
    /**
     * Get the categories to scrape from the website.
     * 
     * @return Map of category names to their URL paths
     */
    Map<String, String> getCategories();
    
    /**
     * Execute the scraping process using the provided Playwright objects.
     * 
     * @param browser The browser instance
     * @param context The browser context
     * @return List of scraped Place objects
     */
    List<Place> execute(Browser browser, BrowserContext context);
    
    /**
     * Process a specific category page.
     * 
     * @param context The browser context
     * @param categoryName Name of the category
     * @param categoryUrl URL of the category
     * @return List of places found in this category
     */
    List<Place> processCategory(BrowserContext context, String categoryName, String categoryUrl);
    
    /**
     * Process a specific place listing page.
     * 
     * @param page The current page
     * @param pageNum The current page number
     * @param totalPages Total number of pages in this category
     * @return List of places found on this page
     */
    List<Place> processListingPage(Page page, int pageNum, int totalPages);
    
    /**
     * Process a place detail page to extract detailed information.
     * 
     * @param detailPage The detail page
     * @param placeId Unique ID for the place
     * @param name Name of the place
     * @param shortDescription Short description of the place
     * @return A fully populated Place object
     */
    Place processDetailPage(Page detailPage, String placeId, String name, String shortDescription);
}
