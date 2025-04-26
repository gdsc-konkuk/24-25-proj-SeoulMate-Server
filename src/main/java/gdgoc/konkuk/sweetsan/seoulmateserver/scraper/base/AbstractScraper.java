package gdgoc.konkuk.sweetsan.seoulmateserver.scraper.base;

import com.microsoft.playwright.*;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
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
                    .setTimeout(300000) // 5 minutes timeout
                    .setSlowMo(50) // Slows down Playwright operations by 50ms for stability
                    .setArgs(Arrays.asList(
                            "--disable-extensions",
                            "--disable-dev-shm-usage", // Overcome limited resource problems
                            "--no-sandbox", // Required for stability in some environments
                            "--disable-setuid-sandbox",
                            "--disable-features=IsolateOrigins,site-per-process", // Helps with frame handling
                            "--disable-web-security", // Helps with cross-origin issues
                            "--disable-gpu" // Better performance in headless
                    )));

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
     * Base implementation for async scraping.
     *
     * @return CompletableFuture with list of Place objects
     */
    @Override
    public CompletableFuture<List<Place>> scrapeAsync() {
        return CompletableFuture.supplyAsync(this::scrape);
    }
}
