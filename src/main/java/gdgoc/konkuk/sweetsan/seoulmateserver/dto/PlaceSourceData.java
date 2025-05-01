package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

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
public class PlaceSourceData {

    /**
     * Name of the place as provided by the source
     */
    private String name;

    /**
     * Description of the place as provided by the source
     */
    private String description;

    /**
     * Optional source-specific identifier that might help with data enrichment
     */
    private String sourceId;

    /**
     * Source from which this data was collected (e.g., "visitseoul", "touristapi")
     */
    private String sourceName;
}
