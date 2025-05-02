package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceSourceData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This repository is responsible for scraping and extracting place information from the Visit Seoul website.
 */
@Slf4j
@Repository
public class VisitSeoulSourceRepository implements PlaceSourceDataRepository {

    // Constants
    private static final String BASE_URL = "https://korean.visitseoul.net";
    private static final String ATTRACTIONS_URL = BASE_URL + "/attractions";
    private static final String SOURCE_NAME = "visitseoul";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36";

    // CSS Selectors - Updated to match actual DOM structure
    private static final String PLACE_LIST_SELECTOR = "ul.article-list";
    private static final String PLACE_ITEM_SELECTOR = "li.item";
    private static final String PLACE_LINK_SELECTOR = "a[href*='/attractions/'][href*='KOP']";

    // Selectors for content based on provided example
    private static final String NAME_SELECTOR = ".infor-element .title";
    private static final String DESCRIPTION_SELECTOR = ".infor-element .small-text.text-dot-d";

    private static final String PAGINATION_LAST_PAGE_SELECTOR = "a[href*='curPage']:last-of-type";
    private static final String PAGINATION_LINK_SELECTOR = "a[href*='curPage']";
    private static final String COOKIE_ACCEPT_SELECTOR = "text=모두 허용";

    // Patterns
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("curPage=(\\d+)");

    /**
     * Asynchronously retrieves tourist place source data from Visit Seoul website.
     */
    @Override
    public CompletableFuture<List<PlaceSourceData>> findAllAsync() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting data retrieval from Visit Seoul website");
            List<PlaceSourceData> sourceDataList = new ArrayList<>();

            try (Playwright playwright = Playwright.create();
                 Browser browser = launchBrowser(playwright)) {

                BrowserContext context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setUserAgent(USER_AGENT)
                                .setViewportSize(1920, 1080));

                try (Page page = context.newPage()) {
                    page.setDefaultTimeout(30000);

                    // Start with the first page
                    sourceDataList.addAll(scrapePage(page, ATTRACTIONS_URL, 1));

                    // Process remaining pages
                    int totalPages = determineTotalPages(page);
                    processRemainingPages(page, sourceDataList, totalPages);
                }
            } catch (Exception e) {
                log.error("Error during Visit Seoul data retrieval: {}", e.getMessage());
            }

            log.info("Completed data retrieval. Total places found: {}", sourceDataList.size());
            return sourceDataList;
        });
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
    private void processRemainingPages(Page page, List<PlaceSourceData> sourceDataList, int totalPages) {
        log.info("Found {} total pages to process", totalPages);

        for (int pageNum = 2; pageNum <= totalPages; pageNum++) {
            try {
                String pageUrl = ATTRACTIONS_URL + "?curPage=" + pageNum;
                List<PlaceSourceData> pageData = scrapePage(page, pageUrl, pageNum);
                sourceDataList.addAll(pageData);
                log.info("Processed page {}/{}: extracted {} places", pageNum, totalPages, pageData.size());
            } catch (Exception e) {
                log.error("Error processing page {}: {}", pageNum, e.getMessage());
            }
        }
    }

    /**
     * Scrapes a single page of the Visit Seoul website.
     */
    private List<PlaceSourceData> scrapePage(Page page, String url, int pageNum) {
        List<PlaceSourceData> results = new ArrayList<>();

        try {
            // Navigate to the page and wait for it to load
            log.info("Navigating to page {}: {}", pageNum, url);
            page.navigate(url);
            handleCookieConsent(page);
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(30000));

            // Wait for the place list to be available
            page.waitForSelector(PLACE_LIST_SELECTOR,
                    new Page.WaitForSelectorOptions().setTimeout(10000));

            // Get all list items with places
            Locator placesList = page.locator(PLACE_LIST_SELECTOR);
            Locator placeItems = placesList.locator(PLACE_ITEM_SELECTOR);
            int count = placeItems.count();
            log.info("Found {} place items on page {}", count, pageNum);

            // Process each place item
            for (int i = 0; i < count; i++) {
                PlaceSourceData place = extractPlaceDataFromItem(placeItems.nth(i));
                if (place != null) {
                    results.add(place);
                }
            }
        } catch (Exception e) {
            log.error("Error scraping page {}: {}", pageNum, e.getMessage());
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
    private PlaceSourceData extractPlaceDataFromItem(Locator placeItem) {
        try {
            // Get the link element inside the list item
            Locator placeLink = placeItem.locator(PLACE_LINK_SELECTOR).first();

            // Extract URL and ID
            String href = placeLink.getAttribute("href");
            if (href == null || !href.contains("KOP")) {
                return null;
            }

            String visitSeoulId = extractVisitSeoulId(href);
            if (visitSeoulId == null) {
                return null;
            }

            String name = "";
            String description = "";

            try {
                // Extract name using title class selector
                Locator nameElement = placeLink.locator(NAME_SELECTOR);
                if (nameElement.count() > 0) {
                    name = nameElement.textContent().trim();
                }

                // Extract description using small-text class selector
                Locator descElement = placeLink.locator(DESCRIPTION_SELECTOR);
                if (descElement.count() > 0) {
                    description = descElement.textContent().trim();
                }
            } catch (Exception e) {
                log.warn("Error extracting place data from DOM: {}", e.getMessage());
            }

            // Skip if we couldn't extract a name
            if (name.isEmpty()) {
                log.warn("Could not extract place name from item: {}", href);
                return null;
            }

            // Create place object
            PlaceSourceData place = PlaceSourceData.builder()
                    .name(name)
                    .description(description)
                    .sourceId(visitSeoulId)
                    .sourceName(SOURCE_NAME)
                    .build();

            log.info("Extracted place: name='{}', desc='{}', id='{}'",
                    place.getName(), place.getDescription(), place.getSourceId());
            return place;
        } catch (Exception e) {
            log.warn("Error extracting place data from item: {}", e.getMessage());
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

        // URL format: /attractions/placeName/KOPxxxxxx
        String[] parts = url.split("/");
        if (parts.length >= 3) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.startsWith("KOP")) {
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
}
