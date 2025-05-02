package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceEnrichmentData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Repository for retrieving place enrichment data from Google Places API. This repository is responsible for
 * complementing basic place information with geographical coordinates, standardized names, and Google-specific
 * identifiers.
 *
 * @see PlaceEnrichmentData
 */
@Slf4j
@Repository
public class PlacesEnrichmentRepository {

    private static final String PLACES_API_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String FIELD_MASK = "places.displayName,places.formattedAddress,places.id,places.location";
    private static final String DEFAULT_LANGUAGE = "ko";
    private static final String DEFAULT_REGION = "kr";

    // Default location bias for Seoul - used to improve search relevance
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;
    private static final int SEARCH_RADIUS_METERS = 50000; // 50km radius

    // Rate limiting and processing parameters
    private static final int REQUEST_DELAY_MS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.maps.api.key:}")
    private String googleApiKey;

    /**
     * Asynchronously retrieves enrichment data for places from Google Places API based on place names.
     *
     * @param placeNames List of place names to be enriched
     * @return CompletableFuture with a list of enrichment data for each place
     */
    public CompletableFuture<List<PlaceEnrichmentData>> findAll(List<String> placeNames) {
        return CompletableFuture.supplyAsync(() -> {
            if (googleApiKey == null || googleApiKey.isBlank()) {
                log.error("Google Places API key is not configured. Cannot retrieve enrichment data.");
                return new ArrayList<>();
            }

            log.info("Retrieving enrichment data for {} places from Google Places API", placeNames.size());
            List<PlaceEnrichmentData> enrichmentDataList = new ArrayList<>();

            int successCount = 0;
            int failCount = 0;
            AtomicInteger requestCount = new AtomicInteger(0);
            AtomicInteger noResultsCount = new AtomicInteger(0);
            AtomicInteger errorResponsesCount = new AtomicInteger(0);

            for (String placeName : placeNames) {
                try {
                    int currentRequest = requestCount.incrementAndGet();

                    // Find enrichment data using Text Search API
                    PlaceEnrichmentData enrichmentData = findPlaceByName(placeName);

                    if (enrichmentData != null) {
                        enrichmentDataList.add(enrichmentData);
                        successCount++;
                        log.info("[{}/{}] '{}' → name='{}', id='{}', location=({}, {})",
                                currentRequest, placeNames.size(), placeName,
                                enrichmentData.getStandardizedName(),
                                enrichmentData.getExternalId(),
                                enrichmentData.getLatitude(),
                                enrichmentData.getLongitude());
                    } else {
                        // Add a placeholder enrichment data with name only
                        enrichmentDataList.add(PlaceEnrichmentData.builder()
                                .standardizedName(placeName)
                                .build());
                        failCount++;
                        log.warn("[{}/{}] '{}' → No results found",
                                currentRequest, placeNames.size(), placeName);
                        noResultsCount.incrementAndGet();
                    }

                    // Log summary every 10 requests
                    if (currentRequest % 10 == 0) {
                        log.info("====== Progress: {}/{} ({}%) - Success: {}, Failed: {} ======",
                                currentRequest, placeNames.size(),
                                Math.round((double) currentRequest / placeNames.size() * 100),
                                successCount, failCount);
                    }

                    // Add delay between requests to avoid rate limiting
                    Thread.sleep(REQUEST_DELAY_MS);

                } catch (InterruptedException e) {
                    log.error("Thread interrupted while retrieving place data", e);
                    Thread.currentThread().interrupt();
                    return enrichmentDataList;
                } catch (Exception e) {
                    log.error("[{}/{}] '{}' → Error: {}", 
                            requestCount.get(), placeNames.size(), placeName, e.getMessage());
                    // Add a placeholder with name only
                    enrichmentDataList.add(PlaceEnrichmentData.builder()
                            .standardizedName(placeName)
                            .build());
                    failCount++;
                    errorResponsesCount.incrementAndGet();
                }
            }

            log.info("Completed Google Places data retrieval. Total: {}, Success: {}, Failed: {}",
                    placeNames.size(), successCount, failCount);
            log.info("Google Places API Stats: No results: {}, Error responses: {}",
                    noResultsCount.get(), errorResponsesCount.get());

            return enrichmentDataList;
        });
    }

    /**
     * Find place details using Google Places API Text Search v1. Uses the new Places API to search for a place by
     * name.
     *
     * @param placeName Place name to find details for
     * @return Enrichment data or null if not found
     */
    private PlaceEnrichmentData findPlaceByName(String placeName) {
        // Skip empty names
        if (placeName == null || placeName.isBlank()) {
            log.warn("Cannot search for place with empty name");
            return null;
        }

        try {
            // Optimize query by adding location context if needed
            String query = optimizeSearchQuery(placeName);

            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("textQuery", query);
            requestBody.put("languageCode", DEFAULT_LANGUAGE);
            requestBody.put("regionCode", DEFAULT_REGION);

            // Add location bias for Seoul to improve search relevance
            Map<String, Object> locationBias = new HashMap<>();
            Map<String, Object> circle = new HashMap<>();
            Map<String, Object> center = new HashMap<>();
            center.put("latitude", SEOUL_LAT);
            center.put("longitude", SEOUL_LNG);
            circle.put("center", center);
            circle.put("radius", (double) SEARCH_RADIUS_METERS);
            locationBias.put("circle", circle);
            requestBody.put("locationBias", locationBias);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PLACES_API_URL))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Goog-Api-Key", googleApiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            // Execute the request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Process response
            if (response.statusCode() == 200) {
                String responseBody = response.body();

                // Parse JSON
                JsonNode root = objectMapper.readTree(responseBody);

                // Check if we have places in response
                if (root.has("places") && root.get("places").isArray() && !root.get("places").isEmpty()) {
                    JsonNode place = root.get("places").get(0); // Get first result
                    return extractPlaceData(place);
                } else {
                    log.info("No results found in API response for place: '{}'", placeName);
                    return null;
                }
            } else if (response.statusCode() == 429) {
                log.warn("Rate limit exceeded (429) for Google Places API. Waiting longer...");
                Thread.sleep(2000); // Wait longer for rate limits
                return null;
            } else {
                log.error("Google Places API error for '{}': status={}, response: {}",
                        placeName, response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("Exception searching for place '{}': {}", placeName, e.getMessage());
            return null;
        }
    }

    /**
     * Extract place data from the Google Places API response.
     *
     * @param place JsonNode containing place details from Google Places API
     * @return PlaceEnrichmentData with extracted information
     */
    private PlaceEnrichmentData extractPlaceData(JsonNode place) {
        try {
            // Extract Google Place ID (format: "places/ChIJ...")
            String placeId = null;
            if (place.has("id")) {
                String fullId = place.get("id").asText();
                // Extract ID part after "places/" if present
                placeId = fullId.startsWith("places/") ? fullId.substring(7) : fullId;
            }

            // Extract display name
            String standardizedName = null;
            if (place.has("displayName") && place.get("displayName").has("text")) {
                standardizedName = place.get("displayName").get("text").asText();
            }

            // If no standard name found, return null
            if (standardizedName == null || standardizedName.isEmpty()) {
                log.warn("Missing display name in API result");
                return null;
            }

            // Extract coordinates
            Double latitude = null;
            Double longitude = null;
            if (place.has("location")) {
                JsonNode location = place.get("location");
                latitude = location.has("latitude") ? location.get("latitude").asDouble() : null;
                longitude = location.has("longitude") ? location.get("longitude").asDouble() : null;
            }

            // Build the enrichment data
            return PlaceEnrichmentData.builder()
                    .standardizedName(standardizedName)
                    .externalId(placeId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();

        } catch (Exception e) {
            log.error("Error extracting place data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Optimize search query for Seoul tourist locations. This improves search accuracy by ensuring location context is
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
}
