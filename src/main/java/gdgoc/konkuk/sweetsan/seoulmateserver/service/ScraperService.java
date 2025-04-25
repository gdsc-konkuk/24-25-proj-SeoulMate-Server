package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.GooglePlaceUtil;
import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.VisitSeoulScraper;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing the scraping process and saving results to the database. Handles both synchronous and
 * asynchronous scraping operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final PlaceRepository placeRepository;
    private final VisitSeoulScraper visitSeoulScraper;
    private final GooglePlaceUtil googlePlaceUtil;

    /**
     * Runs the scraper and saves results to the database synchronously.
     *
     * @return The number of places scraped and saved
     */
    public int scrapeAndSave() {
        log.info("Starting synchronous scraping process");

        try {
            // Perform scraping
            List<Place> scrapedPlaces = visitSeoulScraper.scrape();
            log.info("Scraping completed. Found {} places", scrapedPlaces.size());

            // Enrich with Google Place IDs
            enrichPlacesWithGooglePlaceIds(scrapedPlaces);

            // Save to database
            int placesCount = saveScrapedPlaces(scrapedPlaces);
            log.info("Scraping and saving process completed. Total new places saved: {}", placesCount);

            return placesCount;
        } catch (Exception e) {
            log.error("Error during synchronous scraping", e);
            throw e;
        }
    }

    /**
     * Runs the scraper and saves results to the database asynchronously.
     *
     * @return A CompletableFuture that resolves to the number of places scraped and saved
     */
    @Async
    public CompletableFuture<Integer> scrapeAndSaveAsync() {
        log.info("Starting asynchronous scraping process");

        try {
            return visitSeoulScraper.scrapeAsync()
                    .thenApply(places -> {
                        log.info("Asynchronous scraping completed. Found {} places", places.size());
                        enrichPlacesWithGooglePlaceIds(places);
                        int placesCount = saveScrapedPlaces(places);
                        log.info("Asynchronous saving process completed. Total new places saved: {}", placesCount);
                        return placesCount;
                    })
                    .exceptionally(ex -> {
                        log.error("Error during asynchronous scraping", ex);
                        throw new RuntimeException("Error during asynchronous scraping", ex);
                    });
        } catch (Exception e) {
            log.error("Error starting asynchronous scraping", e);
            throw e;
        }
    }

    /**
     * Enriches places with Google Place IDs.
     *
     * @param places List of places to enrich
     */
    private void enrichPlacesWithGooglePlaceIds(List<Place> places) {
        log.info("Enriching {} places with Google Place IDs", places.size());

        // Check if API key is properly configured
        if (googlePlaceUtil.isApiKeyMissing()) {
            log.warn("Google Maps API key is not properly configured. Google Place IDs will not be enriched.");
            log.warn("Consider setting the GOOGLE_MAPS_API_KEY environment variable for full functionality.");
            return;
        }

        // Sort places to prioritize those with coordinates for better accuracy
        sortPlacesByCoordinateAvailability(places);

        // Process in smaller batches to mitigate API rate limit issues and show better progress
        final int BATCH_SIZE = 20;
        final int DELAY_BETWEEN_BATCH_MS = 2000; // Delay between batches

        // Calculate how many places already have Google Place IDs (to skip them)
        int preExistingIds = (int) places.stream()
                .filter(p -> p.getGooglePlaceId() != null && !p.getGooglePlaceId().isEmpty())
                .count();

        log.info("{} places already have Google Place IDs and will be skipped", preExistingIds);

        // Create a copy of the list to avoid concurrent modification issues
        List<Place> placesToProcess = new ArrayList<>(places);

        // Remove places that already have Google Place IDs
        placesToProcess.removeIf(p -> p.getGooglePlaceId() != null && !p.getGooglePlaceId().isEmpty());

        int totalToProcess = placesToProcess.size();
        int processedCount = 0;
        int successCount = 0;
        int failCount = 0;
        int batchCount = 0;

        // Process in batches
        for (int i = 0; i < totalToProcess; i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, totalToProcess);
            List<Place> batch = placesToProcess.subList(i, endIndex);
            batchCount++;

            log.info("Processing batch {}, places {}-{} of {}",
                    batchCount, i + 1, endIndex, totalToProcess);

            // Process each place in the batch
            for (Place place : batch) {
                try {
                    processedCount++;

                    // Find Google Place ID
                    String googlePlaceId = googlePlaceUtil.findGooglePlaceId(place.getName(), place);
                    if (googlePlaceId != null && !googlePlaceId.isEmpty()) {
                        place.setGooglePlaceId(googlePlaceId);
                        log.debug("Added Google Place ID for {}: {}", place.getName(), googlePlaceId);
                        successCount++;
                    } else {
                        failCount++;
                        log.debug("Failed to find Google Place ID for: {}", place.getName());
                    }

                    // Dynamic delay to avoid API rate limiting
                    int delay = calculateDynamicDelay(successCount, failCount);
                    Thread.sleep(delay);

                } catch (InterruptedException e) {
                    log.error("Thread interrupted while enriching Google Place IDs", e);
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.error("Error enriching place {} with Google Place ID: {}",
                            place.getName(), e.getMessage());
                    failCount++;
                }

                // Log progress more frequently
                if (processedCount % 10 == 0 || processedCount == totalToProcess) {
                    log.info("Google Place ID progress: {}/{} places, Success: {}, Failed: {}",
                            processedCount, totalToProcess, successCount, failCount);
                }
            }

            // Add delay between batches to avoid API rate limiting
            if (i + BATCH_SIZE < totalToProcess) {
                try {
                    log.info("Waiting between batches to avoid API rate limiting...");
                    Thread.sleep(DELAY_BETWEEN_BATCH_MS);
                } catch (InterruptedException e) {
                    log.error("Thread interrupted while waiting between batches", e);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.info("Completed Google Place ID enrichment. Total: {}, Success: {}, Failed: {}",
                processedCount, successCount, failCount);
    }

    /**
     * Calculates a dynamic delay between API requests based on success/fail ratio to adaptively handle rate limiting.
     *
     * @param successCount Number of successful requests
     * @param failCount    Number of failed requests
     * @return Delay in milliseconds
     */
    private int calculateDynamicDelay(int successCount, int failCount) {
        final int BASE_DELAY_MS = 300;
        final int MAX_DELAY_MS = 2000;

        // If no failures, use base delay
        if (failCount == 0) {
            return BASE_DELAY_MS;
        }

        // Calculate failure ratio
        double totalRequests = successCount + failCount;
        double failRatio = failCount / totalRequests;

        // Increase delay based on failure ratio
        int dynamicDelay = (int) (BASE_DELAY_MS + (failRatio * 5 * BASE_DELAY_MS));

        // Cap at maximum delay
        return Math.min(dynamicDelay, MAX_DELAY_MS);
    }

    /**
     * Sorts places by coordinate availability. Places with coordinates come first for better Google Place ID lookup
     * accuracy.
     *
     * @param places List of places to sort
     */
    private void sortPlacesByCoordinateAvailability(List<Place> places) {
        places.sort((p1, p2) -> {
            boolean hasCoord1 = hasValidCoordinates(p1);
            boolean hasCoord2 = hasValidCoordinates(p2);

            if (hasCoord1 && !hasCoord2) {
                return -1;
            }
            if (!hasCoord1 && hasCoord2) {
                return 1;
            }
            return 0;
        });
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
     * Saves scraped places to the database, updating existing ones if needed.
     *
     * @param places List of places to save
     * @return Number of new places saved
     */
    private int saveScrapedPlaces(List<Place> places) {
        log.info("Saving {} places to database", places.size());
        int newCount = 0;
        int updatedCount = 0;

        for (Place place : places) {
            try {
                // Check for existing place by name
                List<Place> existingPlaces = placeRepository.findByName(place.getName());

                if (existingPlaces.isEmpty()) {
                    // Save new place
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
                    }
                }
            } catch (Exception e) {
                log.error("Error saving place: {}", place.getName(), e);
            }
        }

        log.info("Database operation completed. New places: {}, updated places: {}", newCount, updatedCount);
        return newCount;
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

        // Update coordinates if missing
        if (!hasValidCoordinates(existing) && hasValidCoordinates(newPlace)) {
            existing.setCoordinate(newPlace.getCoordinate());
            updated = true;
            log.debug("Updated coordinates for: {}", existing.getName());
        }

        // Update Google Place ID if missing
        if ((existing.getGooglePlaceId() == null || existing.getGooglePlaceId().isEmpty()) &&
                newPlace.getGooglePlaceId() != null && !newPlace.getGooglePlaceId().isEmpty()) {
            existing.setGooglePlaceId(newPlace.getGooglePlaceId());
            updated = true;
            log.debug("Updated Google Place ID for: {}", existing.getName());
        }

        // Update description if the new one is better (longer)
        if ((existing.getDescription() == null || existing.getDescription().isEmpty() ||
                (newPlace.getDescription() != null && newPlace.getDescription().length() > existing.getDescription()
                        .length()))) {
            existing.setDescription(newPlace.getDescription());
            updated = true;
            log.debug("Updated description for: {}", existing.getName());
        }

        return updated;
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
