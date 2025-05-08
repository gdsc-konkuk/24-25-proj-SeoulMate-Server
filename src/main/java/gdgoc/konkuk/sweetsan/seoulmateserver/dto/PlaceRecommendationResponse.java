package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing recommended places")
public class PlaceRecommendationResponse {
    @Schema(description = "List of recommended places")
    private List<PlaceRecommendation> places;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Information about a recommended place")
    public static class PlaceRecommendation {
        @Schema(description = "Google Place ID of the recommended place")
        private String placeId;

        @Schema(description = "Description of the place")
        private String description;

        @Schema(description = "Reason for recommending this place")
        private String reason;
    }
}
