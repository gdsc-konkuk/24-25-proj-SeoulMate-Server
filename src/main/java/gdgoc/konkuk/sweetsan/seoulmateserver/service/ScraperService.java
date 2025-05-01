package gdgoc.konkuk.sweetsan.seoulmateserver.service;

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
import java.util.concurrent.atomic.AtomicInteger;

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
                    log.info("Primary data retrieval completed asynchronously. Found {} source records",
                            sourceDataList.size());

                    // Continue the asynchronous chain with enrichment data
                    return placesEnrichmentRepository.findEnrichmentDataAsync(sourceDataList)
                            .thenApply(enrichmentDataList -> {
                                log.info(
                                        "Enrichment data retrieval completed asynchronously. Found {} enrichment records",
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
                                log.info("Asynchronous saving process completed. Total places saved: {}", placesCount);
                                return placesCount;
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error during asynchronous data collection", ex);
                    throw new RuntimeException("Error during asynchronous data collection", ex);
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

        // Calculate statistics
        int totalPlaces = places.size();
        int[] stats = calculatePlaceStats(places);
        int withName = stats[0];
        int withGooglePlaceId = stats[1];
        int withCoordinates = stats[2];
        int withDescription = stats[3];
        int withAllData = stats[4];

        // Log statistics
        logDataCompletenessStats(totalPlaces, withName, withGooglePlaceId, withCoordinates,
                withDescription, withAllData);

        // Log examples of incomplete data for diagnosis
        logIncompleteData(places, withAllData, totalPlaces);
    }

    /**
     * Calculate statistics for place data
     */
    private int[] calculatePlaceStats(List<Place> places) {
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

        return new int[]{withName, withGooglePlaceId, withCoordinates, withDescription, withAllData};
    }

    /**
     * Log statistics about data completeness
     */
    private void logDataCompletenessStats(int totalPlaces, int withName, int withGooglePlaceId,
                                          int withCoordinates, int withDescription, int withAllData) {
        log.info("===== Data Completeness Statistics =====");
        log.info("Total Places: {}", totalPlaces);
        log.info("With Name: {} ({}%)", withName, percentage(withName, totalPlaces));
        log.info("With Google Place ID: {} ({}%)", withGooglePlaceId, percentage(withGooglePlaceId, totalPlaces));
        log.info("With Coordinates: {} ({}%)", withCoordinates, percentage(withCoordinates, totalPlaces));
        log.info("With Description: {} ({}%)", withDescription, percentage(withDescription, totalPlaces));
        log.info("With ALL Required Data: {} ({}%)", withAllData, percentage(withAllData, totalPlaces));
        log.info("========================================");
    }

    /**
     * Log incomplete data for diagnosis
     */
    private void logIncompleteData(List<Place> places, int withAllData, int totalPlaces) {
        if (withAllData < totalPlaces) {
            log.info("Incomplete records:");
            int count = 0;
            for (Place place : places) {
                boolean hasAllFields = place.isValid();

                if (!hasAllFields) {
                    count++;
                    log.info("Incomplete record #{}: Name='{}', GoogleID={}, Coordinates={}, DescLength={}",
                            count,
                            place.getName(),
                            place.getGooglePlaceId() != null ? "Present" : "Missing",
                            place.getCoordinate() != null ?
                                    String.format("(%.6f, %.6f)",
                                            place.getCoordinate().getLatitude(),
                                            place.getCoordinate().getLongitude()) : "Missing",
                            place.getDescription() != null ? place.getDescription().length() : 0
                    );
                }
            }
        }
    }

    private double percentage(int part, int total) {
        return total > 0 ? Math.round((double) part / total * 100.0) : 0.0;
    }

    /**
     * Saves places to the database, updating existing ones if needed. Only saves places that have all required fields.
     *
     * @param places List of places to save
     * @return Number of new places saved
     */
    private int saveScrapedPlaces(List<Place> places) {
        log.info("Saving places to database with complete validation");
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        // Counters for skipping reasons
        AtomicInteger missingNameCount = new AtomicInteger(0);
        AtomicInteger missingGoogleIdCount = new AtomicInteger(0);
        AtomicInteger missingCoordsCount = new AtomicInteger(0);
        AtomicInteger missingDescCount = new AtomicInteger(0);

        for (Place place : places) {
            try {
                // Log detailed validation for this place
                logPlaceValidation(place);

                // Check if the place is completely valid (has all required fields)
                if (!place.isValid()) {
                    // Update counters for why we're skipping
                    updateSkippingReasonCounters(place, missingNameCount, missingGoogleIdCount,
                            missingCoordsCount, missingDescCount);

                    skippedCount++;
                    log.debug("Skipping place '{}' due to missing required fields", place.getName());
                    continue;
                }

                // Try to find existing places
                List<Place> existingPlaces = findExistingPlaces(place);

                if (existingPlaces == null || existingPlaces.isEmpty()) {
                    // Save new place with essential data
                    placeRepository.save(place);
                    newCount++;
                    log.debug("Saved new place: {}", place.getName());
                } else {
                    // Update existing place if needed
                    Place existingPlace = existingPlaces.get(0);
                    boolean updated = updateExistingPlaceIfNeeded(existingPlace, place);

                    if (updated) {
                        placeRepository.save(existingPlace);
                        updatedCount++;
                        log.debug("Updated existing place: {}", existingPlace.getName());
                    } else {
                        skippedCount++;
                        log.debug("No updates needed for existing place: {}", existingPlace.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error saving place '{}': {}", place.getName(), e.getMessage());
                skippedCount++;
            }
        }

        log.info("Database operation completed. New places: {}, updated places: {}, skipped: {}",
                newCount, updatedCount, skippedCount);

        // Log why places were skipped
        log.info(
                "Skipping reasons: Missing name: {}, Missing Google ID: {}, Missing coordinates: {}, Missing description: {}",
                missingNameCount.get(), missingGoogleIdCount.get(), missingCoordsCount.get(), missingDescCount.get());

        return newCount;
    }

    /**
     * Log detailed validation for a place
     */
    private void logPlaceValidation(Place place) {
        String validationLog = "Validating place '" + place.getName() + "': "
                // Validate individual fields and build log
                + "Name=" + (place.hasValidName() ? "Valid" : "Invalid") + ", "
                + "GoogleID=" + (place.hasValidGooglePlaceId() ? "Valid" : "Invalid") + ", "
                + "Coordinates=" + (place.hasValidCoordinates() ? "Valid" : "Invalid") + ", "
                + "Description=" + (place.hasValidDescription() ? "Valid" : "Invalid");

        log.debug(validationLog);
    }

    /**
     * Update counters for skipping reasons
     */
    private void updateSkippingReasonCounters(Place place, AtomicInteger missingNameCount,
                                              AtomicInteger missingGoogleIdCount,
                                              AtomicInteger missingCoordsCount,
                                              AtomicInteger missingDescCount) {
        if (!place.hasValidName()) {
            missingNameCount.incrementAndGet();
        }
        if (!place.hasValidGooglePlaceId()) {
            missingGoogleIdCount.incrementAndGet();
        }
        if (!place.hasValidCoordinates()) {
            missingCoordsCount.incrementAndGet();
        }
        if (!place.hasValidDescription()) {
            missingDescCount.incrementAndGet();
        }
    }

    /**
     * Find existing places by Google Place ID or name
     */
    private List<Place> findExistingPlaces(Place place) {
        List<Place> existingPlaces = null;

        if (place.hasValidGooglePlaceId()) {
            existingPlaces = placeRepository.findByGooglePlaceId(place.getGooglePlaceId());
        }

        if (existingPlaces == null || existingPlaces.isEmpty()) {
            existingPlaces = placeRepository.findByName(place.getName());
        }

        return existingPlaces;
    }

    /**
     * Updates an existing place with new data if needed. Updates are made to missing or incomplete fields from the new
     * place data.
     *
     * @param existing Existing place from database
     * @param newPlace New place with potential updates
     * @return true if any updates were made
     */
    private boolean updateExistingPlaceIfNeeded(Place existing, Place newPlace) {
        boolean updated = false;

        // Update description if missing in existing but present in new
        // or if new description is better (longer and meaningful)
        if (!existing.hasValidDescription() && newPlace.hasValidDescription()) {
            existing.setDescription(newPlace.getDescription());
            updated = true;
            log.debug("Updated description for: {}", existing.getName());
        } else if (existing.hasValidDescription() && newPlace.hasValidDescription() &&
                newPlace.getDescription().length() > existing.getDescription().length() &&
                newPlace.getDescription().length() > 50) { // Only consider meaningful descriptions
            existing.setDescription(newPlace.getDescription());
            updated = true;
            log.debug("Updated to better description for: {}", existing.getName());
        }

        // Log validation status after update
        if (updated) {
            log.debug("After update, place '{}' is {}", existing.getName(),
                    existing.isValid() ? "completely valid" : "still missing some fields");
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
