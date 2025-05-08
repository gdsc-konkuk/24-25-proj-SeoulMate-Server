package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing basic source data collected from external sources. This serves as an
 * intermediate data structure between raw source data and the final Place model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Basic source data for a place from external sources")
public class PlaceSourceData {

    /**
     * Name of the place as provided by the source
     */
    @Schema(description = "Name of the place as provided by the source", example = "N Seoul Tower")
    private String name;

    /**
     * Description of the place as provided by the source
     */
    @Schema(description = "Description of the place as provided by the source", example = "An iconic landmark of Seoul featuring an observatory with beautiful city views.")
    private String description;

    /**
     * Optional source-specific identifier that might help with data enrichment
     */
    @Schema(description = "Optional source-specific identifier", example = "seoul_tower_001")
    private String sourceId;

    /**
     * Source from which this data was collected
     */
    @Schema(description = "Source from which this data was collected", example = "visitseoul")
    private String sourceName;
}
