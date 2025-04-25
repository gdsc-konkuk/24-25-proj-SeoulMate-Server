package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for place count operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Place count response")
public class ScraperCountResponse {

    @Schema(description = "Operation success status", example = "true")
    private Boolean success;

    @Schema(description = "Response message", example = "Count retrieved successfully")
    private String message;

    @Schema(description = "Total number of places in the database", example = "247")
    private Long count;
}
