package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceEnrichmentData;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceSourceData;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Component responsible for aggregating place data from different sources and converting them into the final Place
 * model.
 * <p>
 * This aggregator combines basic source data (name, description) with enrichment data (coordinates, standardized names,
 * external IDs) to create complete Place entities.
 *
 * @see Place
 * @see PlaceSourceData
 * @see PlaceEnrichmentData
 */
@Slf4j
@Component
public class PlaceAggregator {

    /**
     * Aggregates source data and enrichment data into complete Place objects.
     * <p>
     * This method assumes that the lists are in the same order, with corresponding elements at the same indices. If the
     * lists have different sizes, it will process only up to the size of the smaller list.
     *
     * @param sourceDataList     List of basic place information
     * @param enrichmentDataList List of complementary place details
     * @return List of aggregated Place objects
     */
    public List<Place> aggregatePlaceData(List<PlaceSourceData> sourceDataList,
                                          List<PlaceEnrichmentData> enrichmentDataList) {
        List<Place> places = new ArrayList<>();

        if (sourceDataList == null || sourceDataList.isEmpty()) {
            log.warn("Source data list is empty or null, cannot aggregate place data");
            return places;
        }

        if (enrichmentDataList == null || enrichmentDataList.isEmpty()) {
            log.warn("Enrichment data list is empty or null, proceeding with source data only");
            // Create places with source data only
            for (PlaceSourceData sourceData : sourceDataList) {
                Place place = createPlaceFromSourceData(sourceData);
                places.add(place);
            }
            return places;
        }

        // Process the smaller of the two lists to avoid IndexOutOfBoundsException
        int size = Math.min(sourceDataList.size(), enrichmentDataList.size());
        log.info("Aggregating {} place records from source and enrichment data", size);

        for (int i = 0; i < size; i++) {
            PlaceSourceData sourceData = sourceDataList.get(i);
            PlaceEnrichmentData enrichmentData = enrichmentDataList.get(i);

            try {
                Place place = createAggregatedPlace(sourceData, enrichmentData);
                places.add(place);
            } catch (Exception e) {
                log.warn("Error aggregating place '{}': {}", sourceData.getName(), e.getMessage());
                // Fallback to create place with just source data
                Place place = createPlaceFromSourceData(sourceData);
                places.add(place);
            }
        }

        return places;
    }
    
    /**
     * Creates a Place object using only source data (without enrichment)
     * 
     * @param sourceData The source data containing name and description
     * @return A new Place object with data from source
     */
    private Place createPlaceFromSourceData(PlaceSourceData sourceData) {
        return Place.builder()
                .name(sourceData.getName())
                .description(sourceData.getDescription())
                .build();
    }
    
    /**
     * Creates a fully aggregated Place object combining source and enrichment data
     * 
     * @param sourceData The source data containing name and description
     * @param enrichmentData The enrichment data containing coordinates and identifiers
     * @return A new complete Place object
     */
    private Place createAggregatedPlace(PlaceSourceData sourceData, PlaceEnrichmentData enrichmentData) {
        // Create coordinate object if we have valid coordinates
        Place.Coordinate coordinate = null;
        if (enrichmentData.getLatitude() != null && enrichmentData.getLongitude() != null) {
            coordinate = Place.Coordinate.builder()
                    .latitude(enrichmentData.getLatitude())
                    .longitude(enrichmentData.getLongitude())
                    .build();
        }

        // Prioritize standardized name from enrichment data if available
        String name = enrichmentData.getStandardizedName() != null && !enrichmentData.getStandardizedName().isEmpty()
                ? enrichmentData.getStandardizedName()
                : sourceData.getName();

        // Build the aggregated place 
        return Place.builder()
                .name(name)
                .description(sourceData.getDescription())
                .googlePlaceId(enrichmentData.getExternalId())
                .coordinate(coordinate)
                .build();
    }
}
