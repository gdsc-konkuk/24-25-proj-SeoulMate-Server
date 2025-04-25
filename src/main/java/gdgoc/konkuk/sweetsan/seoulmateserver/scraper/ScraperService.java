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
        logger.info("Starting synchronous scraping process");
        List<Place> scrapedPlaces = visitSeoulScraper.scrape();
        // Google Place ID 조회 추가
        enrichPlacesWithGooglePlaceIds(scrapedPlaces);
        return saveScrapedPlaces(scrapedPlaces);
    }
    
    /**
     * Runs the scraper and saves results to the database asynchronously.
     * 
     * @return A CompletableFuture that resolves to the number of places scraped and saved
     */
    @Async
    public CompletableFuture<Integer> scrapeAndSaveAsync() {
        logger.info("Starting asynchronous scraping process");
        return visitSeoulScraper.scrapeAsync()
                .thenApply(places -> {
                    enrichPlacesWithGooglePlaceIds(places);
                    return saveScrapedPlaces(places);
                });
    }
    
    /**
     * 수집된 장소 데이터에 Google Place ID 정보를 추가
     * 
     * @param places 스크래핑된 장소 목록
     */
    private void enrichPlacesWithGooglePlaceIds(List<Place> places) {
        logger.info("Enriching {} places with Google Place IDs", places.size());
        
        // 좌표가 있는 장소 먼저 처리 (정확도 향상)
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
                // 50개 단위로 로그 출력
                if (count % 50 == 0 || count == totalPlaces) {
                    logger.info("Processing Google Place ID: {}/{} places", count, totalPlaces);
                }
                
                // Google Place ID 조회
                String googlePlaceId = googlePlaceUtil.findGooglePlaceId(place.getName(), place);
                if (googlePlaceId != null && !googlePlaceId.isEmpty()) {
                    place.setGooglePlaceId(googlePlaceId);
                    logger.info("Added Google Place ID for {}: {}", place.getName(), googlePlaceId);
                }
                
                // API 호출 간 간격을 두어 속도 제한 방지 (짧게 조정)
                Thread.sleep(300);
            } catch (Exception e) {
                logger.error("Error enriching place {} with Google Place ID", place.getName(), e);
            }
        }
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
        
        for (Place place : places) {
            try {
                // Check if a place with this name already exists
                List<Place> existingPlaces = placeRepository.findByName(place.getName());
                if (existingPlaces.isEmpty()) {
                    placeRepository.save(place);
                    savedCount++;
                    logger.info("Saved new place: {}", place.getName());
                } else {
                    logger.info("Place already exists, skipping: {}", place.getName());
                    
                    // 기존 장소에 좌표나 Google Place ID가 없는 경우 업데이트
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
                        logger.info("Updating coordinates for existing place: {}", place.getName());
                    }
                    
                    if ((existingPlace.getGooglePlaceId() == null || existingPlace.getGooglePlaceId().isEmpty()) && 
                        place.getGooglePlaceId() != null && !place.getGooglePlaceId().isEmpty()) {
                        
                        existingPlace.setGooglePlaceId(place.getGooglePlaceId());
                        needsUpdate = true;
                        logger.info("Updating Google Place ID for existing place: {}", place.getName());
                    }
                    
                    if (needsUpdate) {
                        placeRepository.save(existingPlace);
                    }
                }
            } catch (Exception e) {
                logger.error("Error saving place: {}", place.getName(), e);
            }
        }
        
        logger.info("Completed saving places. Total new places saved: {}", savedCount);
        return savedCount;
    }
    
    /**
     * 데이터베이스에 저장된 장소 개수를 반환
     * 
     * @return 장소 개수
     */
    public long getPlaceCount() {
        return placeRepository.count();
    }
}
