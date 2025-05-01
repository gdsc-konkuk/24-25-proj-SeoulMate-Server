package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing enrichment data collected from geographic/mapping services. This contains
 * geographic and standardized place details that are not available from basic source data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEnrichmentData {

    /**
     * Standardized name of the place
     */
    private String standardizedName;

    /**
     * Unique identifier from the enrichment service (e.g., Google Place ID)
     */
    private String externalId;

    /**
     * Latitude coordinate
     */
    private Double latitude;

    /**
     * Longitude coordinate
     */
    private Double longitude;
}
