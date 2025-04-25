package gdgoc.konkuk.sweetsan.seoulmateserver.scraper.extractors;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting place details from web pages.
 * Contains methods for extracting various components of place information.
 */
@Slf4j
@Component
public class PlaceDetailsExtractor {
    
    /**
     * Maximum length for place descriptions
     */
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    
    /**
     * Extract place description from the detail page.
     * 
     * @param detailPage Playwright page object
     * @param defaultDescription Default description to use if extraction fails
     * @return Extracted description
     */
    public String extractDescription(Page detailPage, String defaultDescription) {
        try {
            ElementHandle descElement = detailPage.querySelector(
                    "generic[ref^='s1e199'] paragraph, generic[ref^='s1e200'] paragraph, paragraph:has-text('조선')");
                    
            if (descElement != null) {
                String description = descElement.textContent().trim();
                // Limit long descriptions to the first 500 characters
                if (description.length() > MAX_DESCRIPTION_LENGTH) {
                    description = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                }
                return description;
            }
        } catch (Exception e) {
            log.warn("Failed to extract description", e);
        }
        
        return defaultDescription;
    }
    
    /**
     * Extract address from the detail page.
     * 
     * @param detailPage Playwright page object
     * @return Extracted address or empty string if not found
     */
    public String extractAddress(Page detailPage) {
        try {
            ElementHandle addressElement = detailPage.querySelector("term:has-text('주소') + definition");
            if (addressElement != null) {
                return addressElement.textContent().trim();
            }
        } catch (Exception e) {
            log.warn("Failed to extract address", e);
        }
        
        return "";
    }
    
    /**
     * Extract geographical coordinates from the detail page.
     * 
     * @param detailPage Playwright page object
     * @return Place.Coordinate object with extracted coordinates or null values if not found
     */
    public Place.Coordinate extractCoordinates(Page detailPage) {
        try {
            Double latitude = null;
            Double longitude = null;

            // Find map element
            ElementHandle mapElement = detailPage.querySelector(
                    "generic[ref^='s1e326'], generic[ref^='s1e327']");
                    
            if (mapElement != null) {
                // Try to find coordinates in page content
                String pageContent = detailPage.content();
                
                // Try various patterns to find coordinates
                List<Pattern> patterns = Arrays.asList(
                    Pattern.compile("lat\\s*[=:]\\s*([\\d.]+)\\s*,\\s*lng\\s*[=:]\\s*([\\d.]+)"),
                    Pattern.compile("latitude\\s*[=:]\\s*([\\d.]+)\\s*,?\\s*longitude\\s*[=:]\\s*([\\d.]+)"),
                    Pattern.compile("position\\s*[=:]\\s*\\{\\s*lat\\s*:\\s*([\\d.]+)\\s*,\\s*lng\\s*:\\s*([\\d.]+)"),
                    Pattern.compile("center\\s*[=:]\\s*new\\s+google\\.maps\\.LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"),
                    Pattern.compile("LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)")
                );

                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(pageContent);
                    if (matcher.find()) {
                        try {
                            latitude = Double.parseDouble(matcher.group(1));
                            longitude = Double.parseDouble(matcher.group(2));
                            log.info("Found coordinates: lat={}, lng={}", latitude, longitude);
                            break;
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse coordinates", e);
                        }
                    }
                }
            }
            
            return Place.Coordinate.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to extract coordinates", e);
            return Place.Coordinate.builder().build();
        }
    }
    
    /**
     * Extract place ID from URL.
     * 
     * @param url Place detail page URL
     * @return Extracted ID or null if not found
     */
    public String extractPlaceIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // URL pattern: /attractions/경복궁/KOP000072
        try {
            String[] parts = url.split("/");
            // The last part is the ID (KOP...)
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.startsWith("KOP")) {
                    return lastPart;
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting place ID from URL: {}", url, e);
        }

        return null;
    }
}
