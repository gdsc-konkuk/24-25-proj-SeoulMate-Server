package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.SimplePlaceScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scraper service for collecting Seoul tourist places.
 * <p>
 * This service orchestrates the two-step process of collecting place information:
 *
 * <ol>
 *    <li>Web scraping from Visit Seoul website to collect basic information (name, description)</li>
 *    <li>Enriching with Google Places API to get geographic coordinates and standardized place details</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final PlaceRepository placeRepository;
    private final SimplePlaceScraper simplePlaceScraper;
    private final GooglePlacesService googlePlacesService;


    /**
     * Runs the scraper and saves results to the database synchronously. This process consists of two main steps: 1.
     * Scraping place information from Visit Seoul website 2. Enriching the data with Google Places API
     *
     * @return The number of places scraped and saved
     */
    public int scrapeAndSave() {
        log.info("Starting synchronous scraping process");

        try {
            // Step 1: Perform web scraping to get basic place information
            // (name, description)
            List<Place> scrapedPlaces = simplePlaceScraper.scrape();
            log.info("Web scraping completed. Found {} places", scrapedPlaces.size());

            // Step 2: Enrich with Google Places API to get coordinates and Google Place IDs
            List<Place> enrichedPlaces = googlePlacesService.enrichPlacesWithGoogleData(scrapedPlaces);
            log.info("Google Places enrichment completed. Processed {} places", enrichedPlaces.size());

            // Log statistics on the enriched data
            logDataStatistics(enrichedPlaces);

            // Save to database with relaxed validation to debug issues
            int placesCount = saveScrapedPlaces(enrichedPlaces);
            log.info("Scraping and saving process completed. Total new places saved: {}", placesCount);

            return placesCount;
        } catch (Exception e) {
            log.error("Error during synchronous scraping", e);
            throw e;
        }
    }

    /**
     * Log statistics about the enriched data to help diagnose issues
     */
    private void logDataStatistics(List<Place> places) {
        if (places == null || places.isEmpty()) {
            log.warn("No places to analyze for statistics");
            return;
        }

        int totalPlaces = places.size();
        int withName = 0;
        int withGooglePlaceId = 0;
        int withCoordinates = 0;
        int withDescription = 0;
        int withAllData = 0;

        for (Place place : places) {
            // Check each field
            boolean hasName = place.getName() != null && !place.getName().isEmpty();
            boolean hasGoogleId = place.getGooglePlaceId() != null && !place.getGooglePlaceId().isEmpty();
            boolean hasCoords = hasValidCoordinates(place);
            boolean hasDesc = place.getDescription() != null && !place.getDescription().isEmpty() &&
                    place.getDescription().length() >= 20;

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
        log.info("With Name: {} ({}%)", withName, percentage(withName, totalPlaces));
        log.info("With Google Place ID: {} ({}%)", withGooglePlaceId, percentage(withGooglePlaceId, totalPlaces));
        log.info("With Coordinates: {} ({}%)", withCoordinates, percentage(withCoordinates, totalPlaces));
        log.info("With Description: {} ({}%)", withDescription, percentage(withDescription, totalPlaces));
        log.info("With ALL Required Data: {} ({}%)", withAllData, percentage(withAllData, totalPlaces));
        log.info("========================================");

        // Log some examples of incomplete data for diagnosis
        if (withAllData < totalPlaces) {
            log.info("Examples of incomplete records:");
            int examples = 0;
            for (Place place : places) {
                if (examples >= 5) {
                    break; // Limit to 5 examples
                }

                boolean hasAllFields = hasValidName(place) &&
                        hasValidGooglePlaceId(place) &&
                        hasValidCoordinates(place) &&
                        hasValidDescription(place);

                if (!hasAllFields) {
                    examples++;
                    log.info("Incomplete record #{}: Name='{}', GoogleID={}, Coordinates={}, DescLength={}",
                            examples,
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
     * Runs the scraper and saves results to the database asynchronously. This process consists of two main steps: 1.
     * Scraping place information from Visit Seoul website 2. Enriching the data with Google Places API
     *
     * @return A CompletableFuture that resolves to the number of places scraped and saved
     */
    @Async
    public CompletableFuture<Integer> scrapeAndSaveAsync() {
        log.info("Starting asynchronous scraping process");

        return simplePlaceScraper.scrapeAsync()
                .thenApply(places -> {
                    log.info("Web scraping completed asynchronously. Found {} places", places.size());
                    return googlePlacesService.enrichPlacesWithGoogleData(places);
                })
                .thenApply(enrichedPlaces -> {
                    log.info("Google Places enrichment completed asynchronously. Have {} enriched places",
                            enrichedPlaces.size());
                    // Log statistics on the enriched data
                    logDataStatistics(enrichedPlaces);
                    int placesCount = saveScrapedPlaces(enrichedPlaces);
                    log.info("Asynchronous saving process completed. Total places saved: {}", placesCount);
                    return placesCount;
                })
                .exceptionally(ex -> {
                    log.error("Error during asynchronous scraping", ex);
                    throw new RuntimeException("Error during asynchronous scraping", ex);
                });
    }

    /**
     * Saves scraped places to the database, updating existing ones if needed. Only saves places that have essential
     * fields (name, and either Google Place ID or coordinates). For debugging purposes, we're temporarily relaxing the
     * validation to understand what data is available.
     *
     * @param places List of places to save
     * @return Number of new places saved
     */
    private int saveScrapedPlaces(List<Place> places) {
        log.info("Saving places to database with revised validation criteria");
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
                StringBuilder validationLog = new StringBuilder();
                validationLog.append("Validating place '").append(place.getName()).append("': ");

                // Validate individual fields and build log
                boolean nameValid = hasValidName(place);
                validationLog.append("Name=").append(nameValid ? "Valid" : "Invalid").append(", ");

                boolean googleIdValid = hasValidGooglePlaceId(place);
                validationLog.append("GoogleID=").append(googleIdValid ? "Valid" : "Invalid").append(", ");

                boolean coordsValid = hasValidCoordinates(place);
                validationLog.append("Coordinates=").append(coordsValid ? "Valid" : "Invalid").append(", ");

                boolean descValid = hasValidDescription(place);
                validationLog.append("Description=").append(descValid ? "Valid" : "Invalid");

                log.debug(validationLog.toString());

                // For debugging, only require name and either Google Place ID or coordinates
                // This helps us understand what data is actually available
                boolean hasEssentialData = nameValid && (googleIdValid || coordsValid);

                if (!hasEssentialData) {
                    // Update counters for why we're skipping
                    if (!nameValid) {
                        missingNameCount.incrementAndGet();
                    }
                    if (!googleIdValid) {
                        missingGoogleIdCount.incrementAndGet();
                    }
                    if (!coordsValid) {
                        missingCoordsCount.incrementAndGet();
                    }
                    if (!descValid) {
                        missingDescCount.incrementAndGet();
                    }

                    skippedCount++;
                    log.debug("Skipping place '{}' due to incomplete essential data", place.getName());
                    continue;
                }

                // Check for existing place by Google Place ID or by name
                List<Place> existingPlaces = null;

                if (place.getGooglePlaceId() != null && !place.getGooglePlaceId().isEmpty()) {
                    existingPlaces = placeRepository.findByGooglePlaceId(place.getGooglePlaceId());
                }

                if (existingPlaces == null || existingPlaces.isEmpty()) {
                    existingPlaces = placeRepository.findByName(place.getName());
                }

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
     * Check if name is valid
     */
    private boolean hasValidName(Place place) {
        return place.getName() != null && !place.getName().isEmpty();
    }

    /**
     * Check if Google Place ID is valid
     */
    private boolean hasValidGooglePlaceId(Place place) {
        return place.getGooglePlaceId() != null && !place.getGooglePlaceId().isEmpty();
    }

    /**
     * Check if description is valid (non-empty and meaningful length)
     */
    private boolean hasValidDescription(Place place) {
        return place.getDescription() != null && !place.getDescription().isEmpty() &&
                place.getDescription().length() >= 20;
    }

    /**
     * Updates an existing place with new data if needed.
     *
     * @param existing Existing place from database
     * @param newPlace New place with potential updates
     * @return true if any updates were made
     */
    private boolean updateExistingPlaceIfNeeded(Place existing, Place newPlace) {
        boolean updated = false;

        // Update Google Place ID if missing in existing but present in new
        if ((existing.getGooglePlaceId() == null || existing.getGooglePlaceId().isEmpty()) &&
                newPlace.getGooglePlaceId() != null && !newPlace.getGooglePlaceId().isEmpty()) {
            existing.setGooglePlaceId(newPlace.getGooglePlaceId());
            updated = true;
            log.debug("Updated Google Place ID for: {}", existing.getName());
        }

        // Update coordinates if missing in existing but present in new
        if (!hasValidCoordinates(existing) && hasValidCoordinates(newPlace)) {
            existing.setCoordinate(newPlace.getCoordinate());
            updated = true;
            log.debug("Updated coordinates for: {}", existing.getName());
        }

        // Update description if missing in existing but present in new
        // or if new description is better (longer and meaningful)
        if ((existing.getDescription() == null || existing.getDescription().isEmpty()) &&
                newPlace.getDescription() != null && !newPlace.getDescription().isEmpty()) {
            existing.setDescription(newPlace.getDescription());
            updated = true;
            log.debug("Updated description for: {}", existing.getName());
        } else if (existing.getDescription() != null && !existing.getDescription().isEmpty() &&
                newPlace.getDescription() != null && !newPlace.getDescription().isEmpty() &&
                newPlace.getDescription().length() > existing.getDescription().length() &&
                newPlace.getDescription().length() > 50) { // Only consider meaningful descriptions
            existing.setDescription(newPlace.getDescription());
            updated = true;
            log.debug("Updated to better description for: {}", existing.getName());
        }

        return updated;
    }

    /**
     * Checks if a place has valid coordinates.
     *
     * @param place Place to check
     * @return true if the place has valid coordinates
     */
    private boolean hasValidCoordinates(Place place) {
        return place.getCoordinate() != null &&
                place.getCoordinate().getLatitude() != null &&
                place.getCoordinate().getLongitude() != null;
    }

    /**
     * Gets the total count of places in the database.
     *
     * @return Place count
     */
    public long getPlaceCount() {
        try {
            long count = placeRepository.count();
            log.info("Current place count in database: {}", count);
            return count;
        } catch (Exception e) {
            log.error("Error retrieving place count", e);
            throw e;
        }
    }
}
