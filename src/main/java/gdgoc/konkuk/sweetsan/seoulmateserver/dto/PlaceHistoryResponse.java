package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for user's place history or liked places.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Place history response DTO")
public class PlaceHistoryResponse {

    @Schema(description = "List of places representing user's history or liked places")
    private List<PlaceDto> places;
}
