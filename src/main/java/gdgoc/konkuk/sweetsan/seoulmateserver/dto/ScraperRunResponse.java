package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for scraper run operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Scraper run operation response")
public class ScraperRunResponse {

    @Schema(description = "Operation success status", example = "true")
    private Boolean success;

    @Schema(description = "Response message", example = "Scraping completed successfully")
    private String message;

    @Schema(description = "Number of places scraped and saved", example = "42")
    private Integer placesCount;
}
