package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for Google Places API operations.
 * Provides functionality to find Google Place IDs for tourist places.
 */
@Slf4j
@Component
public class GooglePlaceUtil {
    
    // Default location bias for Seoul (used when coordinate is not available)
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;
    private static final int SEARCH_RADIUS_METERS = 50000; // 50km radius
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.maps.api.key:}")
    private String googleApiKey;

    /**
     * Find Google Place ID for a place using its name and coordinates.
     *
     * @param name  Place name
     * @param place Place object containing coordinates
     * @return Google Place ID or null if not found
     */
    public String findGooglePlaceId(String name, Place place) {
        // Check if API key is configured
        if (isApiKeyMissing()) {
            log.warn("Google API key is not configured. Skipping Google Place ID lookup.");
            return null;
        }

        try {
            // Optimize search query for Seoul location
            String searchQuery = optimizeSearchQuery(name);
            
            // Build request URL based on available data
            String requestUrl = buildRequestUrl(searchQuery, place);
            
            // Execute the request
            HttpResponse<String> response = executeRequest(requestUrl);
            
            // Process the response
            if (response.statusCode() == 200) {
                return extractPlaceIdFromResponse(response.body(), name);
            } else {
                log.error("Error calling Google Places API: {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error looking up Google Place ID for {}", name, e);
            Thread.currentThread().interrupt();
        }

        return null;
    }
    
    /**
     * Check if the API key is missing or invalid.
     * 
     * @return true if the API key is missing
     */
    private boolean isApiKeyMissing() {
        return googleApiKey == null || 
               googleApiKey.isEmpty() || 
               googleApiKey.equals("${google.maps.api.key}");
    }
    
    /**
     * Optimize search query for Seoul locations.
     * 
     * @param name Original place name
     * @return Optimized search query
     */
    private String optimizeSearchQuery(String name) {
        if (!name.contains("서울") && !name.contains("Seoul")) {
            return name + " 서울";
        }
        return name;
    }
    
    /**
     * Build Google Places API request URL.
     * 
     * @param searchQuery Optimized search query
     * @param place Place object with potential coordinates
     * @return Request URL string
     */
    private String buildRequestUrl(String searchQuery, Place place) {
        String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
        
        // Determine if we can use place coordinates
        boolean hasCoordinates = place.getCoordinate() != null && 
                                 place.getCoordinate().getLatitude() != null && 
                                 place.getCoordinate().getLongitude() != null;
        
        if (hasCoordinates) {
            // Use point locationbias with place coordinates
            return String.format(
                "https://maps.googleapis.com/maps/api/place/findplacefromtext/json" +
                "?input=%s" +
                "&inputtype=textquery" +
                "&locationbias=point:%f,%f" +
                "&fields=place_id,name,formatted_address" +
                "&language=ko" +
                "&key=%s",
                encodedQuery,
                place.getCoordinate().getLatitude(),
                place.getCoordinate().getLongitude(),
                googleApiKey
            );
        } else {
            // Use circle locationbias with Seoul center and 50km radius
            return String.format(
                "https://maps.googleapis.com/maps/api/place/findplacefromtext/json" +
                "?input=%s" +
                "&inputtype=textquery" +
                "&locationbias=circle:%d@%f,%f" +
                "&fields=place_id,name,formatted_address" +
                "&language=ko" +
                "&key=%s",
                encodedQuery,
                SEARCH_RADIUS_METERS,
                SEOUL_LAT,
                SEOUL_LNG,
                googleApiKey
            );
        }
    }
    
    /**
     * Execute HTTP request to Google Places API.
     * 
     * @param requestUrl Request URL
     * @return HTTP response
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private HttpResponse<String> executeRequest(String requestUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();
                
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * Extract place ID and validate result from API response.
     * 
     * @param responseBody Response body JSON
     * @param originalName Original place name for validation
     * @return Place ID or null if not found
     * @throws IOException if JSON parsing fails
     */
    private String extractPlaceIdFromResponse(String responseBody, String originalName) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        
        if (root.has("candidates") && root.get("candidates").isArray() && !root.get("candidates").isEmpty()) {
            JsonNode candidate = root.get("candidates").get(0);
            String placeId = candidate.get("place_id").asText();
            
            // Validate the result by comparing names
            if (candidate.has("name")) {
                String returnedName = candidate.get("name").asText();
                boolean nameMatch = returnedName.contains(originalName) || originalName.contains(returnedName);
                
                if (!nameMatch) {
                    log.warn("Returned place name '{}' doesn't match query '{}'", returnedName, originalName);
                    // Still return the result, application can verify
                }
            }
            
            log.info("Found Google Place ID for {}: {}", originalName, placeId);
            return placeId;
        } else {
            log.warn("No candidates found for {}", originalName);
            return null;
        }
    }
}
