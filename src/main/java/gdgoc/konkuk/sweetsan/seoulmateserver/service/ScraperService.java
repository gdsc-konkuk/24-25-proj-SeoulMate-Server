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

/**
 * Scraper service for collecting Seoul tourist places.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final PlaceRepository placeRepository;
    private final SimplePlaceScraper simplePlaceScraper;
    private final GooglePlacesService googlePlacesService;


    /**
     * Runs the scraper and saves results to the database synchronously.
     *
     * @return The number of places scraped and saved
     */
    public int scrapeAndSave() {
        log.info("Starting synchronous scraping process");

        try {
            // Perform scraping
            List<Place> scrapedPlaces = simplePlaceScraper.scrape();
            log.info("Scraping completed. Found {} places", scrapedPlaces.size());

            // Enrich with Google Place IDs
            googlePlacesService.enrichPlacesWithGoogleData(scrapedPlaces);

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

        return simplePlaceScraper.scrapeAsync()
                .thenApply(places -> {
                    log.info("Asynchronous basic scraping completed. Found {} places", places.size());
                    return googlePlacesService.enrichPlacesWithGoogleData(places);
                })
                .thenApply(enrichedPlaces -> {
                    log.info("Asynchronous Google Places enrichment completed. Have {} enriched places",
                            enrichedPlaces.size());
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
     * Saves scraped places to the database, updating existing ones if needed.
     *
     * @param places List of places to save
     * @return Number of new places saved
     */
    private int saveScrapedPlaces(List<Place> places) {
        log.info("Saving {} places to database", places.size());
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (Place place : places) {
            try {
                // Skip places with insufficient data
                if (place.getName() == null || place.getName().isEmpty()) {
                    skippedCount++;
                    log.warn("Place name is null or empty for place: {}", place.getDescription());
                    continue;
                }

                // Check for existing place by name or Google Place ID
                List<Place> existingPlaces = null;

                if (place.getGooglePlaceId() != null && !place.getGooglePlaceId().isEmpty()) {
                    existingPlaces = placeRepository.findByGooglePlaceId(place.getGooglePlaceId());
                }

                if (existingPlaces == null || existingPlaces.isEmpty()) {
                    existingPlaces = placeRepository.findByName(place.getName());
                }

                if (existingPlaces == null || existingPlaces.isEmpty()) {
                    // Only save if we have meaningful data
                    if (hasValidData(place)) {
                        // Save new place
                        placeRepository.save(place);
                        newCount++;
                        log.debug("Saved new place: {}", place.getName());
                    } else {
                        skippedCount++;
                        log.warn("Place has insufficient data for saving: {}", place.getName());
                    }
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
                log.error("Error saving place: {}", place.getName(), e);
                skippedCount++;
            }
        }

        log.info("Database operation completed. New places: {}, updated places: {}, skipped: {}",
                newCount, updatedCount, skippedCount);
        return newCount;
    }

    /**
     * Checks if a place has enough valid data to be worth saving.
     *
     * @param place Place to check
     * @return true if the place has valid data
     */
    private boolean hasValidData(Place place) {
        // At minimum, must have a name and either:
        // 1. A valid coordinate, or
        // 2. A Google Place ID, or
        // 3. A meaningful description

        if (place.getName() == null || place.getName().isEmpty()) {
            return false;
        }

        boolean hasCoordinates = hasValidCoordinates(place);

        boolean hasGooglePlaceId = place.getGooglePlaceId() != null &&
                !place.getGooglePlaceId().isEmpty();

        boolean hasDescription = place.getDescription() != null &&
                !place.getDescription().isEmpty() &&
                place.getDescription().length() > 20; // Arbitrary minimum length

        return hasCoordinates || hasGooglePlaceId || hasDescription;
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
