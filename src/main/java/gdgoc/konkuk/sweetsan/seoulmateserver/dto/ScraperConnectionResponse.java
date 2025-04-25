package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for scraper connection test.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Scraper connection test response")
public class ScraperConnectionResponse {

    @Schema(description = "Operation success status", example = "true")
    private Boolean success;

    @Schema(description = "Response message", example = "Successfully connected to Visit Seoul website")
    private String message;

    @Schema(description = "Timestamp of the connection test", example = "1682512345678")
    private Long timestamp;
}
