package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceSourceData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * This repository is responsible for scraping and extracting place information from the Visit Seoul website.
 */
@Slf4j
@Repository
public class VisitSeoulSourceRepository implements PlaceSourceDataRepository {

    // Constants
    private static final String BASE_URL = "https://english.visitseoul.net";
    private static final String SOURCE_NAME = "visitseoul";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36";

    // CSS Selectors - Updated to match actual DOM structure
    private static final String PLACE_LIST_SELECTOR = "ul.article-list";
    private static final String PLACE_ITEM_SELECTOR = "li";

    // Selectors for content based on actual DOM structure
    private static final String NAME_SELECTOR = ".title";
    private static final String DESCRIPTION_SELECTOR = ".small-text";

    private static final String PAGINATION_LAST_PAGE_SELECTOR = "a[href*='curPage']:last-of-type";
    private static final String COOKIE_ACCEPT_SELECTOR = "text=Accept All";

    // Patterns
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("curPage=(\\d+)");
    // Define all actual categories from the website
    private static final List<CategoryInfo> CATEGORIES = Arrays.asList(
            new CategoryInfo(BASE_URL + "/attractions", "Attractions", "a[href*='/attractions/']"),
            new CategoryInfo(BASE_URL + "/nature", "Nature", "a[href*='/nature/']"),
            new CategoryInfo(BASE_URL + "/entertainment", "Entertainment", "a[href*='/entertainment/']"),
            new CategoryInfo(BASE_URL + "/shopping", "Shopping", "a[href*='/shopping/']"),
            new CategoryInfo(BASE_URL + "/restaurants", "Restaurants", "a[href*='/restaurants/']"),
            new CategoryInfo(BASE_URL + "/area", "Area", "a[href*='/area/']")
    );

    /**
     * Asynchronously retrieves tourist place source data from Visit Seoul website.
     */
    @Override
    public CompletableFuture<List<PlaceSourceData>> findAllAsync() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting data retrieval from Visit Seoul English website for all categories");
            List<PlaceSourceData> allSourceData = new ArrayList<>();

            try (Playwright playwright = Playwright.create();
                 Browser browser = launchBrowser(playwright)) {

                BrowserContext context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setUserAgent(USER_AGENT)
                                .setViewportSize(1920, 1080));

                try (Page page = context.newPage()) {
                    page.setDefaultTimeout(30000);

                    // Process each category
                    for (CategoryInfo category : CATEGORIES) {
                        log.info("Processing category: {}", category.name);
                        List<PlaceSourceData> categoryData = processCategoryPages(page, category);
                        allSourceData.addAll(categoryData);
                        log.info("Completed category: {}. Places found: {}", category.name, categoryData.size());
                    }
                }
            } catch (Exception e) {
                log.error("Error during Visit Seoul data retrieval: {}", e.getMessage());
            }

            log.info("Completed data retrieval for all categories. Total places found: {}", allSourceData.size());
            return allSourceData;
        });
    }

    /**
     * Processes all pages for a specific category.
     */
    private List<PlaceSourceData> processCategoryPages(Page page, CategoryInfo category) {
        List<PlaceSourceData> categoryData = new ArrayList<>();

        try {
            // Start with the first page
            categoryData.addAll(scrapePage(page, category.url, 1, category));

            // Process remaining pages
            int totalPages = determineTotalPages(page);
            processRemainingPages(page, categoryData, totalPages, category);
        } catch (Exception e) {
            log.error("Error processing category {}: {}", category.name, e.getMessage());
        }

        return categoryData;
    }

    /**
     * Launches a headless browser with required settings.
     */
    private Browser launchBrowser(Playwright playwright) {
        return playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setTimeout(60000)
                .setArgs(Arrays.asList(
                        "--disable-extensions",
                        "--disable-dev-shm-usage",
                        "--no-sandbox",
                        "--disable-features=IsolateOrigins,site-per-process",
                        "--disable-web-security")));
    }

    /**
     * Processes the remaining pages after the first one.
     */
    private void processRemainingPages(Page page, List<PlaceSourceData> sourceDataList,
                                       int totalPages, CategoryInfo category) {
        log.info("Found {} total pages to process for category {}", totalPages, category.name);

        for (int pageNum = 2; pageNum <= totalPages; pageNum++) {
            try {
                String pageUrl = category.url + "?curPage=" + pageNum;
                List<PlaceSourceData> pageData = scrapePage(page, pageUrl, pageNum, category);
                sourceDataList.addAll(pageData);
                log.info("Processed page {}/{} for category {}: extracted {} places",
                        pageNum, totalPages, category.name, pageData.size());
            } catch (Exception e) {
                log.error("Error processing page {} for category {}: {}",
                        pageNum, category.name, e.getMessage());
            }
        }
    }

    /**
     * Scrapes a single page of the Visit Seoul website.
     */
    private List<PlaceSourceData> scrapePage(Page page, String url, int pageNum, CategoryInfo category) {
        List<PlaceSourceData> results = new ArrayList<>();

        try {
            // Navigate to the page and wait for it to load
            log.info("Navigating to page {} for category {}: {}", pageNum, category.name, url);
            page.navigate(url);
            handleCookieConsent(page);

            // Wait for content to be fully loaded
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(30000));

            // Wait for the place list to be available
            page.waitForSelector(PLACE_LIST_SELECTOR,
                    new Page.WaitForSelectorOptions().setTimeout(10000));

            // Get all list items with places
            Locator placesList = page.locator(PLACE_LIST_SELECTOR);
            Locator placeItems = placesList.locator(PLACE_ITEM_SELECTOR);
            int count = placeItems.count();
            log.info("Found {} place items on page {} for category {}", count, pageNum, category.name);

            // Process each place item
            for (int i = 0; i < count; i++) {
                PlaceSourceData place = extractPlaceDataFromItem(placeItems.nth(i), category);
                if (place != null) {
                    results.add(place);
                }
            }
        } catch (Exception e) {
            log.error("Error scraping page {} for category {}: {}", pageNum, category.name, e.getMessage());
        }

        return results;
    }

    /**
     * Handles cookie consent popup if it appears.
     */
    private void handleCookieConsent(Page page) {
        try {
            Locator cookieAcceptButton = page.locator(COOKIE_ACCEPT_SELECTOR);
            if (cookieAcceptButton.isVisible()) {
                cookieAcceptButton.click();
                log.debug("Accepted cookies");
                page.waitForTimeout(1000);
            }
        } catch (Exception ignored) {
            // Cookie consent may not appear, so ignore any errors
        }
    }

    /**
     * Extracts place data from a list item containing place information.
     */
    private PlaceSourceData extractPlaceDataFromItem(Locator placeItem, CategoryInfo category) {
        try {
            // Get the main link in each item
            Locator placeLink = placeItem.locator("a").first();

            // Extract URL and check for ENP ID pattern
            String href = placeLink.getAttribute("href");
            if (href == null || !href.contains("ENP")) {
                return null;
            }

            String visitSeoulId = extractVisitSeoulId(href);
            if (visitSeoulId == null) {
                return null;
            }

            // Extract name and description
            String name = "";
            String description = "";

            // Extract title
            Locator nameElement = placeItem.locator(NAME_SELECTOR);
            if (nameElement.count() > 0) {
                name = nameElement.textContent().trim();
            }

            // Extract description
            Locator descElement = placeItem.locator(DESCRIPTION_SELECTOR);
            if (descElement.count() > 0) {
                description = descElement.textContent().trim();
            }

            // Skip if name couldn't be extracted
            if (name.isEmpty()) {
                log.warn("Could not extract place name from item in category {}: {}",
                        category.name, href);
                return null;
            }

            // Create place object
            PlaceSourceData place = PlaceSourceData.builder()
                    .name(name)
                    .description(description)
                    .sourceId(visitSeoulId)
                    .sourceName(SOURCE_NAME)
                    .build();

            log.info("Extracted place from category {}: name='{}', desc='{}', id='{}'",
                    category.name, place.getName(), place.getDescription(), place.getSourceId());
            return place;
        } catch (Exception e) {
            log.warn("Error extracting place data from item in category {}: {}",
                    category.name, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the Visit Seoul ID from a URL.
     */
    private String extractVisitSeoulId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // URL format: /category/placeName/ENPxxxxxx
        String[] parts = url.split("/");
        if (parts.length >= 2) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.startsWith("ENP")) {
                return lastPart;
            }
        }

        return null;
    }

    /**
     * Determines the total number of pages available for scraping.
     */
    private int determineTotalPages(Page page) {
        try {
            // Find the last page link using Locator API
            Locator lastPageLink = page.locator(PAGINATION_LAST_PAGE_SELECTOR);
            if (lastPageLink.count() > 0) {
                String href = lastPageLink.getAttribute("href");
                if (href != null && href.contains("curPage=")) {
                    Matcher matcher = PAGE_NUMBER_PATTERN.matcher(href);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error determining total pages: {}", e.getMessage());
        }

        return 5; // Default value
    }

    // Category Information
    private static class CategoryInfo {
        String url;
        String name;
        String linkSelector;

        CategoryInfo(String url, String name, String linkSelector) {
            this.url = url;
            this.name = name;
            this.linkSelector = linkSelector;
        }
    }
}
