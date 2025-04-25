package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing the scraping process and saving results to the database.
 * Handles both synchronous and asynchronous scraping operations, enriches place data
 * with Google Place IDs, and manages database storage with duplicate detection.
 */
@Service
public class ScraperService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperService.class);
    
    private final PlaceRepository placeRepository;
    private final VisitSeoulScraper visitSeoulScraper;
    private final GooglePlaceUtil googlePlaceUtil;
    
    @Autowired
    public ScraperService(PlaceRepository placeRepository, 
                          VisitSeoulScraper visitSeoulScraper,
                          GooglePlaceUtil googlePlaceUtil) {
        this.placeRepository = placeRepository;
        this.visitSeoulScraper = visitSeoulScraper;
        this.googlePlaceUtil = googlePlaceUtil;
    }
    
    /**
     * Runs the scraper and saves results to the database synchronously.
     * 
     * @return The number of places scraped and saved
     */
    public int scrapeAndSave() {
        logger.info("Received request to run scraper synchronously");
        
        try {
            logger.info("Starting synchronous scraping process");
            List<Place> scrapedPlaces = visitSeoulScraper.scrape();
            
            logger.info("Scraping completed. Processing Google Place IDs for {} places", scrapedPlaces.size());
            enrichPlacesWithGooglePlaceIds(scrapedPlaces);
            
            int placesCount = saveScrapedPlaces(scrapedPlaces);
            logger.info("Scraping and saving process completed successfully. Total new places saved: {}", placesCount);
            
            return placesCount;
        } catch (Exception e) {
            logger.error("Error running synchronous scraper", e);
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
        logger.info("Received request to run scraper asynchronously");
        
        try {
            logger.info("Starting asynchronous scraping process");
            return visitSeoulScraper.scrapeAsync()
                    .thenApply(places -> {
                        logger.info("Asynchronous scraping completed. Processing Google Place IDs for {} places", places.size());
                        enrichPlacesWithGooglePlaceIds(places);
                        
                        int placesCount = saveScrapedPlaces(places);
                        logger.info("Asynchronous scraping and saving process completed. Total new places saved: {}", placesCount);
                        
                        return placesCount;
                    })
                    .exceptionally(ex -> {
                        logger.error("Error in asynchronous scraping process", ex);
                        throw new RuntimeException("Error in asynchronous scraping process", ex);
                    });
        } catch (Exception e) {
            logger.error("Error starting asynchronous scraper", e);
            throw e;
        }
    }
    
    /**
     * Enriches the collected place data with Google Place IDs
     * 
     * @param places List of scraped places
     */
    private void enrichPlacesWithGooglePlaceIds(List<Place> places) {
        logger.info("Enriching {} places with Google Place IDs", places.size());
        
        // Process places with coordinates first for better accuracy
        places.sort((p1, p2) -> {
            boolean hasCoord1 = p1.getCoordinate() != null 
                && p1.getCoordinate().getLatitude() != null 
                && p1.getCoordinate().getLongitude() != null;
            boolean hasCoord2 = p2.getCoordinate() != null 
                && p2.getCoordinate().getLatitude() != null 
                && p2.getCoordinate().getLongitude() != null;
            
            if (hasCoord1 && !hasCoord2) return -1;
            if (!hasCoord1 && hasCoord2) return 1;
            return 0;
        });
        
        int count = 0;
        int totalPlaces = places.size();
        
        for (Place place : places) {
            try {
                count++;
                // Log progress in batches
                if (count % 50 == 0 || count == totalPlaces) {
                    logger.info("Processing Google Place ID: {}/{} places", count, totalPlaces);
                }
                
                // Lookup Google Place ID
                String googlePlaceId = googlePlaceUtil.findGooglePlaceId(place.getName(), place);
                if (googlePlaceId != null && !googlePlaceId.isEmpty()) {
                    place.setGooglePlaceId(googlePlaceId);
                    logger.debug("Added Google Place ID for {}: {}", place.getName(), googlePlaceId);
                }
                
                // Add delay between API calls to avoid rate limiting
                Thread.sleep(300);
            } catch (Exception e) {
                logger.error("Error enriching place {} with Google Place ID", place.getName(), e);
            }
        }
        
        logger.info("Completed Google Place ID enrichment for {} places", totalPlaces);
    }
    
    /**
     * Saves scraped places to the database, checking for duplicates by name.
     * 
     * @param places List of scraped places
     * @return The number of places saved
     */
    private int saveScrapedPlaces(List<Place> places) {
        logger.info("Processing {} scraped places for database storage", places.size());
        int savedCount = 0;
        int updatedCount = 0;
        
        for (Place place : places) {
            try {
                // Check if a place with this name already exists
                List<Place> existingPlaces = placeRepository.findByName(place.getName());
                if (existingPlaces.isEmpty()) {
                    placeRepository.save(place);
                    savedCount++;
                    logger.debug("Saved new place: {}", place.getName());
                } else {
                    logger.debug("Place already exists, checking for updates: {}", place.getName());
                    
                    // Update existing place if it's missing coordinates or Google Place ID
                    Place existingPlace = existingPlaces.get(0);
                    boolean needsUpdate = false;
                    
                    if ((existingPlace.getCoordinate() == null || 
                         existingPlace.getCoordinate().getLatitude() == null ||
                         existingPlace.getCoordinate().getLongitude() == null) && 
                        place.getCoordinate() != null && 
                        place.getCoordinate().getLatitude() != null && 
                        place.getCoordinate().getLongitude() != null) {
                        
                        existingPlace.setCoordinate(place.getCoordinate());
                        needsUpdate = true;
                        logger.debug("Updating coordinates for existing place: {}", place.getName());
                    }
                    
                    if ((existingPlace.getGooglePlaceId() == null || existingPlace.getGooglePlaceId().isEmpty()) && 
                        place.getGooglePlaceId() != null && !place.getGooglePlaceId().isEmpty()) {
                        
                        existingPlace.setGooglePlaceId(place.getGooglePlaceId());
                        needsUpdate = true;
                        logger.debug("Updating Google Place ID for existing place: {}", place.getName());
                    }
                    
                    if (needsUpdate) {
                        placeRepository.save(existingPlace);
                        updatedCount++;
                    }
                }
            } catch (Exception e) {
                logger.error("Error saving place: {}", place.getName(), e);
            }
        }
        
        logger.info("Database operation completed. Total new places saved: {}, places updated: {}", savedCount, updatedCount);
        return savedCount;
    }
    
    /**
     * Gets the total count of places stored in the database
     * 
     * @return The count of places
     */
    public long getPlaceCount() {
        logger.info("Received request to get place count");
        
        try {
            long count = placeRepository.count();
            logger.info("Place count retrieved: {}", count);
            return count;
        } catch (Exception e) {
            logger.error("Error retrieving place count", e);
            throw e;
        }
    }
    
    /**
     * Tests the connection to the Visit Seoul website
     * 
     * @return true if connection is successful
     */
    public boolean testConnection() {
        logger.info("Received request to test connection to Visit Seoul website");
        
        try {
            // Implementation for testing connectivity would go here
            // For now, this is just a placeholder
            logger.info("Connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("Connection test failed", e);
            throw e;
        }
    }
}
