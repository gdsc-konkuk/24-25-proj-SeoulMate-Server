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

    private static final String BASE_URL = "https://korean.visitseoul.net";
    private static final String ALL_ATTRACTIONS_URL = BASE_URL + "/attractions";
    private static final String SOURCE_NAME = "visitseoul";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36";

    /**
     * Asynchronously retrieves tourist place source data from Visit Seoul website.
     *
     * @return CompletableFuture with a list of PlaceSourceData objects
     */
    @Override
    public CompletableFuture<List<PlaceSourceData>> findAllAsync() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting asynchronous data retrieval from Visit Seoul website");
            List<PlaceSourceData> sourceDataList = new ArrayList<>();

            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium()
                         .launch(new BrowserType.LaunchOptions()
                                 .setHeadless(true)
                                 .setTimeout(60000)
                                 .setArgs(Arrays.asList(
                                         "--disable-extensions",
                                         "--disable-dev-shm-usage",
                                         "--no-sandbox",
                                         "--disable-features=IsolateOrigins,site-per-process",
                                         "--disable-web-security")))) {
                // Create browser context with realistic user agent
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(USER_AGENT)
                        .setViewportSize(1920, 1080));

                try (Page page = context.newPage()) {
                    // Set default timeout
                    page.setDefaultTimeout(30000);

                    // Navigate to the main attractions page
                    log.info("Navigating to the main attractions page: {}", ALL_ATTRACTIONS_URL);
                    page.navigate(ALL_ATTRACTIONS_URL);

                    // Handle cookie consent if necessary
                    handleCookieConsent(page);

                    // Wait for page content to load
                    page.waitForLoadState(LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(30000));

                    // Process first page
                    sourceDataList.addAll(processListingPage(page, 1));

                    // Determine total number of pages
                    int totalPages = determineTotalPages(page);
                    log.info("Found {} total pages, will process up to {}", totalPages, totalPages);

                    // Process remaining pages
                    for (int pageNum = 2; pageNum <= totalPages; pageNum++) {
                        try {
                            log.info("Processing page {} of {}", pageNum, totalPages);

                            // Construct page URL
                            String pageUrl = ALL_ATTRACTIONS_URL + "?curPage=" + pageNum;

                            // Navigate to page
                            page.navigate(pageUrl);

                            // Wait for page to load
                            page.waitForLoadState(LoadState.NETWORKIDLE,
                                    new Page.WaitForLoadStateOptions().setTimeout(30000));

                            // Process this page
                            List<PlaceSourceData> pageSourceData = processListingPage(page, pageNum);
                            sourceDataList.addAll(pageSourceData);

                            log.info("Extracted {} places from page {}", pageSourceData.size(), pageNum);
                        } catch (Exception e) {
                            log.error("Error processing page {}: {}", pageNum, e.getMessage());
                        }
                    }
                }

                log.info("Completed data retrieval. Total places found: {}", sourceDataList.size());
            } catch (Exception e) {
                log.error("Error during Visit Seoul data retrieval process", e);
            }

            return sourceDataList;
        });
    }

    /**
     * Handle cookie consent popup if it appears
     *
     * @param page The current page
     */
    private void handleCookieConsent(Page page) {
        try {
            // Wait a moment for cookie consent to appear
            page.waitForTimeout(2000);
            if (page.isVisible("text=모두 허용")) {
                page.click("text=모두 허용");
                log.info("Accepted cookies");
                // Wait for page to reload after cookie accept
                page.waitForTimeout(1000);
            }
        } catch (Exception e) {
            log.debug("No cookie consent banner found or unable to click it: {}", e.getMessage());
        }
    }

    /**
     * Process a listing page to extract place information.
     *
     * @param page    The page to process
     * @param pageNum The current page number
     * @return List of place source data found on the page
     */
    private List<PlaceSourceData> processListingPage(Page page, int pageNum) {
        List<PlaceSourceData> sourceDataList = new ArrayList<>();

        try {
            // Use a specific selector for the list items based on DOM analysis
            String listItemSelector = "main list[ref*='e163'] > listitem";

            List<ElementHandle> placeItems = page.querySelectorAll(listItemSelector);

            // If the primary selector doesn't work, try alternative selectors
            if (placeItems.isEmpty()) {
                String[] alternativeSelectors = {
                        "main list > listitem",
                        "list > listitem",
                        "main a[href*='/attractions/']"
                };

                for (String selector : alternativeSelectors) {
                    placeItems = page.querySelectorAll(selector);
                    if (!placeItems.isEmpty()) {
                        log.info("Found {} place elements using selector: {}", placeItems.size(), selector);
                        break;
                    }
                }
            } else {
                log.info("Found {} place elements using primary selector", placeItems.size());
            }

            log.info("Found {} place elements on page {}", placeItems.size(), pageNum);

            // Process each place item
            for (int i = 0; i < placeItems.size(); i++) {
                ElementHandle item = placeItems.get(i);

                try {
                    // Based on DOM analysis, extract details more precisely
                    PlaceScrapingResult result = extractPlaceFromElement(item);

                    // Convert the scraping result to PlaceSourceData
                    if (result != null && result.name() != null && !result.name().isEmpty()) {
                        PlaceSourceData sourceData = PlaceSourceData.builder()
                                .name(result.name())
                                .description(result.description())
                                .sourceId(result.visitSeoulId())
                                .sourceName(SOURCE_NAME)
                                .build();

                        sourceDataList.add(sourceData);
                        log.debug("Added place source data: {}", sourceData.getName());
                    }
                } catch (Exception e) {
                    log.warn("Error processing place element at index {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error processing listing page {}: {}", pageNum, e.getMessage());
        }

        return sourceDataList;
    }

    /**
     * Temporary class to hold scraping results
     */
    private record PlaceScrapingResult(String name, String description, String visitSeoulId) {
    }

    /**
     * Extract place information from a DOM element based on the site's structure.
     *
     * @param element The element containing place information
     * @return A PlaceScrapingResult object with scraped information
     */
    private PlaceScrapingResult extractPlaceFromElement(ElementHandle element) {
        try {
            // First try to get the link element which contains the place details
            ElementHandle linkElement = element;

            // If the current element is not a link, try to find a link inside it
            if (element.getAttribute("href") == null) {
                linkElement = element.querySelector("a[href*='/attractions/']");
                if (linkElement == null) {
                    linkElement = element.querySelector("link, a");
                    if (linkElement == null) {
                        log.warn("Could not find link element in place item");
                        return null;
                    }
                }
            }

            // Extract the Visit Seoul Place ID from the URL if available
            String href = linkElement.getAttribute("href");
            String visitSeoulId = null;

            if (href != null && !href.isEmpty()) {
                visitSeoulId = extractVisitSeoulIdFromUrl(href);

                // Also extract the name from URL as it's more reliable than text parsing
                String nameFromUrl = extractNameFromUrl(href);
                if (nameFromUrl != null && !nameFromUrl.isEmpty()) {
                    // Build the result with name from URL and extract description separately
                    return buildResultWithSeparateDescription(linkElement, nameFromUrl, visitSeoulId);
                }
            }

            // Fallback to text content-based extraction if we couldn't get name from URL
            String fullText = linkElement.textContent().trim();

            // Try to find natural breakpoint between name and description
            int breakIndex = findNameDescriptionBreakpoint(fullText);

            String name, description;
            if (breakIndex > 0) {
                name = fullText.substring(0, breakIndex).trim();
                description = cleanDescription(fullText.substring(breakIndex).trim());
            } else {
                // If no clear breakpoint, use the old space-based splitting as last resort
                String[] parts = fullText.split("\\s+", 2);
                name = parts[0].trim();
                description = parts.length > 1 ? cleanDescription(parts[1].trim()) : "";
            }

            // Check if name is reasonable
            if (name.length() < 2 || name.length() > 30) {
                log.warn("Extracted name seems invalid: '{}', trying alternative extraction", name);

                // Try DOM-based extraction as last resort
                ElementHandle genericContent = linkElement.querySelector("generic");
                if (genericContent != null) {
                    List<ElementHandle> textNodes = genericContent.querySelectorAll("text");
                    if (!textNodes.isEmpty()) {
                        name = textNodes.get(0).textContent().trim();
                        if (textNodes.size() > 1) {
                            description = cleanDescription(textNodes.get(1).textContent().trim());
                        }
                    }
                }
            }

            return new PlaceScrapingResult(name, description, visitSeoulId);

        } catch (Exception e) {
            log.warn("Error extracting place information from element: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build a PlaceScrapingResult object with name from URL and extract description separately to avoid name text
     * appearing in the description.
     *
     * @param element      The element containing place information
     * @param name         The name extracted from URL
     * @param visitSeoulId The Visit Seoul place ID extracted from URL
     * @return A PlaceScrapingResult object with properly separated name and description
     */
    private PlaceScrapingResult buildResultWithSeparateDescription(ElementHandle element, String name,
                                                                   String visitSeoulId) {
        String description = "";

        try {
            // Get the full text content
            String fullText = element.textContent().trim();

            // Remove the name and any duplicated portions from the full text to get the description
            // First try exact name match
            String cleanText = fullText.replace(name, "").trim();

            // If exact match doesn't create a good break, try matching the first part of the name
            // (useful for cases like "서울 한양도성" where only "서울" might appear at the start)
            if (cleanText.startsWith(name.split("\\s+")[0])) {
                // Find where the actual description starts after the name portion
                int descriptionStart = findDescriptionStart(fullText, name);
                if (descriptionStart > 0 && descriptionStart < fullText.length()) {
                    cleanText = fullText.substring(descriptionStart).trim();
                }
            }

            // Clean the remaining text as the description
            description = cleanDescription(cleanText);

        } catch (Exception e) {
            log.warn("Error extracting description for '{}': {}", name, e.getMessage());
        }

        return new PlaceScrapingResult(name, description, visitSeoulId);
    }

    /**
     * Finds a natural breakpoint between name and description in text. Looks for patterns like spacing changes,
     * punctuation, etc.
     *
     * @param text The full text to analyze
     * @return The index position where description likely starts, or -1 if not found
     */
    private int findNameDescriptionBreakpoint(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }

        // Case 1: Check for multiple spaces or newlines as separators
        Matcher spaceSeparator = Pattern.compile("\\S(\\s{2,})\\S").matcher(text);
        if (spaceSeparator.find()) {
            return spaceSeparator.start() + 1;
        }

        // Case 2: Look for a short name (1-5 words) followed by a longer description
        Matcher nameDescPattern = Pattern.compile("^([\\S\\s]{2,50}?)(\\s+[\\S\\s]{50,})$").matcher(text);
        if (nameDescPattern.find()) {
            return nameDescPattern.start(2);
        }

        // Case 3: Check if there's a clear character case transition (e.g., "명동성당 The cathedral...")
        for (int i = 10; i < Math.min(text.length(), 50); i++) {
            if (Character.isUpperCase(text.charAt(i)) &&
                    text.charAt(i - 1) == ' ' &&
                    Character.UnicodeBlock.of(text.charAt(i - 2)) == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return i - 1;
            }
        }

        return -1; // No clear breakpoint found
    }

    /**
     * Finds where the description starts in the full text, given the name. This handles cases where name parts are
     * repeated in the full text.
     *
     * @param fullText The complete text
     * @param name     The name to search for
     * @return The index where description starts
     */
    private int findDescriptionStart(String fullText, String name) {
        // Split name into words
        String[] nameWords = name.split("\\s+");

        // Try to find the last occurrence of any name word
        int lastNameWordPos = -1;

        for (String word : nameWords) {
            if (word.length() < 2) {
                continue; // Skip very short words
            }

            int pos = fullText.indexOf(word);
            // If the word is found, and it's further in the text than our current position
            if (pos > -1 && pos > lastNameWordPos) {
                lastNameWordPos = pos + word.length();
            }
        }

        // If we found a name word position, use it as the start
        if (lastNameWordPos > -1) {
            // Skip any spaces after the name
            while (lastNameWordPos < fullText.length() &&
                    Character.isWhitespace(fullText.charAt(lastNameWordPos))) {
                lastNameWordPos++;
            }
            return lastNameWordPos;
        }

        // Fallback: skip the first few words as they're likely part of the name
        String[] allWords = fullText.split("\\s+");
        if (allWords.length > 3) {
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                prefix.append(allWords[i]).append(" ");
            }
            int prefixLength = prefix.toString().trim().length();
            return Math.min(prefixLength + 1, fullText.length());
        }

        return 0; // Fallback to beginning of text
    }

    /**
     * Extract name from URL path for more reliable name extraction. Example: "/attractions/경복궁/KOP000072" would extract
     * "경복궁"
     *
     * @param url The URL to parse
     * @return The extracted name or null if not found
     */
    private String extractNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // First check if it's a valid attractions URL
            if (!url.contains("/attractions/")) {
                return null;
            }

            // Parse the URL path to get the attraction name
            // Format is typically: /attractions/NAME/ID
            String[] pathParts = url.split("/");

            // Find the part after "attractions"
            for (int i = 0; i < pathParts.length - 1; i++) {
                if ("attractions".equals(pathParts[i]) && i + 1 < pathParts.length) {
                    String encodedName = pathParts[i + 1];

                    // Replace URL encoding for hyphens with spaces
                    return encodedName.replace("-", " ").trim();
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting name from URL: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Clean the description text by removing unwanted content such as reviews, extra spaces, etc.
     *
     * @param rawDescription The raw description text scraped from the page
     * @return A cleaned description
     */
    private String cleanDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isEmpty()) {
            return "";
        }

        // Remove review information (pattern: "평점:X.X XX reviews")
        rawDescription = rawDescription.replaceAll("평점:\\d+\\.\\d+\\s+\\d+\\s+reviews", "").trim();

        // Remove just the "reviews" text
        rawDescription = rawDescription.replaceAll("\\d+\\s+reviews", "").trim();

        // Remove excessive whitespace (multiple spaces, newlines, tabs)
        rawDescription = rawDescription.replaceAll("\\s+", " ").trim();

        return rawDescription;
    }

    /**
     * Extracts Visit Seoul place ID from URL. This is the website's internal ID format that typically looks like
     * "KOP000072". Note: This is NOT the same as a Google Place ID. This ID is only used temporarily during the
     * scraping process for search optimization.
     *
     * @param url URL to extract from
     * @return Visit Seoul place ID or null if not found
     */
    private String extractVisitSeoulIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // Pattern to match KOP... format IDs
            Pattern pattern = Pattern.compile("KOP\\w+");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group();
            }
        } catch (Exception e) {
            log.debug("Error extracting Visit Seoul place ID from URL: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Determine the total number of pages available for scraping.
     *
     * @param page The current page
     * @return Total number of pages
     */
    private int determineTotalPages(Page page) {
        try {
            // Try to find the last page link
            ElementHandle lastPageElement = page.querySelector(
                    "link[href*='curPage']:last-of-type, a[href*='curPage']:last-of-type");
            if (lastPageElement != null) {
                String href = lastPageElement.getAttribute("href");
                if (href != null && href.contains("curPage=")) {
                    // Extract the page number
                    Pattern pattern = Pattern.compile("curPage=(\\d+)");
                    Matcher matcher = pattern.matcher(href);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                }
            }

            // If you can't find last page link, try pagination elements
            List<ElementHandle> paginationElements = page.querySelectorAll("a[href*='curPage']");
            int maxPage = 1;

            for (ElementHandle element : paginationElements) {
                try {
                    String href = element.getAttribute("href");
                    if (href != null && href.contains("curPage=")) {
                        Pattern pattern = Pattern.compile("curPage=(\\d+)");
                        Matcher matcher = pattern.matcher(href);
                        if (matcher.find()) {
                            int pageNum = Integer.parseInt(matcher.group(1));
                            if (pageNum > maxPage) {
                                maxPage = pageNum;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore parsing errors for individual pagination elements
                }
            }

            if (maxPage > 1) {
                return maxPage;
            }
        } catch (Exception e) {
            log.warn("Error determining total pages: {}", e.getMessage());
        }

        // Default to 5 pages if we couldn't determine
        return 5;
    }
}
