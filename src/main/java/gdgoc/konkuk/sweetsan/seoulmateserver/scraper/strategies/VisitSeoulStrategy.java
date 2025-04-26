package gdgoc.konkuk.sweetsan.seoulmateserver.scraper.strategies;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.base.ScraperStrategy;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.extractors.PlaceDetailsExtractor;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Implementation of the scraper strategy for the Visit Seoul website.
 */
@Slf4j
@Component
public class VisitSeoulStrategy implements ScraperStrategy {

    private static final String BASE_URL = "https://korean.visitseoul.net";
    private static final int REQUEST_DELAY_MS = 200;
    private static final int MAX_PAGES_PER_CATEGORY = 5; // Limit to 5 pages per category to avoid overloading

    private final PlaceDetailsExtractor detailsExtractor;

    @Autowired
    public VisitSeoulStrategy(PlaceDetailsExtractor detailsExtractor) {
        this.detailsExtractor = detailsExtractor;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public Map<String, String> getCategories() {
        // Updated with actual category URL parameters from current website structure
        return Map.of(
                "랜드마크",
                "/attractions?srchType=&srchOptnCode=&srchCtgry=68&sortOrder=&srchWord=&radioOptionLike=TURSM_AREA_8",
                "고궁",
                "/attractions?srchType=&srchOptnCode=&srchCtgry=69&sortOrder=&srchWord=&radioOptionLike=TURSM_AREA_8",
                "역사적 장소",
                "/attractions?srchType=&srchOptnCode=&srchCtgry=70&sortOrder=&srchWord=&radioOptionLike=TURSM_AREA_8",
                "전체", "/attractions", // Basic main attractions page
                "오래가게",
                "/attractions?srchType=&srchOptnCode=&srchCtgry=71&sortOrder=&srchWord=&radioOptionLike=TURSM_AREA_8"
                // "미술관&박물관" category seems to have an issue on the website (returns no content)
        );
    }

    @Override
    public List<Place> execute(Browser browser, BrowserContext context) {
        Map<String, Place> uniquePlaces = new HashMap<>();
        int totalAttemptedCategories = 0;
        int successfulCategories = 0;

        for (Map.Entry<String, String> category : getCategories().entrySet()) {
            String categoryName = category.getKey();
            String categoryUrl = getBaseUrl() + category.getValue();
            totalAttemptedCategories++;

            log.info("Scraping category: {} ({}/{})", categoryName, totalAttemptedCategories, getCategories().size());

            try {
                List<Place> categoryPlaces = processCategory(context, categoryName, categoryUrl);

                // Add to unique places map, avoiding duplicates
                for (Place place : categoryPlaces) {
                    String placeId = place.getGooglePlaceId(); // Using Google Place ID as unique key
                    if (placeId == null || placeId.isEmpty()) {
                        // Fallback to name if no ID
                        placeId = place.getName();
                    }

                    if (!uniquePlaces.containsKey(placeId)) {
                        uniquePlaces.put(placeId, place);
                    }
                }

                if (!categoryPlaces.isEmpty()) {
                    successfulCategories++;
                    log.info("Successfully scraped {} places from category: {}", categoryPlaces.size(), categoryName);
                } else {
                    log.warn("No places found in category: {}", categoryName);
                }

                // If we've processed at least one category successfully and have some data,
                // we continue to process all categories to get as much data as possible

                // Add a short pause between categories to avoid overwhelming the server
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            } catch (Exception e) {
                log.error("Failed to process category {}: {}", categoryName, e.getMessage());
            }
        }

        log.info("Total places scraped across all categories: {}", uniquePlaces.size());
        return new ArrayList<>(uniquePlaces.values());
    }

    @Override
    public List<Place> processCategory(BrowserContext context, String categoryName, String categoryUrl) {
        List<Place> places = new ArrayList<>();

        try (Page page = context.newPage()) {
            // Increase default timeout to handle slow loading pages
            page.setDefaultTimeout(120000);

            log.info("Navigating to category URL: {}", categoryUrl);
            page.navigate(categoryUrl);

            // Handle cookie consent popup if it appears
            try {
                // Wait a moment for cookie consent to appear
                page.waitForTimeout(3000);
                if (page.isVisible("text=모두 허용")) {
                    page.click("text=모두 허용");
                    log.info("Accepted cookies");
                    // Wait for page to reload after cookie accept
                    page.waitForTimeout(2000);
                }
            } catch (Exception e) {
                log.warn("No cookie consent banner found or unable to click it: {}", e.getMessage());
            }

            // Wait for main content to load with a longer timeout
            try {
                log.info("Waiting for main element to load");
                page.waitForSelector("main", new Page.WaitForSelectorOptions().setTimeout(60000));
                log.info("Main element loaded successfully");
            } catch (Exception e) {
                log.warn("Timeout waiting for main element: {}", e.getMessage());
                // Continue anyway
            }

            // Wait for page to be completely loaded
            try {
                log.info("Waiting for network to be idle");
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(60000));
                log.info("Network is now idle");
            } catch (Exception e) {
                log.warn("Timeout waiting for network idle: {}", e.getMessage());
                // Continue anyway
            }

            // Add a small delay to ensure JavaScript has fully executed
            page.waitForTimeout(3000);

            // Take a screenshot for debugging
            try {
                String screenshotPath = "category-" + categoryName.replaceAll("[^a-zA-Z0-9]", "_") + ".png";
                page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                log.info("Saved category page screenshot to {}", screenshotPath);
            } catch (Exception e) {
                log.warn("Failed to save screenshot: {}", e.getMessage());
            }

            // Process the first page
            List<Place> pageOnePlaces = processListingPage(page, 1, 1);

            if (!pageOnePlaces.isEmpty()) {
                log.info("Successfully processed first page with {} places", pageOnePlaces.size());
                places.addAll(pageOnePlaces);

                // Determine the total number of pages and process each one
                int totalPages = determineTotalPages(page);
                int pagesToProcess = Math.min(totalPages, MAX_PAGES_PER_CATEGORY);

                log.info("Found {} pages for category {}, will process up to {}",
                        totalPages, categoryName, pagesToProcess);

                for (int pageNum = 2; pageNum <= pagesToProcess; pageNum++) {
                    try {
                        log.info("Processing page {} of {} (category: {})", pageNum, pagesToProcess, categoryName);

                        // Construct the pagination URL
                        String pageUrl = categoryUrl;
                        if (pageUrl.contains("?")) {
                            pageUrl += "&curPage=" + pageNum;
                        } else {
                            pageUrl += "?curPage=" + pageNum;
                        }

                        log.info("Navigating to page URL: {}", pageUrl);
                        page.navigate(pageUrl);

                        // Wait for the page to load
                        try {
                            page.waitForLoadState(LoadState.NETWORKIDLE,
                                    new Page.WaitForLoadStateOptions().setTimeout(60000));
                        } catch (Exception e) {
                            log.warn("Timeout waiting for network idle on page {}: {}", pageNum, e.getMessage());
                        }

                        page.waitForTimeout(2000);

                        // Process this page
                        List<Place> pagePlaces = processListingPage(page, pageNum, totalPages);
                        places.addAll(pagePlaces);
                        log.info("Extracted {} places from page {} of category {}",
                                pagePlaces.size(), pageNum, categoryName);
                    } catch (Exception e) {
                        log.error("Error processing page {} of category {}: {}", pageNum, categoryName, e.getMessage());
                    }
                }
            } else {
                log.warn("Failed to extract any places from first page of category {}, trying fallback method",
                        categoryName);

                // Use a fallback method to directly scrape known attractions
                List<Place> fallbackPlaces = tryFallbackScrapingByDirectUrls(context, categoryName);
                places.addAll(fallbackPlaces);
            }
        } catch (Exception e) {
            log.error("Error processing category {}: {}", categoryName, e.getMessage());

            // Try fallback approach even on full category failure
            if (places.isEmpty()) {
                log.info("Using fallback approach due to category processing failure for {}", categoryName);
                List<Place> fallbackPlaces = tryFallbackScrapingByDirectUrls(context, categoryName);
                places.addAll(fallbackPlaces);
            }
        }

        log.info("Found a total of {} places in category {}", places.size(), categoryName);
        return places;
    }

    /**
     * Fallback method that tries to scrape known attractions by direct URLs when list pages fail
     */
    private List<Place> tryFallbackScrapingByDirectUrls(BrowserContext context, String categoryName) {
        log.info("Attempting fallback scraping for category {} using known URLs", categoryName);
        List<Place> places = new ArrayList<>();

        // Common well-known attractions with their IDs
        Map<String, String> knownAttractions = new HashMap<>();
        if ("고궁".equals(categoryName)) {
            knownAttractions.put("경복궁", "KOP000072");
            knownAttractions.put("창덕궁", "KOP000295");
            knownAttractions.put("덕수궁", "KOP002046");
            knownAttractions.put("창경궁", "KOP000297");
            knownAttractions.put("종묘", "KOP000507");
        } else if ("랜드마크".equals(categoryName)) {
            knownAttractions.put("남산서울타워", "KOP000036");
            knownAttractions.put("롯데월드타워", "KOP021278");
            knownAttractions.put("63스퀘어", "KOP000210");
            knownAttractions.put("북촌한옥마을", "KOP000261");
            knownAttractions.put("별마당 도서관", "KOP026558");
            knownAttractions.put("한강 이랜드크루즈", "KOP002126");
        } else if ("역사적 장소".equals(categoryName)) {
            knownAttractions.put("서울 한양도성", "KOP000090");
            knownAttractions.put("흥인지문(동대문)", "KOP001999");
            knownAttractions.put("숭례문(남대문)", "KOP022888");
            knownAttractions.put("서대문형무소역사관", "KOP001831");
            knownAttractions.put("남산골 한옥마을", "KOP000276");
        } else if ("오래가게".equals(categoryName)) {
            knownAttractions.put("익선동 한옥거리", "KOP037008");
            knownAttractions.put("삼청동 골목길", "KOP002121");
        } else {
            // Generic attractions for "전체" category or any other category
            knownAttractions.put("경복궁", "KOP000072");
            knownAttractions.put("남산서울타워", "KOP000036");
            knownAttractions.put("창덕궁", "KOP000295");
            knownAttractions.put("서울 한양도성", "KOP000090");
            knownAttractions.put("북촌한옥마을", "KOP000261");
            knownAttractions.put("롯데월드타워", "KOP021278");
            knownAttractions.put("익선동 한옥거리", "KOP037008");
        }

        for (Map.Entry<String, String> attraction : knownAttractions.entrySet()) {
            try {
                String name = attraction.getKey();
                String id = attraction.getValue();
                String encodedName = encodeKoreanNameForUrl(name);
                String detailUrl = String.format("%s/attractions/%s/%s", getBaseUrl(), encodedName, id);

                log.info("Trying direct URL for {}: {}", name, detailUrl);

                try (Page detailPage = context.newPage()) {
                    detailPage.setDefaultTimeout(60000);
                    detailPage.navigate(detailUrl);

                    try {
                        detailPage.waitForLoadState(LoadState.NETWORKIDLE,
                                new Page.WaitForLoadStateOptions().setTimeout(60000));
                    } catch (Exception e) {
                        log.warn("Timeout waiting for network idle for {}: {}", name, e.getMessage());
                    }

                    Place place = processDetailPage(detailPage, id, name, "");
                    if (place != null) {
                        places.add(place);
                        log.info("Successfully added {} from direct URL", name);
                    }
                }

                // Small delay between requests
                Thread.sleep(1000);

            } catch (Exception e) {
                log.warn("Error with fallback scraping for attraction: {}", e.getMessage());
            }
        }

        log.info("Fallback scraping found {} places for category {}", places.size(), categoryName);
        return places;
    }

    /**
     * Helper method to encode Korean names for URL
     */
    private String encodeKoreanNameForUrl(String name) {
        // Simple replacement of spaces with hyphens
        return name.replace(" ", "-");
    }

    @Override
    public List<Place> processListingPage(Page page, int pageNum, int totalPages) {
        List<Place> places = new ArrayList<>();

        try {
            // Get place listing elements with multiple selector patterns
            List<ElementHandle> placeElements = new ArrayList<>();

            // Try multiple selectors in sequence to find place items
            String[] itemSelectors = {
                    "list[ref*='e163'] > listitem",
                    "main list > listitem",
                    "list > listitem"
            };

            for (String selector : itemSelectors) {
                try {
                    List<ElementHandle> elements = page.querySelectorAll(selector);
                    if (!elements.isEmpty()) {
                        log.info("Found {} place elements using selector: {}", elements.size(), selector);
                        placeElements = elements;
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Error finding place elements with selector {}: {}", selector, e.getMessage());
                }
            }

            // If still no elements found, try a more general approach
            if (placeElements.isEmpty()) {
                log.warn("No place elements found with specific selectors on page {}, trying general approach",
                        pageNum);
                try {
                    // Look for links that contain attraction URLs
                    List<ElementHandle> allLinks = page.querySelectorAll("main a[href*='/attractions/']");
                    if (!allLinks.isEmpty()) {
                        log.info("Found {} potential place links using general selector", allLinks.size());
                        placeElements = allLinks;
                    }
                } catch (Exception e) {
                    log.error("Error with fallback approach: {}", e.getMessage());
                }
            }

            log.info("Found {} total tourist places on page {}", placeElements.size(), pageNum);

            for (int i = 0; i < placeElements.size(); i++) {
                ElementHandle element = placeElements.get(i);

                // Get detail URL
                String detailUrl = null;

                try {
                    // Try to get href directly from the element or from a child link
                    if (element.getAttribute("href") != null) {
                        detailUrl = element.getAttribute("href");
                    } else {
                        ElementHandle linkElement = element.querySelector("a[href], link");
                        if (linkElement != null) {
                            detailUrl = linkElement.getAttribute("href");
                        }
                    }

                    // Ensure URL is absolute
                    if (detailUrl != null && !detailUrl.isEmpty()) {
                        if (!detailUrl.startsWith("http")) {
                            if (detailUrl.startsWith("/")) {
                                detailUrl = getBaseUrl() + detailUrl;
                            } else {
                                detailUrl = getBaseUrl() + "/" + detailUrl;
                            }
                        }
                    } else {
                        log.warn("Empty detail URL for place at index {} on page {}, skipping", i, pageNum);
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Error getting URL from element at index {} on page {}: {}", i, pageNum, e.getMessage());
                    continue;
                }

                // Extract place ID for deduplication
                String placeId = detailsExtractor.extractPlaceIdFromUrl(detailUrl);
                if (placeId == null || placeId.isEmpty()) {
                    log.warn("Could not extract ID from URL: {}, will use full URL as ID", detailUrl);
                    placeId = detailUrl;
                }

                // Extract basic info from the element
                String name = "";
                String shortDescription = "";

                try {
                    // Get text content from the element
                    String elementText = element.textContent().trim();

                    // Parse the text to extract name and description
                    String[] parts = elementText.split("\\s+", 2);
                    if (parts.length > 0) {
                        name = parts[0].trim();
                        if (parts.length > 1) {
                            shortDescription = parts[1].trim();
                        }
                    }

                    // If the name is too short, try an alternative approach
                    if (name.length() < 2) {
                        // Try finding a nested element with the place name
                        ElementHandle nameElement = element.querySelector("generic, text, emphasis");
                        if (nameElement != null) {
                            name = nameElement.textContent().trim();
                        }
                    }

                    // If still no name, use a generic placeholder
                    if (name.length() < 2) {
                        name = "관광지 " + (i + 1);
                        log.warn("Could not extract name for place at index {} on page {}, using placeholder",
                                i, pageNum);
                    }
                } catch (Exception e) {
                    log.warn("Error extracting name and description at index {} on page {}: {}",
                            i, pageNum, e.getMessage());
                    name = "관광지 " + (i + 1);
                }

                log.info("Processing place {}/{} on page {}: {} (ID: {})",
                        (i + 1), placeElements.size(), pageNum, name, placeId);

                // Process detail page to get complete place information
                try (Page detailPage = page.context().newPage()) {
                    detailPage.setDefaultTimeout(60000);
                    Place place = processDetailPage(detailPage, placeId, name, shortDescription);
                    if (place != null) {
                        places.add(place);
                        log.info("Successfully processed place: {}", name);
                    }
                }

                // Add small delay to prevent overwhelming the server
                page.waitForTimeout(REQUEST_DELAY_MS);
            }

        } catch (Exception e) {
            log.error("Error processing listing page {}: {}", pageNum, e.getMessage());
        }

        return places;
    }

    @Override
    public Place processDetailPage(Page detailPage, String placeId, String name, String shortDescription) {
        try {
            // Create URL from the place ID and name
            String encodedName = encodeKoreanNameForUrl(name);
            String detailUrl = String.format("%s/attractions/%s/%s", getBaseUrl(), encodedName, placeId);

            log.info("Navigating to detail page: {}", detailUrl);
            detailPage.navigate(detailUrl);

            try {
                detailPage.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(60000));
            } catch (Exception e) {
                log.warn("Timeout waiting for network idle on detail page for {}: {}", name, e.getMessage());
            }

            // Wait a bit for dynamic content to load
            detailPage.waitForTimeout(2000);

            // Extract description, address, and coordinates
            String description = detailsExtractor.extractDescription(detailPage, shortDescription);
            String address = detailsExtractor.extractAddress(detailPage);
            Place.Coordinate coordinate = detailsExtractor.extractCoordinates(detailPage);

            // Log if no coordinates but address is available for later geocoding
            if ((coordinate.getLatitude() == null || coordinate.getLongitude() == null) && !address.isEmpty()) {
                log.info("No coordinates found for {}, but address is available for geocoding: {}",
                        name, address);
            }

            // Construct and return Place object
            Place place = Place.builder()
                    .name(name)
                    .description(description)
                    .coordinate(coordinate)
                    .googlePlaceId(placeId)
                    .build();

            log.info("Successfully created Place object for: {} with coordinates: {},{}",
                    name,
                    coordinate.getLatitude() != null ? coordinate.getLatitude() : "null",
                    coordinate.getLongitude() != null ? coordinate.getLongitude() : "null");

            return place;

        } catch (Exception e) {
            log.error("Error processing detail page for {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Determine the total number of pages for a category.
     *
     * @param page The current page
     * @return Total number of pages
     */
    private int determineTotalPages(Page page) {
        // Set a maximum limit for pages to scrape as a failsafe
        final int MAX_PAGES_LIMIT = 100;

        try {
            // Try multiple selectors for finding the last page link
            List<String> lastPageSelectors = Arrays.asList(
                    "link:has-text('마지막 페이지')",
                    "a[href*='curPage']:last-of-type",
                    "link[ref*='296']",
                    "generic[ref*='280'] > link:last-of-type"
            );

            for (String selector : lastPageSelectors) {
                try {
                    ElementHandle lastPageElement = page.querySelector(selector);
                    if (lastPageElement != null) {
                        String href = lastPageElement.getAttribute("href");
                        if (href != null && href.contains("curPage=")) {
                            // Extract the page number
                            Matcher matcher = Pattern.compile("curPage=(\\d+)").matcher(href);
                            if (matcher.find()) {
                                int pages = Integer.parseInt(matcher.group(1));
                                log.info("Found total pages from selector '{}': {}", selector, pages);
                                return Math.min(pages, MAX_PAGES_LIMIT);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error with selector: {}", selector, e);
                }
            }

            // Try multiple selectors for pagination buttons
            List<String> paginationSelectors = Arrays.asList(
                    "pagination button",
                    "generic[ref*='280'] > link",
                    "link[href*='curPage']"
            );

            for (String selector : paginationSelectors) {
                try {
                    List<ElementHandle> paginationElements = page.querySelectorAll(selector);
                    if (!paginationElements.isEmpty()) {
                        // Get the text of the last numeric pagination element
                        for (int i = paginationElements.size() - 1; i >= 0; i--) {
                            String text = paginationElements.get(i).textContent().trim();
                            // Try to extract the page number
                            Matcher matcher = Pattern.compile("\\d+").matcher(text);
                            if (matcher.find()) {
                                try {
                                    int pages = Integer.parseInt(matcher.group());
                                    log.info("Found total pages from pagination elements: {}", pages);
                                    return Math.min(pages, MAX_PAGES_LIMIT);
                                } catch (NumberFormatException e) {
                                    // Not a valid number, continue
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error with pagination selector: {}", selector, e);
                }
            }

            // Try one last approach - directly look for URLs in the page
            try {
                String pageContent = page.content();
                Pattern pagePattern = Pattern.compile("curPage=(\\d+)");
                Matcher matcher = pagePattern.matcher(pageContent);
                int highestPage = 0;

                while (matcher.find()) {
                    try {
                        int pageNum = Integer.parseInt(matcher.group(1));
                        if (pageNum > highestPage) {
                            highestPage = pageNum;
                        }
                    } catch (NumberFormatException e) {
                        // Ignore if not a valid number
                    }
                }

                if (highestPage > 0) {
                    log.info("Found total pages from page content analysis: {}", highestPage);
                    return Math.min(highestPage, MAX_PAGES_LIMIT);
                }
            } catch (Exception e) {
                log.warn("Error analyzing page content for pagination", e);
            }
        } catch (Exception e) {
            log.warn("Error determining total pages", e);
        }

        // Default to 5 pages if we couldn't determine the total (better than just 1)
        log.info("Could not determine total pages, defaulting to 5 pages");
        return 5;
    }
}
