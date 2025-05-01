package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceSourceData;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlacesEnrichmentRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceSourceDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for collecting and managing Seoul tourist places.
 * <p>
 * This service orchestrates the following process:
 *
 * <ol>
 *    <li>Retrieve basic place data (name, description) from a primary source</li>
 *    <li>Enrich the basic data with location details from Google Places API</li>
 *    <li>Aggregate the information into complete Place objects</li>
 *    <li>Save the results to the database</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final PlaceRepository placeRepository;
    private final PlaceSourceDataRepository placeSourceDataRepository;
    private final PlacesEnrichmentRepository placesEnrichmentRepository;
    private final PlaceAggregator placeAggregator;

    /**
     * Runs the data collection and saving process asynchronously. This method uses a complete asynchronous chain for
     * the entire process.
     *
     * @return A CompletableFuture that resolves to the number of places collected and saved
     */
    @Async
    public CompletableFuture<Integer> scrapeAndSave() {
        log.info("Starting asynchronous data collection process");

        return placeSourceDataRepository.findAllAsync()
                .thenCompose(sourceDataList -> {
                    log.info("Primary data retrieval completed. Found {} source records", sourceDataList.size());

                    // Extract place names for enrichment API
                    List<String> placeNames = sourceDataList.stream()
                            .map(PlaceSourceData::getName)
                            .collect(Collectors.toList());

                    // Continue the asynchronous chain with enrichment data
                    return placesEnrichmentRepository.findAll(placeNames)
                            .thenApply(enrichmentDataList -> {
                                log.info("Enrichment data retrieval completed. Found {} enrichment records",
                                        enrichmentDataList.size());

                                // Aggregate data
                                List<Place> places = placeAggregator.aggregatePlaceData(sourceDataList,
                                        enrichmentDataList);
                                log.info("Data aggregation completed. Created {} complete place records",
                                        places.size());

                                // Log statistics on the aggregated data
                                logDataStatistics(places);

                                // Save to database
                                int placesCount = saveScrapedPlaces(places);
                                log.info("Saving process completed. Total places saved: {}", placesCount);
                                return placesCount;
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error during data collection process", ex);
                    throw new RuntimeException("Error during data collection process", ex);
                });
    }

    /**
     * Log statistics about the place data to help diagnose issues
     */
    private void logDataStatistics(List<Place> places) {
        if (places == null || places.isEmpty()) {
            log.warn("No places to analyze for statistics");
            return;
        }

        // Calculate completeness statistics
        int totalPlaces = places.size();
        int withName = 0;
        int withGooglePlaceId = 0;
        int withCoordinates = 0;
        int withDescription = 0;
        int withAllData = 0;

        for (Place place : places) {
            boolean hasName = place.hasValidName();
            boolean hasGoogleId = place.hasValidGooglePlaceId();
            boolean hasCoords = place.hasValidCoordinates();
            boolean hasDesc = place.hasValidDescription();

            // Count fields
            if (hasName) {
                withName++;
            }
            if (hasGoogleId) {
                withGooglePlaceId++;
            }
            if (hasCoords) {
                withCoordinates++;
            }
            if (hasDesc) {
                withDescription++;
            }

            // Count complete records
            if (hasName && hasGoogleId && hasCoords && hasDesc) {
                withAllData++;
            }
        }

        // Log statistics
        log.info("===== Data Completeness Statistics =====");
        log.info("Total Places: {}", totalPlaces);
        log.info("With Name: {} ({}%)", withName, calculatePercentage(withName, totalPlaces));
        log.info("With Google Place ID: {} ({}%)", withGooglePlaceId,
                calculatePercentage(withGooglePlaceId, totalPlaces));
        log.info("With Coordinates: {} ({}%)", withCoordinates, calculatePercentage(withCoordinates, totalPlaces));
        log.info("With Description: {} ({}%)", withDescription, calculatePercentage(withDescription, totalPlaces));
        log.info("With ALL Required Data: {} ({}%)", withAllData, calculatePercentage(withAllData, totalPlaces));
        log.info("========================================");
    }

    /**
     * Calculate percentage
     */
    private double calculatePercentage(int part, int total) {
        return total > 0 ? Math.round((double) part / total * 100.0) : 0.0;
    }

    /**
     * Saves places to the database, updating existing ones if needed. Only saves places that have all required fields.
     *
     * @param places List of places to save
     * @return Number of new places saved
     */
    private int saveScrapedPlaces(List<Place> places) {
        log.info("Saving places to database");
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int invalidCount = 0;

        for (Place place : places) {
            try {
                // Skip invalid places
                if (!place.isValid()) {
                    invalidCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping invalid place: {} (missing: {}{}{}{})",
                                place.getName(),
                                !place.hasValidName() ? "name " : "",
                                !place.hasValidGooglePlaceId() ? "googleId " : "",
                                !place.hasValidCoordinates() ? "coordinates " : "",
                                !place.hasValidDescription() ? "description" : "");
                    }
                    continue;
                }

                // First try to find by Google Place ID
                List<Place> existingPlaces = null;
                if (place.hasValidGooglePlaceId()) {
                    existingPlaces = placeRepository.findByGooglePlaceId(place.getGooglePlaceId());
                }

                // If not found, try by name
                if ((existingPlaces == null || existingPlaces.isEmpty()) && place.hasValidName()) {
                    existingPlaces = placeRepository.findByName(place.getName());
                }

                // Determine if we need to save as new or update existing
                if (existingPlaces == null || existingPlaces.isEmpty()) {
                    // Save as new
                    placeRepository.save(place);
                    newCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("Saved new place: {}", place.getName());
                    }
                } else {
                    // Update only if description is better
                    Place existingPlace = existingPlaces.get(0);
                    boolean updated = updateExistingPlace(existingPlace, place);

                    if (updated) {
                        placeRepository.save(existingPlace);
                        updatedCount++;
                        if (log.isDebugEnabled()) {
                            log.debug("Updated place: {}", existingPlace.getName());
                        }
                    } else {
                        skippedCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Error saving place '{}': {}", place.getName(), e.getMessage());
                skippedCount++;
            }
        }

        log.info("Places saved: {} new, {} updated, {} skipped, {} invalid",
                newCount, updatedCount, skippedCount, invalidCount);
        return newCount;
    }

    /**
     * Update an existing place with better data if available
     *
     * @param existing Existing place from database
     * @param newPlace New place with potential updates
     * @return true if any updates were made
     */
    private boolean updateExistingPlace(Place existing, Place newPlace) {
        boolean updated = false;

        // Update description if missing or if new one is better (longer and meaningful)
        if (!existing.hasValidDescription() && newPlace.hasValidDescription()) {
            existing.setDescription(newPlace.getDescription());
            updated = true;
        } else if (existing.hasValidDescription() && newPlace.hasValidDescription() &&
                newPlace.getDescription().length() > existing.getDescription().length() &&
                newPlace.getDescription().length() > 50) {
            existing.setDescription(newPlace.getDescription());
            updated = true;
        }

        return updated;
    }

    /**
     * Gets the total count of places in the database.
     *
     * @return Place count
     */
    public long getPlaceCount() {
        long count = placeRepository.count();
        log.info("Current place count in database: {}", count);
        return count;
    }
}
