package gdgoc.konkuk.sweetsan.seoulmateserver.scraper.strategies;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.base.ScraperStrategy;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.extractors.PlaceDetailsExtractor;
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
        return Map.of(
            "전체", "/attractions",
            "랜드마크", "/attractions?categoryGroup=랜드마크",
            "고궁", "/attractions?categoryGroup=고궁",
            "역사적 장소", "/attractions?categoryGroup=역사적 장소",
            "오래가게", "/attractions?categoryGroup=오래가게",
            "미술관&박물관", "/attractions?categoryGroup=미술관&박물관"
        );
    }

    @Override
    public List<Place> execute(Browser browser, BrowserContext context) {
        Map<String, Place> uniquePlaces = new HashMap<>();
        
        for (Map.Entry<String, String> category : getCategories().entrySet()) {
            String categoryName = category.getKey();
            String categoryUrl = getBaseUrl() + category.getValue();
            
            log.info("Scraping category: {}", categoryName);
            
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
        }
        
        return new ArrayList<>(uniquePlaces.values());
    }

    @Override
    public List<Place> processCategory(BrowserContext context, String categoryName, String categoryUrl) {
        List<Place> places = new ArrayList<>();
        Page page = null;
        
        try {
            page = context.newPage();
            page.setDefaultTimeout(60000);
            
            log.info("Navigating to category URL: {}", categoryUrl);
            page.navigate(categoryUrl);
            
            // Handle cookie consent popup if it appears
            try {
                if (page.isVisible("text=모두 허용")) {
                    page.click("text=모두 허용");
                    log.info("Accepted cookies");
                }
            } catch (Exception e) {
                log.warn("No cookie consent banner found or unable to click it");
            }
            
            // Wait for main content to load
            page.waitForSelector("main list", new Page.WaitForSelectorOptions().setTimeout(30000));
            
            // Find total number of pages
            int totalPages = determineTotalPages(page);
            log.info("Category {} has {} pages", categoryName, totalPages);
            
            // Process each page
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                try {
                    // Navigate to specific page if not already on it
                    if (pageNum > 1) {
                        log.info("Navigating to page {} of {}", pageNum, totalPages);
                        String pageUrl = categoryUrl + (categoryUrl.contains("?") ? "&" : "?") + "curPage=" + pageNum;
                        page.navigate(pageUrl);
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                    }
                    
                    // Process places on this page
                    List<Place> pagePlaces = processListingPage(page, pageNum, totalPages);
                    places.addAll(pagePlaces);
                    
                } catch (Exception e) {
                    log.error("Error processing page {} of category {}: {}", 
                            pageNum, categoryName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing category {}: {}", categoryName, e.getMessage());
        } finally {
            if (page != null) {
                page.close();
            }
        }
        
        return places;
    }

    @Override
    public List<Place> processListingPage(Page page, int pageNum, int totalPages) {
        List<Place> places = new ArrayList<>();
        
        try {
            // Get place listing elements
            List<ElementHandle> placeElements = page.querySelectorAll("main list > listitem");
            log.info("Found {} tourist places on page {}", placeElements.size(), pageNum);
            
            for (int i = 0; i < placeElements.size(); i++) {
                ElementHandle element = placeElements.get(i);
                
                // Get link element
                ElementHandle linkElement = element.querySelector("link");
                if (linkElement == null) {
                    log.warn("No link element found for place at index {}, skipping", i);
                    continue;
                }
                
                // Get detail URL
                String detailUrl = linkElement.getAttribute("href");
                if (detailUrl == null || detailUrl.isEmpty()) {
                    log.warn("Empty detail URL for place at index {}, skipping", i);
                    continue;
                }
                
                // Extract place ID for deduplication
                String placeId = detailsExtractor.extractPlaceIdFromUrl(detailUrl);
                if (placeId == null || placeId.isEmpty()) {
                    log.warn("Could not extract ID from URL: {}, will use full URL as ID", detailUrl);
                    placeId = detailUrl;
                }
                
                // Complete URL if it's relative
                if (!detailUrl.startsWith("http")) {
                    detailUrl = getBaseUrl() + detailUrl;
                }
                
                // Extract basic info from listing
                String linkText = linkElement.textContent();
                String[] parts = linkText.split("[\\n\\r]", 2);
                String name = parts[0].trim();
                String shortDescription = parts.length > 1 ? parts[1].trim() : "";
                
                log.info("Processing place {}/{} on page {}: {} (ID: {})",
                        (i + 1), placeElements.size(), pageNum, name, placeId);
                
                // Process detail page to get complete place information
                try (Page detailPage = page.context().newPage()) {
                    Place place = processDetailPage(detailPage, placeId, name, shortDescription);
                    if (place != null) {
                        places.add(place);
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
            // Create URL from the place ID
            String detailUrl = String.format("%s/attractions/%s/%s", getBaseUrl(), 
                    name.replaceAll("[\\s/]", "-"), placeId);
                    
            detailPage.navigate(detailUrl);
            detailPage.waitForLoadState(LoadState.NETWORKIDLE);
            
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
                    .build();
                
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
        try {
            // Try to find the last page link element
            ElementHandle lastPageElement = page.querySelector("link:has-text('마지막 페이지')");
            if (lastPageElement != null) {
                String href = lastPageElement.getAttribute("href");
                if (href != null && href.contains("curPage=")) {
                    String lastPageText = href.substring(href.indexOf("curPage=") + 8);
                    try {
                        return Integer.parseInt(lastPageText);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse last page number: {}", lastPageText);
                    }
                }
            }
            
            // Try to find pagination elements
            List<ElementHandle> paginationElements = page.querySelectorAll("pagination button");
            if (!paginationElements.isEmpty()) {
                // Get the text of the last numeric pagination element
                for (int i = paginationElements.size() - 1; i >= 0; i--) {
                    String text = paginationElements.get(i).textContent().trim();
                    try {
                        return Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        // Not a number, continue to the next element
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error determining total pages", e);
        }
        
        // Default to 1 page if we couldn't determine the total
        return 1;
    }
}
