package gdgoc.konkuk.sweetsan.seoulmateserver.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for Google Places API integration. This service enriches place data with information from Google Places API.
 * <p>
 * This service obtains the following information from Google Places API:
 *
 * <ul>
 *     <li>Google Place ID</li>
 *     <li>Location coordinates (latitude, longitude)</li>
 *     <li>Standardized place name</li>
 * </ul>
 * <p>
 * Note that descriptions are NOT available from the Google Places API.
 */
@Slf4j
@Component
public class GooglePlacesService {

    // Default location bias for Seoul
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;

    private static final int SEARCH_RADIUS_METERS = 50000; // 50km radius
    private static final int REQUEST_DELAY_MS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.maps.api.key:}")
    private String googleApiKey;

    /**
     * Enriches places with data from Google Places API.
     * <p>
     * Data obtained from Google Places API: - Google Place ID - Coordinates (latitude/longitude) - Standardized place
     * name
     * <p>
     * Data NOT obtained from Google Places API: - Description (this comes from web scraping only)
     * <p>
     * Note: Google's place name data takes precedence over the initially scraped name as Google's data is considered
     * more accurate and standardized.
     *
     * @param places List of places to enrich with Google data
     * @return List of enriched places
     */
    public List<Place> enrichPlacesWithGoogleData(List<Place> places) {
        if (googleApiKey == null || googleApiKey.isBlank()) {
            log.error("Google Places API key is not configured. Cannot enrich places with Google data.");
            return places;
        }

        log.info("Enriching {} places with data from Google Places API", places.size());
        List<Place> enrichedPlaces = new ArrayList<>();

        int successCount = 0;
        int failCount = 0;
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicInteger noResultsCount = new AtomicInteger(0);
        AtomicInteger errorResponsesCount = new AtomicInteger(0);

        for (Place place : places) {
            try {
                int currentRequest = requestCount.incrementAndGet();

                // Log progress periodically
                if (currentRequest % 10 == 0) {
                    log.info("Processing Google Places API request {}/{} ({}%)",
                            currentRequest, places.size(),
                            Math.round((double) currentRequest / places.size() * 100));
                }

                // Find detailed place information using Text Search API
                Place enrichedPlace = findPlaceDetails(place);

                if (enrichedPlace != null) {
                    enrichedPlaces.add(enrichedPlace);
                    successCount++;
                    log.debug("Successfully enriched place: {}", place.getName());
                } else {
                    // If failed to find, keep the original place
                    enrichedPlaces.add(place);
                    failCount++;
                    log.debug("Failed to find Google data for place: {}", place.getName());
                    noResultsCount.incrementAndGet();
                }

                // Add delay between requests to avoid rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

            } catch (InterruptedException e) {
                log.error("Thread interrupted while enriching places", e);
                Thread.currentThread().interrupt();
                return enrichedPlaces;
            } catch (Exception e) {
                log.error("Error enriching place {}: {}", place.getName(), e.getMessage());
                enrichedPlaces.add(place); // Keep the original place
                failCount++;
                errorResponsesCount.incrementAndGet();
            }
        }

        log.info("Completed Google Places enrichment. Total: {}, Success: {}, Failed: {}",
                places.size(), successCount, failCount);
        log.info("Google Places API Stats: No results: {}, Error responses: {}",
                noResultsCount.get(), errorResponsesCount.get());

        return enrichedPlaces;
    }

    /**
     * Find detailed place information using Google Places API Text Search. Uses the place name from web scraping to
     * search for the place in Google Places API.
     *
     * @param place Place to find details for
     * @return Enriched place or null if not found
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private Place findPlaceDetails(Place place) throws IOException, InterruptedException {
        String name = place.getName();

        // Skip empty names
        if (name == null || name.isBlank()) {
            log.warn("Cannot search for place with empty name");
            return null;
        }

        String query = optimizeSearchQuery(name);
        log.debug("Searching for place '{}' with optimized query: '{}'", name, query);

        // Build Text Search request URL
        String requestUrl = buildTextSearchUrl(query);

        // Execute the request
        HttpResponse<String> response = executeRequest(requestUrl);

        if (response.statusCode() == 200) {
            String responseBody = response.body();

            // Log truncated response for debugging (first 500 chars)
            if (responseBody.length() > 500) {
                log.debug("Received response for '{}': {}...", name, responseBody.substring(0, 500));
            } else {
                log.debug("Received response for '{}': {}", name, responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);

            // Check API status
            if (root.has("status")) {
                String status = root.get("status").asText();
                if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
                    log.warn("Google Places API returned non-OK status for '{}': {}", name, status);

                    // Log error message if present
                    if (root.has("error_message")) {
                        log.error("Error message: {}", root.get("error_message").asText());
                    }

                    return null;
                }
            }

            if (root.has("results") && root.get("results").isArray()) {
                JsonNode results = root.get("results");

                if (results.isEmpty()) {
                    log.debug("No results found for place: '{}'", name);
                    return null;
                }

                // Log number of results
                log.debug("Found {} results for '{}'", results.size(), name);

                JsonNode result = results.get(0);

                // Extract place details and create a new enriched place
                Place enrichedPlace = extractPlaceFromResult(result, place);

                // Log the enriched data
                if (enrichedPlace != null) {
                    log.debug("Enriched data for '{}': GoogleID={}, Coordinates={}",
                            enrichedPlace.getName(),
                            enrichedPlace.getGooglePlaceId(),
                            enrichedPlace.getCoordinate() != null ?
                                    String.format("(%.6f, %.6f)",
                                            enrichedPlace.getCoordinate().getLatitude(),
                                            enrichedPlace.getCoordinate().getLongitude()) : "null"
                    );
                }

                return enrichedPlace;
            } else {
                log.warn("Unexpected response format for '{}': 'results' field missing or not an array", name);
            }
        } else if (response.statusCode() == 429) {
            log.warn("Rate limit exceeded (429) for Google Places API. Waiting longer...");
            Thread.sleep(2000); // Wait longer for rate limits
        } else {
            log.error("Error calling Google Places API: {} for '{}'", response.statusCode(), name);
            log.debug("Response body: {}", response.body());
        }

        return null;
    }

    /**
     * Extract place details from Google Places API response.
     * <p>
     * This method extracts: - Google Place ID - Coordinates (latitude/longitude) - Standardized place name
     * <p>
     * It preserves the description from the original place as this information is NOT available from Google Places
     * API.
     *
     * @param result        JSON node containing place details from Google Places API
     * @param originalPlace Original place from web scraping
     * @return New enriched place combining data from both sources
     */
    private Place extractPlaceFromResult(JsonNode result, Place originalPlace) {
        if (result == null) {
            log.warn("Cannot extract place from null result");
            return null;
        }

        try {
            // Get Google Place ID
            String googlePlaceId = result.has("place_id") ? result.get("place_id").asText() : null;
            if (googlePlaceId == null || googlePlaceId.isEmpty()) {
                log.warn("Missing place_id in API result for: {}", originalPlace.getName());
            }

            // Use Google's name instead of the scraped name
            String name = result.has("name") ? result.get("name").asText() : originalPlace.getName();
            if (name == null || name.isEmpty()) {
                log.warn("Missing name in API result for: {}", originalPlace.getName());
                name = originalPlace.getName(); // Fallback to original name
            }

            // Get coordinates
            Double latitude = null;
            Double longitude = null;

            if (result.has("geometry") && result.get("geometry").has("location")) {
                JsonNode location = result.get("geometry").get("location");
                latitude = location.has("lat") ? location.get("lat").asDouble() : null;
                longitude = location.has("lng") ? location.get("lng").asDouble() : null;

                if (latitude == null || longitude == null) {
                    log.warn("Incomplete coordinates in API result for: {}", originalPlace.getName());
                }
            } else {
                log.warn("Missing geometry.location in API result for: {}", originalPlace.getName());
            }

            // Create coordinate object if we have valid coordinates
            Place.Coordinate coordinate = null;
            if (latitude != null && longitude != null) {
                coordinate = Place.Coordinate.builder()
                        .latitude(latitude)
                        .longitude(longitude)
                        .build();
            }

            // Build the enriched place with combined data:
            // - Use description from original place (from web scraping)
            // - Use Google Place ID and coordinates from Google Places API
            // - Use standardized name from Google Places API
            return Place.builder()
                    .name(name)
                    .description(originalPlace.getDescription())
                    .googlePlaceId(googlePlaceId)
                    .coordinate(coordinate)
                    .build();
        } catch (Exception e) {
            log.error("Error extracting place data from API result: {}", e.getMessage());
            log.debug("Problematic result: {}", result);
            return null;
        }
    }

    /**
     * Optimize search query for Seoul locations. This improves search accuracy by ensuring location context is
     * included.
     *
     * @param name Original place name
     * @return Optimized search query
     */
    private String optimizeSearchQuery(String name) {
        // Handle null or empty names
        if (name == null || name.isBlank()) {
            return "Seoul tourist attractions";
        }

        // Check if the name already contains Seoul or Korea reference
        boolean hasSeoulOrKorea = name.toLowerCase().contains("서울") ||
                name.toLowerCase().contains("seoul") ||
                name.toLowerCase().contains("korea") ||
                name.toLowerCase().contains("한국");

        // Add Seoul to the query if it doesn't have location context
        if (!hasSeoulOrKorea) {
            return name + " 서울";
        }

        return name;
    }

    /**
     * Build Google Places API Text Search request URL with improved parameters.
     *
     * @param query Search query
     * @return Request URL string
     */
    private String buildTextSearchUrl(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // Use circle locationbias with Seoul center and radius
        // Include important fields for our use case
        return String.format(
                "https://maps.googleapis.com/maps/api/place/textsearch/json" +
                        "?query=%s" +
                        "&language=ko" +
                        "&locationbias=circle:%d@%f,%f" +
                        "&region=kr" +
                        "&type=tourist_attraction" +
                        "&key=%s",
                encodedQuery,
                SEARCH_RADIUS_METERS,
                SEOUL_LAT,
                SEOUL_LNG,
                googleApiKey
        );
    }

    /**
     * Execute HTTP request to Google Places API.
     *
     * @param requestUrl Request URL
     * @return HTTP response
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private HttpResponse<String> executeRequest(String requestUrl) throws IOException, InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Error executing HTTP request: {}", e.getMessage());
            throw e; // Re-throw to be handled by caller
        }
    }
}
