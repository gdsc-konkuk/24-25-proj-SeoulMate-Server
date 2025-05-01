package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceEnrichmentData;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceSourceData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
     * Retrieves enrichment data for places from Google Places API.
     *
     * @param sourceDataList List of basic place data to be enriched
     * @return List of enrichment data for each place
     */
    public List<PlaceEnrichmentData> findEnrichmentData(List<PlaceSourceData> sourceDataList) {
        if (googleApiKey == null || googleApiKey.isBlank()) {
            log.error("Google Places API key is not configured. Cannot retrieve enrichment data.");
            return new ArrayList<>();
        }

        log.info("Retrieving enrichment data for {} places from Google Places API", sourceDataList.size());
        List<PlaceEnrichmentData> enrichmentDataList = new ArrayList<>();

        int successCount = 0;
        int failCount = 0;
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicInteger noResultsCount = new AtomicInteger(0);
        AtomicInteger errorResponsesCount = new AtomicInteger(0);

        for (PlaceSourceData sourceData : sourceDataList) {
            try {
                int currentRequest = requestCount.incrementAndGet();

                // Log progress periodically
                if (currentRequest % 10 == 0) {
                    log.info("Processing Google Places API request {}/{} ({}%)",
                            currentRequest, sourceDataList.size(),
                            Math.round((double) currentRequest / sourceDataList.size() * 100));
                }

                // Find enrichment data using Text Search API
                PlaceEnrichmentData enrichmentData = findPlaceDetails(sourceData);

                if (enrichmentData != null) {
                    enrichmentDataList.add(enrichmentData);
                    successCount++;
                    log.debug("Successfully retrieved enrichment data for place: {}", sourceData.getName());
                } else {
                    // Add a placeholder enrichment data with name only
                    enrichmentDataList.add(PlaceEnrichmentData.builder()
                            .standardizedName(sourceData.getName())
                            .build());
                    failCount++;
                    log.debug("Failed to find enrichment data for place: {}", sourceData.getName());
                    noResultsCount.incrementAndGet();
                }

                // Add delay between requests to avoid rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

            } catch (InterruptedException e) {
                log.error("Thread interrupted while retrieving place data", e);
                Thread.currentThread().interrupt();
                return enrichmentDataList;
            } catch (Exception e) {
                log.error("Error retrieving Google data for place {}: {}", sourceData.getName(), e.getMessage());
                // Add a placeholder with name only
                enrichmentDataList.add(PlaceEnrichmentData.builder()
                        .standardizedName(sourceData.getName())
                        .build());
                failCount++;
                errorResponsesCount.incrementAndGet();
            }
        }

        log.info("Completed Google Places data retrieval. Total: {}, Success: {}, Failed: {}",
                sourceDataList.size(), successCount, failCount);
        log.info("Google Places API Stats: No results: {}, Error responses: {}",
                noResultsCount.get(), errorResponsesCount.get());

        return enrichmentDataList;
    }

    /**
     * Asynchronously retrieves enrichment data for places from Google Places API.
     *
     * @param sourceDataList List of basic place data to be enriched
     * @return CompletableFuture with a list of enrichment data for each place
     */
    public CompletableFuture<List<PlaceEnrichmentData>> findEnrichmentDataAsync(List<PlaceSourceData> sourceDataList) {
        return CompletableFuture.supplyAsync(() -> findEnrichmentData(sourceDataList));
    }

    /**
     * Find detailed place information using Google Places API Text Search.
     *
     * @param sourceData Source data to find details for
     * @return Enrichment data or null if not found
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private PlaceEnrichmentData findPlaceDetails(PlaceSourceData sourceData) throws IOException, InterruptedException {
        String name = sourceData.getName();

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

                // Extract place details
                PlaceEnrichmentData enrichmentData = extractEnrichmentDataFromResult(result);

                // Log the enrichment data
                if (enrichmentData != null) {
                    log.debug("Enrichment data for '{}': GoogleID={}, Coordinates=({}, {})",
                            enrichmentData.getStandardizedName(),
                            enrichmentData.getExternalId(),
                            enrichmentData.getLatitude(),
                            enrichmentData.getLongitude());
                }

                return enrichmentData;
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
     * Extract enrichment data from Google Places API response.
     *
     * @param result JSON node containing place details from Google Places API
     * @return Enrichment data object
     */
    private PlaceEnrichmentData extractEnrichmentDataFromResult(JsonNode result) {
        if (result == null) {
            log.warn("Cannot extract enrichment data from null result");
            return null;
        }

        try {
            // Get Google Place ID
            String googlePlaceId = result.has("place_id") ? result.get("place_id").asText() : null;
            if (googlePlaceId == null || googlePlaceId.isEmpty()) {
                log.warn("Missing place_id in API result");
            }

            // Get standardized name
            String standardizedName = result.has("name") ? result.get("name").asText() : null;
            if (standardizedName == null || standardizedName.isEmpty()) {
                log.warn("Missing name in API result");
                return null; // Name is essential for identification
            }

            // Get coordinates
            Double latitude = null;
            Double longitude = null;

            if (result.has("geometry") && result.get("geometry").has("location")) {
                JsonNode location = result.get("geometry").get("location");
                latitude = location.has("lat") ? location.get("lat").asDouble() : null;
                longitude = location.has("lng") ? location.get("lng").asDouble() : null;

                if (latitude == null || longitude == null) {
                    log.warn("Incomplete coordinates in API result");
                }
            } else {
                log.warn("Missing geometry.location in API result");
            }

            // Build the enrichment data
            return PlaceEnrichmentData.builder()
                    .standardizedName(standardizedName)
                    .externalId(googlePlaceId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();

        } catch (Exception e) {
            log.error("Error extracting enrichment data from API result: {}", e.getMessage());
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
