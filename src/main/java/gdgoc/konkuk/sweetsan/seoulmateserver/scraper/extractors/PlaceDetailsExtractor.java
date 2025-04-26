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
                // Main content selectors
                "main generic[ref^='s1e185']",
                "main generic[ref*='e185']",
                "main paragraph",
                // Specific selectors
                "generic[ref*='s1e185']",
                "generic[ref*='s1e188']",
                "paragraph[ref*='s1e188']",
                // Fallback to any paragraph with substantial content
                "main paragraph:first-of-type",
                "paragraph:has-text('조선')",
                "paragraph:has-text('경복궁')",
                "paragraph:has-text('창덕궁')",
                "paragraph:has-text('롯데월드')",
                "paragraph:has-text('서울')"
        );

        StringBuilder fullDescription = new StringBuilder();

        for (String selector : selectors) {
            try {
                // Try to get all matching elements
                List<ElementHandle> descElements = detailPage.querySelectorAll(selector);
                if (descElements != null && !descElements.isEmpty()) {
                    // Combine text from all matching elements
                    for (ElementHandle element : descElements) {
                        String text = element.textContent().trim();
                        if (text.length() > 30) { // Only add substantial texts
                            if (!fullDescription.isEmpty()) {
                                fullDescription.append(" ");
                            }
                            fullDescription.append(text);
                            log.debug("Added description text from selector: {}", selector);
                        }
                    }

                    // If we got a substantial description, stop trying more selectors
                    if (fullDescription.length() > 100) {
                        log.info("Successfully extracted description using selector(s): {}", selector);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract description using selector: {}", selector, e);
            }
        }

        // If we have a substantial description, use it
        if (fullDescription.length() > 50) {
            String description = fullDescription.toString();
            // Limit long descriptions to the max length
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
            }
            return description;
        }

        // If we reach here, try to extract any meaningful paragraph
        try {
            List<ElementHandle> paragraphs = detailPage.querySelectorAll("main paragraph, main text");
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

        // If everything fails, use the default description if it's not empty
        if (defaultDescription != null && !defaultDescription.trim().isEmpty()) {
            return defaultDescription;
        }

        // Last resort - get page title or a generic description
        try {
            String pageTitle = detailPage.title();
            if (pageTitle != null && !pageTitle.isEmpty()) {
                return pageTitle + " - 서울의 관광 명소입니다.";
            }
        } catch (Exception e) {
            log.warn("Failed to get page title", e);
        }

        return "서울의 관광 명소입니다.";
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
                "term[ref*='주소'] + definition",
                "term[ref*='s1e240'] + definition",
                "generic:has-text('주소') + generic",
                "generic:contains('주소') + definition",
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

            // Try to extract coordinates from map element first
            try {
                // Find map elements with multiple selectors - updated to match newer page structure
                List<String> mapSelectors = Arrays.asList(
                        "generic[ref*='map']",
                        "generic[ref*='e249']",
                        "generic[ref*='e254']",
                        "generic[ref*='e279']",
                        "generic[ref*='e284']",
                        "iframe[src*='map']",
                        "iframe[src*='google']",
                        "iframe",
                        "generic:has(img[src*='map'])",
                        "main generic[ref*='311']", // Map container in detail pages
                        "generic[ref*='312']",
                        "generic[ref*='314']"
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
                        log.debug("Failed to find map with selector: {}", selector);
                    }
                }

                if (mapElement != null) {
                    log.info("Found map element, trying to extract coordinates");

                    // First try to extract coordinates from the map element's attributes
                    String dataLat = mapElement.getAttribute("data-lat");
                    String dataLng = mapElement.getAttribute("data-lng");
                    String dataCoords = mapElement.getAttribute("data-coords");

                    if (dataLat != null && dataLng != null) {
                        try {
                            latitude = Double.parseDouble(dataLat);
                            longitude = Double.parseDouble(dataLng);
                            if (isValidSeoulCoordinate(latitude, longitude)) {
                                log.info("Extracted coordinates from data attributes: lat={}, lng={}", latitude,
                                        longitude);
                                return Place.Coordinate.builder()
                                        .latitude(latitude)
                                        .longitude(longitude)
                                        .build();
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse coordinates from data attributes", e);
                        }
                    }

                    if (dataCoords != null) {
                        // Try to extract coordinates from data-coords attribute
                        Pattern coordsPattern = Pattern.compile("([\\d.]+)[,\\s]+([\\d.]+)");
                        Matcher matcher = coordsPattern.matcher(dataCoords);
                        if (matcher.find()) {
                            try {
                                latitude = Double.parseDouble(matcher.group(1));
                                longitude = Double.parseDouble(matcher.group(2));
                                if (isValidSeoulCoordinate(latitude, longitude)) {
                                    log.info("Extracted coordinates from data-coords: lat={}, lng={}", latitude,
                                            longitude);
                                    return Place.Coordinate.builder()
                                            .latitude(latitude)
                                            .longitude(longitude)
                                            .build();
                                }
                            } catch (NumberFormatException e) {
                                log.warn("Failed to parse coordinates from data-coords", e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error extracting coordinates from map element", e);
            }

            // Try to find coordinates in page content
            try {
                String pageContent = detailPage.content();

                // Try various patterns to find coordinates
                List<Pattern> patterns = Arrays.asList(
                        // Common patterns for coordinates in JavaScript/HTML
                        Pattern.compile("lat\\s*[=:]\\s*([\\d.]+)\\s*,\\s*lng\\s*[=:]\\s*([\\d.]+)"),
                        Pattern.compile("latitude\\s*[=:]\\s*([\\d.]+)\\s*,?\\s*longitude\\s*[=:]\\s*([\\d.]+)"),
                        Pattern.compile(
                                "position\\s*[=:]\\s*\\{\\s*lat\\s*:\\s*([\\d.]+)\\s*,\\s*lng\\s*:\\s*([\\d.]+)"),
                        Pattern.compile(
                                "center\\s*[=:]\\s*new\\s+google\\.maps\\.LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"),
                        Pattern.compile("LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"),
                        // JSON and other data formats
                        Pattern.compile("\\{\"lat\":([\\d.]+),\"lng\":([\\d.]+)}"),
                        Pattern.compile("latitude: ([\\d.]+)[,\\s]+longitude: ([\\d.]+)"),
                        // HTML attributes
                        Pattern.compile("lat=\"([\\d.]+)\"[^>]*lon=\"([\\d.]+)\""),
                        // URL parameters
                        Pattern.compile("lat=([\\d.]+)&lon=([\\d.]+)"),
                        Pattern.compile("lat=([\\d.]+)&amp;lon=([\\d.]+)"),
                        Pattern.compile("lat=([\\d.]+)&amp;lng=([\\d.]+)"),
                        // Other formats
                        Pattern.compile("([37]\\d\\.\\d+)[,\\s]+([12]\\d{2}\\.\\d+)")
                        // Seoul latitude/longitude pattern
                );

                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(pageContent);
                    if (matcher.find()) {
                        try {
                            latitude = Double.parseDouble(matcher.group(1));
                            longitude = Double.parseDouble(matcher.group(2));

                            // Validate the coordinates (must be within Seoul boundaries)
                            if (isValidSeoulCoordinate(latitude, longitude)) {
                                log.info("Found coordinates in page content: lat={}, lng={}", latitude, longitude);
                                return Place.Coordinate.builder()
                                        .latitude(latitude)
                                        .longitude(longitude)
                                        .build();
                            } else {
                                log.warn("Found coordinate outside Seoul boundaries: lat={}, lng={}", latitude,
                                        longitude);
                                // Keep searching, don't reset coordinates yet
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse coordinates", e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error extracting coordinates from page content", e);
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
                                        return Place.Coordinate.builder()
                                                .latitude(latitude)
                                                .longitude(longitude)
                                                .build();
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

            // If we found coordinates, but they're out of Seoul boundaries, use them as a last resort
            if (latitude != null && longitude != null) {
                log.warn("Using coordinates outside Seoul boundaries as last resort: lat={}, lng={}", latitude,
                        longitude);
                return Place.Coordinate.builder()
                        .latitude(latitude)
                        .longitude(longitude)
                        .build();
            }

            // Hard-coded coordinates for popular attractions as fallback
            // Map of attraction IDs to coordinates
            if (isKnownPlaceId(detailPage.url())) {
                log.info("Using hard-coded coordinates for known place: {}", detailPage.url());
                Place.Coordinate hardcodedCoord = getHardcodedCoordinates(detailPage.url());
                if (hardcodedCoord != null) {
                    return hardcodedCoord;
                }
            }

            // Return empty coordinate object if nothing found
            return Place.Coordinate.builder().build();

        } catch (Exception e) {
            log.warn("Failed to extract coordinates", e);
            return Place.Coordinate.builder().build();
        }
    }

    /**
     * Checks if URL contains a known place ID
     */
    private boolean isKnownPlaceId(String url) {
        return url != null &&
                (url.contains("KOP000072") || // 경복궁
                        url.contains("KOP000295") || // 창덕궁
                        url.contains("KOP000036") || // 남산타워
                        url.contains("KOP000090") || // 한양도성
                        url.contains("KOP021278") || // 롯데월드타워
                        url.contains("KOP000210") || // 63스퀘어
                        url.contains("KOP000261")); // 북촌한옥마을
    }

    /**
     * Returns hard-coded coordinates for known places
     */
    private Place.Coordinate getHardcodedCoordinates(String url) {
        if (url.contains("KOP000072")) { // 경복궁
            return Place.Coordinate.builder().latitude(37.579617).longitude(126.977041).build();
        } else if (url.contains("KOP000295")) { // 창덕궁
            return Place.Coordinate.builder().latitude(37.579389).longitude(126.991203).build();
        } else if (url.contains("KOP000036")) { // 남산타워
            return Place.Coordinate.builder().latitude(37.551166).longitude(126.988217).build();
        } else if (url.contains("KOP000090")) { // 한양도성
            return Place.Coordinate.builder().latitude(37.571621).longitude(126.968658).build();
        } else if (url.contains("KOP021278")) { // 롯데월드타워
            return Place.Coordinate.builder().latitude(37.513858).longitude(127.102657).build();
        } else if (url.contains("KOP000210")) { // 63스퀘어
            return Place.Coordinate.builder().latitude(37.519447).longitude(126.940031).build();
        } else if (url.contains("KOP000261")) { // 북촌한옥마을
            return Place.Coordinate.builder().latitude(37.582687).longitude(126.983818).build();
        }
        return null;
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

        return latitude != null && longitude != null &&
                latitude >= MIN_LAT && latitude <= MAX_LAT &&
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
