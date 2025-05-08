package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Enrichment data for a place from geographic/mapping services")
public class PlaceEnrichmentData {

    /**
     * Standardized name of the place
     */
    @Schema(description = "Standardized name of the place", example = "Starbucks Gangnam Branch")
    private String standardizedName;

    /**
     * Unique identifier from the enrichment service (e.g., Google Place ID)
     */
    @Schema(description = "Unique identifier from the enrichment service", example = "ChIJN1t_tDeuEmsRUsoyG83frY4")
    private String externalId;

    /**
     * Latitude coordinate
     */
    @Schema(description = "Latitude coordinate", example = "37.5083")
    private Double latitude;

    /**
     * Longitude coordinate
     */
    @Schema(description = "Longitude coordinate", example = "127.0628")
    private Double longitude;
}
