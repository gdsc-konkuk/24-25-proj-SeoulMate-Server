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

    // Patterns
    private static final Pattern VISIT_SEOUL_ID_PATTERN = Pattern.compile("KOP\\w+");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("curPage=(\\d+)");

    // CSS Selectors for place items
    private static final String[] PLACE_ITEM_SELECTORS = {
            "main > generic > generic > list > listitem",
            "main list > listitem",
            "list > listitem"
    };

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

            // Find and process place items
            List<ElementHandle> placeItems = findPlaceItems(page);
            log.info("Found {} place items on page {}", placeItems.size(), pageNum);

            for (int i = 0; i < placeItems.size(); i++) {
                PlaceSourceData place = extractPlaceData(placeItems.get(i), i);
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
            if (page.isVisible("text=모두 허용")) {
                page.click("text=모두 허용");
                log.debug("Accepted cookies");
                page.waitForTimeout(1000);
            }
        } catch (Exception ignored) {
            // Cookie consent may not appear, so ignore any errors
        }
    }

    /**
     * Finds all place items on the current page using various selectors.
     */
    private List<ElementHandle> findPlaceItems(Page page) {
        for (String selector : PLACE_ITEM_SELECTORS) {
            List<ElementHandle> items = page.querySelectorAll(selector);
            if (!items.isEmpty()) {
                log.debug("Found {} items using selector: {}", items.size(), selector);
                return items;
            }
        }
        log.debug("No place items found with standard selectors, trying direct links");

        // Last resort: try to find links directly
        return page.querySelectorAll("a[href*='/attractions/']");
    }

    /**
     * Extracts place data from an element and creates a PlaceSourceData object.
     */
    private PlaceSourceData extractPlaceData(ElementHandle element, int index) {
        try {
            ElementHandle linkElement = findLinkElement(element);
            if (linkElement == null) {
                return null;
            }

            String href = linkElement.getAttribute("href");
            String visitSeoulId = extractVisitSeoulId(href);

            // Extract name and description from DOM structure (only method we use)
            String[] nameAndDesc = extractFromDomStructure(linkElement);

            // Create place object if extraction was successful
            if (nameAndDesc != null && nameAndDesc[0] != null && !nameAndDesc[0].isEmpty()) {
                PlaceSourceData place = PlaceSourceData.builder()
                        .name(nameAndDesc[0])
                        .description(nameAndDesc[1])
                        .sourceId(visitSeoulId)
                        .sourceName(SOURCE_NAME)
                        .build();

                logExtractedPlace(place);
                return place;
            }
        } catch (Exception e) {
            log.warn("Error extracting place at index {}: {}", index, e.getMessage());
        }

        return null;
    }

    /**
     * Finds the link element within a place item.
     */
    private ElementHandle findLinkElement(ElementHandle element) {
        // If the element itself is a link, use it
        if (element.getAttribute("href") != null) {
            return element;
        }

        // Otherwise, look for a link inside
        return element.querySelector("a[href*='/attractions/'], link[href*='/attractions/']");
    }

    /**
     * Extracts the Visit Seoul ID from a URL.
     */
    private String extractVisitSeoulId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Matcher matcher = VISIT_SEOUL_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Extracts name and description from DOM structure.
     *
     * @return String array where [0] is name and [1] is description, or null if extraction failed
     */
    private String[] extractFromDomStructure(ElementHandle linkElement) {
        try {
            ElementHandle genericContent = linkElement.querySelector("generic");
            if (genericContent != null) {
                List<ElementHandle> textNodes = genericContent.querySelectorAll("text");

                if (textNodes.size() >= 2) {
                    // 첫 번째 text 노드가 이름, 두 번째 text 노드가 설명
                    String name = textNodes.get(0).textContent().trim();
                    String description = textNodes.get(1).textContent().trim();

                    if (!name.isEmpty()) {
                        log.debug("DOM extraction successful: name='{}', desc='{}'",
                                name, truncateText(description, 30));
                        return new String[]{name, description};
                    }
                }
            }
        } catch (Exception e) {
            log.debug("DOM extraction failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Determines the total number of pages available for scraping.
     */
    private int determineTotalPages(Page page) {
        try {
            // Try pagination links
            List<ElementHandle> paginationLinks = page.querySelectorAll("a[href*='curPage']");
            int maxPage = 1;

            for (ElementHandle link : paginationLinks) {
                String href = link.getAttribute("href");
                if (href != null && href.contains("curPage=")) {
                    Matcher matcher = PAGE_NUMBER_PATTERN.matcher(href);
                    if (matcher.find()) {
                        int pageNum = Integer.parseInt(matcher.group(1));
                        maxPage = Math.max(maxPage, pageNum);
                    }
                }
            }

            if (maxPage > 1) {
                return maxPage;
            }

            // Try last page link
            ElementHandle lastPageLink = page.querySelector(
                    "a[href*='curPage']:last-of-type, link[href*='curPage']:last-of-type");
            if (lastPageLink != null) {
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

        return 5; // Default if we can't determine
    }

    /**
     * Logs information about an extracted place.
     */
    private void logExtractedPlace(PlaceSourceData place) {
        String descSummary = truncateText(place.getDescription(), 30);
        log.info("Extracted place: name='{}', desc='{}', id='{}'",
                place.getName(), descSummary, place.getSourceId());
    }

    /**
     * Truncates text to a specific length with ellipsis if needed.
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
