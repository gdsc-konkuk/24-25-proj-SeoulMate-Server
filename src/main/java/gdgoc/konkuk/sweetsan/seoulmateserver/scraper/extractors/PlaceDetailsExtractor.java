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
 * Utility class for extracting place details from web pages. Contains methods for extracting various components of
 * place information.
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
     * @param detailPage         Playwright page object
     * @param defaultDescription Default description to use if extraction fails
     * @return Extracted description
     */
    public String extractDescription(Page detailPage, String defaultDescription) {
        // Multiple selectors to try for description extraction, to handle website structure changes
        List<String> selectors = Arrays.asList(
                // Original selectors
                "generic[ref^='s1e199'] paragraph",
                "generic[ref^='s1e200'] paragraph",
                "paragraph:has-text('조선')",
                // Additional more general selectors
                "main generic[ref*='e200']",
                "main paragraph:first-of-type",
                "main paragraph:nth-of-type(1)",
                "main paragraph:has-text('경복궁')",
                "generic[ref*='description']",
                "paragraph:has-text('궁궐')"
        );

        for (String selector : selectors) {
            try {
                ElementHandle descElement = detailPage.querySelector(selector);
                if (descElement != null) {
                    String description = descElement.textContent().trim();
                    // Validate if the text is actually a description (minimum length)
                    if (description.length() > 50) {
                        // Limit long descriptions to the first 500 characters
                        if (description.length() > MAX_DESCRIPTION_LENGTH) {
                            description = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                        }
                        log.info("Successfully extracted description using selector: {}", selector);
                        return description;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract description using selector: {}", selector, e);
            }
        }

        // If we reach here, try to extract any meaningful paragraph
        try {
            List<ElementHandle> paragraphs = detailPage.querySelectorAll("main paragraph");
            for (ElementHandle paragraph : paragraphs) {
                String text = paragraph.textContent().trim();
                if (text.length() > 100) { // Longer text might be an actual description
                    if (text.length() > MAX_DESCRIPTION_LENGTH) {
                        text = text.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                    }
                    log.info("Extracted description from generic paragraph");
                    return text;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract description from general paragraphs", e);
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
        // Multiple selectors to try for address extraction, to handle website structure changes
        List<String> selectors = Arrays.asList(
                "term:has-text('주소') + definition",
                "generic:has(term:has-text('주소')) definition",
                "generic:has-text('주소') + generic",
                "term:contains('주소') + definition",
                "generic:has-text('주소')",
                "term:has-text('Address') + definition"
        );

        for (String selector : selectors) {
            try {
                ElementHandle addressElement = detailPage.querySelector(selector);
                if (addressElement != null) {
                    String address = addressElement.textContent().trim();
                    if (address.contains("서울")) {
                        log.info("Successfully extracted address using selector: {}", selector);
                        return address;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract address using selector: {}", selector, e);
            }
        }

        // Try to find a more generic way if the above fails
        try {
            // Look for any definition-like text that contains Seoul address pattern
            List<ElementHandle> definitions = detailPage.querySelectorAll("definition, generic:has-text('서울')");
            for (ElementHandle definition : definitions) {
                String text = definition.textContent().trim();
                if (text.contains("서울") && text.contains("구") && text.length() < 100) {
                    log.info("Extracted address from generic element");
                    return text;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract address from general elements", e);
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

            // Find map elements with multiple selectors
            List<String> mapSelectors = Arrays.asList(
                    "generic[ref^='s1e326'], generic[ref^='s1e327']",
                    "generic[ref*='map']",
                    "generic[ref*='e327']",
                    "iframe[src*='map']",
                    "generic:has(img[src*='map'])"
            );

            ElementHandle mapElement = null;
            for (String selector : mapSelectors) {
                try {
                    mapElement = detailPage.querySelector(selector);
                    if (mapElement != null) {
                        log.info("Found map element using selector: {}", selector);
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Failed to find map with selector: {}", selector);
                }
            }

            if (mapElement != null) {
                // Try to find coordinates in page content
                String pageContent = detailPage.content();

                // Try various patterns to find coordinates
                List<Pattern> patterns = Arrays.asList(
                        // Original patterns
                        Pattern.compile("lat\\s*[=:]\\s*([\\d.]+)\\s*,\\s*lng\\s*[=:]\\s*([\\d.]+)"),
                        Pattern.compile("latitude\\s*[=:]\\s*([\\d.]+)\\s*,?\\s*longitude\\s*[=:]\\s*([\\d.]+)"),
                        Pattern.compile(
                                "position\\s*[=:]\\s*\\{\\s*lat\\s*:\\s*([\\d.]+)\\s*,\\s*lng\\s*:\\s*([\\d.]+)"),
                        Pattern.compile(
                                "center\\s*[=:]\\s*new\\s+google\\.maps\\.LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"),
                        Pattern.compile("LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"),
                        // Additional patterns
                        Pattern.compile("\\{\"lat\":([\\d.]+),\"lng\":([\\d.]+)}"),
                        Pattern.compile("latitude: ([\\d.]+)[,\\s]+longitude: ([\\d.]+)"),
                        Pattern.compile("lat=\"([\\d.]+)\"[^>]*lon=\"([\\d.]+)\""),
                        Pattern.compile("lat=([\\d.]+)&lon=([\\d.]+)"),
                        Pattern.compile("lat=([\\d.]+)&amp;lon=([\\d.]+)"),
                        Pattern.compile("lat=([\\d.]+)&amp;lng=([\\d.]+)")
                );

                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(pageContent);
                    if (matcher.find()) {
                        try {
                            latitude = Double.parseDouble(matcher.group(1));
                            longitude = Double.parseDouble(matcher.group(2));

                            // Validate the coordinates (must be within Seoul boundaries)
                            if (isValidSeoulCoordinate(latitude, longitude)) {
                                log.info("Found coordinates: lat={}, lng={}", latitude, longitude);
                                break;
                            } else {
                                log.warn("Found coordinate outside Seoul boundaries: lat={}, lng={}", latitude,
                                        longitude);
                                latitude = null;
                                longitude = null;
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse coordinates", e);
                        }
                    }
                }
            }

            // If coordinates still not found, try to find in iframes
            if (latitude == null || longitude == null) {
                try {
                    List<ElementHandle> iframes = detailPage.querySelectorAll(
                            "iframe[src*='map'], iframe[src*='google']");
                    for (ElementHandle iframe : iframes) {
                        String src = iframe.getAttribute("src");
                        if (src != null && !src.isEmpty()) {
                            // Extract coordinates from Google Maps iframe
                            Pattern googleMapPattern = Pattern.compile("q=([\\d.]+),([\\d.]+)|ll=([\\d.]+),([\\d.]+)");
                            Matcher matcher = googleMapPattern.matcher(src);
                            if (matcher.find()) {
                                try {
                                    String lat = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
                                    String lng = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
                                    latitude = Double.parseDouble(lat);
                                    longitude = Double.parseDouble(lng);
                                    if (isValidSeoulCoordinate(latitude, longitude)) {
                                        log.info("Found coordinates in iframe: lat={}, lng={}", latitude, longitude);
                                        break;
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("Failed to parse iframe coordinates", e);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract coordinates from iframes", e);
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
     * Validates if coordinates are within Seoul boundaries
     *
     * @param latitude  Latitude to check
     * @param longitude Longitude to check
     * @return true if coordinates are valid for Seoul
     */
    private boolean isValidSeoulCoordinate(Double latitude, Double longitude) {
        // Seoul approximate boundaries
        final double MIN_LAT = 37.0;
        final double MAX_LAT = 38.0;
        final double MIN_LNG = 126.5;
        final double MAX_LNG = 127.5;

        return latitude >= MIN_LAT && latitude <= MAX_LAT &&
                longitude >= MIN_LNG && longitude <= MAX_LNG;
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

        // Try different patterns to find the ID
        try {
            // Primary pattern: /attractions/경복궁/KOP000072
            String[] parts = url.split("/");
            // The last part is the ID (KOP...)
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.startsWith("KOP")) {
                    log.debug("Extracted place ID from URL using path splitting: {}", lastPart);
                    return lastPart;
                }
            }

            // Secondary approach: Use regex pattern matching for more flexibility
            Pattern kopPattern = Pattern.compile("KOP\\w+");
            Matcher matcher = kopPattern.matcher(url);
            if (matcher.find()) {
                String kopId = matcher.group();
                log.debug("Extracted place ID from URL using regex: {}", kopId);
                return kopId;
            }

            // If URL has query parameters, check them too
            if (url.contains("?")) {
                String queryParams = url.substring(url.indexOf("?") + 1);
                String[] params = queryParams.split("&");
                for (String param : params) {
                    if (param.startsWith("id=")) {
                        String id = param.substring(3);
                        log.debug("Extracted place ID from URL query parameter: {}", id);
                        return id;
                    }
                }
            }

            // Fallback: If no KOP ID found, return the entire URL path for uniqueness
            log.warn("No KOP ID found in URL: {}, using URL hash as fallback", url);
            return "URL_" + Math.abs(url.hashCode());

        } catch (Exception e) {
            log.warn("Error extracting place ID from URL: {}", url, e);
            // Fallback in case of error
            return "URL_" + Math.abs(url.hashCode());
        }
    }
}
