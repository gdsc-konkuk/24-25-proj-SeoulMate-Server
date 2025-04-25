package gdgoc.konkuk.sweetsan.seoulmateserver.scraper.base;

import com.microsoft.playwright.*;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for web scrapers. Provides common functionality for web scraping operations.
 */
@Slf4j
public abstract class AbstractScraper implements PlaceScraper {
    
    /**
     * Creates a new browser instance with standard configuration.
     * 
     * @return Configured Browser instance
     */
    protected Browser createBrowser() {
        try {
            Playwright playwright = Playwright.create();
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(180000));
                    
            log.info("Browser created successfully");
            return browser;
        } catch (Exception e) {
            log.error("Failed to create browser", e);
            throw new RuntimeException("Failed to create browser", e);
        }
    }
    
    /**
     * Creates a new browser context with standard configuration.
     * 
     * @param browser Browser instance
     * @return Configured BrowserContext
     */
    protected BrowserContext createBrowserContext(Browser browser) {
        try {
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36")
                    .setViewportSize(1920, 1080));
                    
            log.info("Browser context created successfully");
            return context;
        } catch (Exception e) {
            log.error("Failed to create browser context", e);
            throw new RuntimeException("Failed to create browser context", e);
        }
    }
    
    /**
     * Handles cookie consent popups that may appear on websites.
     * 
     * @param page Playwright Page instance
     */
    protected void handleCookieConsent(Page page) {
        try {
            // Common cookie consent button selectors
            String[] consentSelectors = {
                "text=모두 허용", 
                "text=Accept All", 
                "text=Accept Cookies",
                "button:has-text('Accept')",
                "[id*='cookie'] button",
                "[class*='cookie'] button"
            };
            
            for (String selector : consentSelectors) {
                if (page.isVisible(selector)) {
                    page.click(selector);
                    log.info("Clicked cookie consent button: {}", selector);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Could not handle cookie consent, continuing anyway", e);
        }
    }
    
    /**
     * Base implementation for async scraping.
     * 
     * @return CompletableFuture with list of Place objects
     */
    @Override
    public CompletableFuture<List<Place>> scrapeAsync() {
        return CompletableFuture.supplyAsync(this::scrape);
    }
    
    /**
     * Safely close a browser instance.
     * 
     * @param browser Browser instance to close
     */
    protected void closeBrowser(Browser browser) {
        if (browser != null) {
            try {
                browser.close();
                log.info("Browser closed successfully");
            } catch (Exception e) {
                log.warn("Error closing browser", e);
            }
        }
    }
}
