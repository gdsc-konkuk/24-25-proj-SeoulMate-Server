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

/**
 * Enhanced service for Google Places API integration. This service focuses on efficient API usage by collecting all
 * necessary data in a single API call.
 * <p>
 * IMPORTANT: Google Places API data always takes precedence over data scraped from other sources. This includes place
 * names, descriptions, and coordinates. Google's data is considered more accurate, standardized, and reliable than data
 * scraped from websites, which may contain inconsistencies.
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
     * Enriches places with data from Google Places API. This method adds Google Place ID, coordinates, and other
     * missing information.
     * <p>
     * IMPORTANT: When enriching places, all information from Google Places API takes precedence over the originally
     * scraped data. This includes place names, which means the final place names in the database may differ from what
     * was initially scraped from the website. Google's data is considered more accurate and standardized.
     *
     * @param places List of places to enrich
     * @return List of enriched places with Google data taking precedence
     */
    public List<Place> enrichPlacesWithGoogleData(List<Place> places) {
        if (googleApiKey.isBlank()) {
            log.warn("Google Places API key is not configured. Cannot enrich places with Google data.");
            return places;
        }

        log.info("Enriching {} places with data from Google Places API", places.size());
        List<Place> enrichedPlaces = new ArrayList<>();

        int successCount = 0;
        int failCount = 0;

        for (Place place : places) {
            try {
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
                }

                // Add delay between requests to avoid rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

                // Log progress periodically
                if ((successCount + failCount) % 10 == 0) {
                    log.info("Google Places API progress: {} places processed, Success: {}, Failed: {}",
                            successCount + failCount, successCount, failCount);
                }

            } catch (InterruptedException e) {
                log.error("Thread interrupted while enriching places", e);
                Thread.currentThread().interrupt();
                return enrichedPlaces;
            } catch (Exception e) {
                log.error("Error enriching place {}: {}", place.getName(), e.getMessage());
                enrichedPlaces.add(place); // Keep the original place
                failCount++;
            }
        }

        log.info("Completed Google Places enrichment. Total: {}, Success: {}, Failed: {}",
                places.size(), successCount, failCount);

        return enrichedPlaces;
    }

    /**
     * Find detailed place information using Google Places API Text Search.
     *
     * @param place Place to find details for
     * @return Enriched place or null if not found
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private Place findPlaceDetails(Place place) throws IOException, InterruptedException {
        String name = place.getName();
        String query = optimizeSearchQuery(name);

        // Build Text Search request URL
        String requestUrl = buildTextSearchUrl(query);

        // Execute the request
        HttpResponse<String> response = executeRequest(requestUrl);

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());

            if (root.has("results") && root.get("results").isArray() && !root.get("results").isEmpty()) {
                JsonNode result = root.get("results").get(0);

                // Extract place details and create a new enriched place
                return extractPlaceFromResult(result, place);
            }
        } else if (response.statusCode() == 429) {
            log.warn("Rate limit exceeded (429) for Google Places API. Waiting longer...");
            Thread.sleep(2000); // Wait longer for rate limits
        } else {
            log.error("Error calling Google Places API: {} for '{}'", response.statusCode(), name);
        }

        return null;
    }

    /**
     * Extract place details from Google Places API response. All information from Google Places API takes precedence
     * over the originally scraped data, including the place name, as Google's data is considered more accurate and
     * standardized.
     *
     * @param result        JSON node containing place details
     * @param originalPlace Original place to copy missing fields from (only used if Google data is missing)
     * @return New enriched place with Google data taking precedence
     */
    private Place extractPlaceFromResult(JsonNode result, Place originalPlace) {
        // Always prioritize Google data when available
        String placeId = result.has("place_id") ? result.get("place_id").asText() : null;

        // Use Google's name instead of the scraped name
        String name = result.has("name") ? result.get("name").asText() : originalPlace.getName();

        // Get coordinates
        Double latitude = null;
        Double longitude = null;

        if (result.has("geometry") && result.get("geometry").has("location")) {
            JsonNode location = result.get("geometry").get("location");
            latitude = location.has("lat") ? location.get("lat").asDouble() : null;
            longitude = location.has("lng") ? location.get("lng").asDouble() : null;
        }

        // Create coordinate object if we have valid coordinates
        Place.Coordinate coordinate = null;
        if (latitude != null && longitude != null) {
            coordinate = Place.Coordinate.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
        }

        // For description, first try Google data, then fall back to original description
        String description = null;
        if (result.has("editorial_summary") && result.get("editorial_summary").has("overview")) {
            description = result.get("editorial_summary").get("overview").asText();
        } else if (originalPlace.getDescription() != null && !originalPlace.getDescription().isEmpty()) {
            description = originalPlace.getDescription();
        }

        // Build the enriched place with Google data taking precedence
        return Place.builder()
                .name(name)
                .description(description)
                .googlePlaceId(placeId)
                .coordinate(coordinate)
                .build();
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
     * Build Google Places API Text Search request URL.
     *
     * @param query Search query
     * @return Request URL string
     */
    private String buildTextSearchUrl(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // Use circle locationbias with Seoul center and radius
        return String.format(
                "https://maps.googleapis.com/maps/api/place/textsearch/json" +
                        "?query=%s" +
                        "&locationbias=circle:%d@%f,%f" +
                        "&language=ko" +
                        "&fields=place_id,name,formatted_address,geometry,editorial_summary" +
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
